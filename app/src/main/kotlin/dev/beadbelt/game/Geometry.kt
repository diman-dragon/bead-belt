package dev.beadbelt.game

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Логические координаты поля 360x640 (потом масштабируются под экран).
const val W = 360f
const val H = 640f
const val TRAY_Y = 186f
const val BOX_Y = 528f
const val BOX_W = 78f
const val BOX_H = 92f
const val BODY_R = 8.5f      // радиус шарика в воронке
/**
 * На сколько воронка и лотки подняты над лентой при отрисовке. Физика считается в прежних координатах
 * (поэтому поведение шариков и уровни не меняются), а рисуется всё это со сдвигом вверх.
 */
const val LIFT = 40f

// кнопки бустеров (нужны и игре — для следа ракеты, и интерфейсу)
const val ROCKET_X = 44f
const val RETURN_X = 316f
const val BOOST_Y = 614f

const val EV_TAP = 0
const val EV_PICK = 1
const val EV_POP = 2
const val EV_WIN = 3
const val EV_LOSE = 4
const val EV_ROCKET = 5
const val EV_UNLOCK = 6
const val EV_CLINK = 7      // удар шариков друг о друга / о стекло; arg — сила 0..7

/** Овальный конвейер: сверху вправо, снизу влево (по часовой). */
object Geo {
    const val CX = 180f
    const val CY = 392f
    const val A = 100f      // полудлина прямой
    const val R = 50f       // радиус дуги
    val ARC = (PI * R).toFloat()
    val L = 4 * A + 2 * ARC

    /** Точка на ленте: out[0], out[1]. */
    fun point(p0: Float, out: FloatArray) {
        var p = p0 % L
        if (p < 0) p += L
        when {
            p < A -> { out[0] = CX + p; out[1] = CY - R }
            p < A + ARC -> {
                val a = -PI / 2 + (p - A) / R
                out[0] = (CX + A + R * cos(a)).toFloat(); out[1] = (CY + R * sin(a)).toFloat()
            }
            p < 3 * A + ARC -> { out[0] = CX + A - (p - A - ARC); out[1] = CY + R }
            p < 3 * A + 2 * ARC -> {
                val a = PI / 2 + (p - 3 * A - ARC) / R
                out[0] = (CX - A + R * cos(a)).toFloat(); out[1] = (CY + R * sin(a)).toFloat()
            }
            else -> { out[0] = CX - A + (p - 3 * A - 2 * ARC); out[1] = CY - R }
        }
    }

    /** Точка и направление движения (градусы, 0 = вправо) в out[0..2]. */
    fun pose(p0: Float, out: FloatArray) {
        point(p0, out)
        var p = p0 % L
        if (p < 0) p += L
        out[2] = when {
            p < A -> 0f
            p < A + ARC -> Math.toDegrees((p - A) / R.toDouble()).toFloat()
            p < 3 * A + ARC -> 180f
            p < 3 * A + 2 * ARC -> 180f + Math.toDegrees((p - 3 * A - ARC) / R.toDouble()).toFloat()
            else -> 0f
        }
    }
}

/** Стенки воронки (x1, y1, x2, y2): шарики из лотков падают сюда и скатываются к воротам над конвейером. */
val WALLS = arrayOf(
    floatArrayOf(12f, 120f, 12f, 250f), floatArrayOf(12f, 250f, 171f, 328f),
    floatArrayOf(171f, 328f, 189f, 328f),                       // отверстие: чуть шире шарика, шарики проходят по одному
    floatArrayOf(189f, 328f, 348f, 250f), floatArrayOf(348f, 250f, 348f, 120f),
)
