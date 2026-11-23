package dev.beadbelt.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.os.Process
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Звуки без ресурсов в APK: при первом запуске синтезируются в короткие WAV и грузятся в SoundPool.
 *
 * Почему раньше с включённым звуком игра подтормаживала: `SoundPool.play()` — синхронный вызов в аудиосервер,
 * а его делали прямо из потока отрисовки, и на каждый «цок» шариков, подбор и хлопок коробки кадр ждал ответа.
 * Теперь игра лишь кладёт в очередь короткое сообщение (без выделения памяти), а SoundPool создаётся и играет
 * в отдельном потоке. Частые «цоки» не копятся: пока предыдущий не сыгран, новый отбрасывается.
 */
class Sound(context: Context) {
    @Volatile var enabled = true

    private companion object {
        const val RATE = 22050
        const val VERSION = 4
        const val PICK = "pick"; const val TAP = "tap"; const val POP = "pop"; const val WIN = "win"
        const val LOSE = "lose"; const val ROCKET = "rocket"; const val UNLOCK = "unlock"; const val CLINK = "clink"
        val NAMES = arrayOf(PICK, TAP, POP, WIN, LOSE, ROCKET, UNLOCK, CLINK)
        // номера звуков в массивах
        const val I_PICK = 0; const val I_TAP = 1; const val I_POP = 2; const val I_WIN = 3
        const val I_LOSE = 4; const val I_ROCKET = 5; const val I_UNLOCK = 6; const val I_CLINK = 7
    }

    private val thread = HandlerThread("bead-sound", Process.THREAD_PRIORITY_AUDIO).also { it.start() }
    private val handler = Handler(thread.looper) { msg -> playEvent(msg.what, msg.arg1); true }

    // Всё ниже трогается только из потока звука, поэтому без блокировок.
    private var pool: SoundPool? = null
    private val ids = IntArray(NAMES.size)
    private val ready = BooleanArray(NAMES.size)

    init {
        handler.post {
            try {
                val sp = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                ).build()
                sp.setOnLoadCompleteListener { _, id, status ->
                    if (status == 0) for (i in ids.indices) if (ids[i] == id) ready[i] = true
                }
                pool = sp
                val dir = File(context.cacheDir, "snd$VERSION").also { it.mkdirs() }
                for (i in NAMES.indices) {
                    val f = File(dir, NAMES[i] + ".wav")
                    if (!f.exists() || f.length() == 0L) writeWav(f, synth(NAMES[i]))
                    ids[i] = sp.load(f.absolutePath, 1)
                }
            } catch (e: Exception) { /* звук не критичен */ }
        }
    }

    private fun play(i: Int, rate: Float = 1f, vol: Float = 1f, priority: Int = 1) {
        val sp = pool ?: return
        if (!ready[i]) return
        sp.play(ids[i], vol, vol, priority, 0, rate)
    }

    /** Вызывается в потоке звука. */
    private fun playEvent(ev: Int, arg: Int) {
        when (ev) {
            EV_PICK -> play(I_PICK, 2.0.pow(minOf(arg, 6) * 2 / 12.0).toFloat(), 0.8f)   // комбо: тон растёт по целым тонам
            EV_TAP -> play(I_TAP, 1f, 0.7f)
            EV_POP -> play(I_POP)
            EV_WIN -> play(I_WIN)
            EV_ROCKET -> play(I_ROCKET, 1f, 0.8f)
            EV_UNLOCK -> play(I_UNLOCK)
            EV_CLINK -> play(I_CLINK, 0.85f + arg * 0.07f, 0.18f + arg * 0.05f, 0)    // сильнее удар — выше и громче
            else -> play(I_LOSE)
        }
    }

    /** Вызывается из игры (поток отрисовки): только ставит событие в очередь. */
    fun event(ev: Int, arg: Int) {
        if (!enabled) return
        if (ev == EV_CLINK && handler.hasMessages(EV_CLINK)) return      // частые «цоки» не копим
        handler.sendMessage(Message.obtain(handler, ev, arg, 0))
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        handler.post { pool?.release(); pool = null; thread.quitSafely() }
    }

    // ---------- синтез ----------

    private fun bell(t: Float, f: Float, decay: Float): Float {
        val a = (t / 0.004f).coerceAtMost(1f)
        val e = a * exp(-t * decay)
        val w = 2f * PI.toFloat() * f * t
        return (sin(w) + 0.32f * sin(2f * w) + 0.12f * sin(3.01f * w)) * e
    }

    private fun synth(name: String): ShortArray {
        val out: (Float) -> Float
        val ms: Int
        when (name) {
            CLINK -> { ms = 80; out = { t -> bell(t, 2300f, 55f) * 0.8f + bell(t, 3450f, 70f) * 0.3f } }
            PICK -> { ms = 190; out = { t -> bell(t, 784f, 17f) * 0.8f } }
            TAP -> { ms = 90; out = { t -> sin(2f * PI.toFloat() * (240f + 200f * exp(-t * 40f)) * t) * exp(-t * 38f) } }
            POP -> { ms = 420; out = { t -> bell(t, 1047f, 11f) * 0.6f + bell((t - 0.05f).coerceAtLeast(0f), 1319f, 11f) * (if (t > 0.05f) 0.6f else 0f) + bell((t - 0.1f).coerceAtLeast(0f), 1568f, 9f) * (if (t > 0.1f) 0.6f else 0f) } }
            WIN -> {
                ms = 1300
                val notes = floatArrayOf(523f, 659f, 784f, 1047f, 1319f, 1568f, 2093f)
                out = { t ->
                    var s = 0f
                    for (i in notes.indices) { val st = i * 0.095f; if (t > st) s += bell(t - st, notes[i], if (i == notes.size - 1) 4f else 8f) * 0.42f }
                    s
                }
            }
            LOSE -> { ms = 650; out = { t -> val f = 330f - 190f * (t / 0.65f); val w = 2f * PI.toFloat() * f * t; (sin(w) + 0.4f * sin(2f * w)) * (1f - t / 0.65f) * 0.7f * (1f - exp(-t * 60f)) } }
            ROCKET -> {
                ms = 460
                var seed = 12345
                out = { t ->
                    seed = seed * 1103515245 + 12345
                    val noise = ((seed ushr 16) and 0x7FFF) / 16384f - 1f
                    val f = 180f + 1500f * (t / 0.46f) * (t / 0.46f)
                    (sin(2f * PI.toFloat() * f * t) * 0.6f + noise * 0.25f) * (1f - exp(-t * 40f)) * (1f - t / 0.46f)
                }
            }
            else -> { ms = 420; out = { t -> bell(t, 659f, 9f) * 0.6f + (if (t > 0.1f) bell(t - 0.1f, 988f, 8f) * 0.6f else 0f) } }
        }
        val n = RATE * ms / 1000
        val data = ShortArray(n)
        var peak = 0.0001f
        val tmp = FloatArray(n) { i -> out(i.toFloat() / RATE).also { v -> if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v) } }
        val g = 0.7f * 32767f / peak
        for (i in 0 until n) {
            val fade = ((n - i) / (RATE * 0.01f)).coerceAtMost(1f)       // 10 мс затухания в конце, без щелчка
            data[i] = (tmp[i] * g * fade).toInt().coerceIn(-32768, 32767).toShort()
        }
        return data
    }

    private fun writeWav(f: File, pcm: ShortArray) {
        val dataLen = pcm.size * 2
        val b = java.nio.ByteBuffer.allocate(44 + dataLen).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + dataLen).put("WAVE".toByteArray()).put("fmt ".toByteArray())
        b.putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(dataLen)
        for (s in pcm) b.putShort(s)
        FileOutputStream(f).use { it.write(b.array()) }
    }
}
