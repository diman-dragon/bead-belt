package dev.beadbelt.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/** Короткие синтезированные звуки (без файлов). Каждый — статический AudioTrack. */
class Sound {
    var enabled = true
    private val rate = 22050
    private val tracks = HashMap<Int, AudioTrack?>()

    private fun tone(freqs: List<Float>, durMs: Int): ShortArray {
        val per = rate * durMs / 1000 / freqs.size
        val out = ShortArray(per * freqs.size)
        for ((k, f) in freqs.withIndex()) for (i in 0 until per) {
            val env = (1f - i.toFloat() / per).let { it * it }
            out[k * per + i] = (sin(2.0 * PI * f * i / rate) * env * 9000).toInt().toShort()
        }
        return out
    }

    private fun build(id: Int): AudioTrack? = try {
        val data = when (id) {
            in 0..7 -> tone(listOf(520f * Math.pow(1.0595, id * 2.0).toFloat()), 110)   // подбор шарика: тон растёт
            10 -> tone(listOf(330f), 60)                                                  // тап
            11 -> tone(listOf(660f, 880f, 1100f), 260)                                    // коробка заполнена
            12 -> tone(listOf(523f, 659f, 784f, 1046f), 520)                              // победа
            14 -> tone(listOf(200f, 350f, 600f, 900f, 1300f), 380)                        // ракета
            15 -> tone(listOf(440f, 880f), 200)                                           // лифт открылся
            else -> tone(listOf(300f, 230f, 160f), 520)                                   // поражение
        }
        AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(data.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build().also { it.write(data, 0, data.size) }
    } catch (e: Exception) { null }

    private fun play(id: Int) {
        if (!enabled) return
        try {
            val t = tracks.getOrPut(id) { build(id) } ?: return
            t.stop(); t.reloadStaticData(); t.play()
        } catch (e: Exception) { /* звук не критичен */ }
    }

    fun event(ev: Int, arg: Int) = play(when (ev) {
        EV_PICK -> arg
        EV_TAP -> 10
        EV_POP -> 11
        EV_WIN -> 12
        EV_ROCKET -> 14
        EV_UNLOCK -> 15
        else -> 13
    })

    fun release() { tracks.values.forEach { it?.release() }; tracks.clear() }
}
