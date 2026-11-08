package com.broilerchart.app

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One day of the standard chart (values are PER BIRD, grams). */
data class DayRow(
    val day: Int,
    val weight: Int,      // body weight (g)
    val gain: Int,        // daily weight gain (g)
    val dailyFeed: Int,   // daily feed per bird (g)
    val cumFeed: Int      // cumulative feed per bird (g)
)

object BroilerData {
    const val START_WEIGHT = 42
    /** The reference chart corresponds to this FCR. Changing FCR scales feed by (fcr / BASE_FCR). */
    const val BASE_FCR = 1.48

    // Phases: Starter 1-14, Grower 15-25, Finisher 26-32+
    const val STARTER_END = 14
    const val GROWER_END = 25
    const val LAST_DAY = 32

    fun phaseOf(day: Int): Int = when {
        day <= STARTER_END -> 0
        day <= GROWER_END -> 1
        else -> 2
    }

    val rows: List<DayRow> = listOf(
        DayRow(1, 56, 14, 13, 13),
        DayRow(2, 72, 16, 17, 30),
        DayRow(3, 89, 17, 21, 51),
        DayRow(4, 109, 20, 23, 74),
        DayRow(5, 131, 22, 29, 103),
        DayRow(6, 157, 26, 31, 132),
        DayRow(7, 185, 28, 35, 167),
        DayRow(8, 215, 30, 39, 206),
        DayRow(9, 247, 32, 44, 250),
        DayRow(10, 283, 36, 48, 298),
        DayRow(11, 321, 38, 54, 352),
        DayRow(12, 364, 43, 58, 410),
        DayRow(13, 412, 48, 64, 474),
        DayRow(14, 465, 53, 68, 542),
        DayRow(15, 524, 59, 75, 617),
        DayRow(16, 586, 62, 81, 698),
        DayRow(17, 651, 65, 87, 785),
        DayRow(18, 719, 68, 93, 878),
        DayRow(19, 790, 71, 98, 976),
        DayRow(20, 865, 75, 105, 1081),
        DayRow(21, 943, 78, 111, 1192),
        DayRow(22, 1023, 80, 117, 1309),
        DayRow(23, 1104, 81, 124, 1433),
        DayRow(24, 1186, 82, 130, 1562),
        DayRow(25, 1269, 83, 134, 1696),
        DayRow(26, 1353, 84, 141, 1837),
        DayRow(27, 1438, 85, 148, 1985),
        DayRow(28, 1524, 86, 152, 2137),
        DayRow(29, 1613, 89, 158, 2295),
        DayRow(30, 1705, 92, 163, 2458),
        DayRow(31, 1799, 94, 169, 2627),
        DayRow(32, 1895, 96, 174, 2801)
    )
}

data class Settings(
    val count: Int,
    val fcr: Double,
    val scaleDaily: Boolean,
    val bagKg: Int
)

/** All numbers derived from the settings. */
class Computed(val s: Settings) {
    val k: Double = s.fcr / BroilerData.BASE_FCR
    val rows get() = BroilerData.rows

    /** Per-bird daily feed (g). Scaled only when "scaleDaily" is ON. */
    fun daily(r: DayRow): Double = if (s.scaleDaily) r.dailyFeed * k else r.dailyFeed.toDouble()

    /** Per-bird cumulative feed (g). Scaled only when "scaleDaily" is ON. */
    fun cum(r: DayRow): Double = if (s.scaleDaily) r.cumFeed * k else r.cumFeed.toDouble()

    /** Whole-flock cumulative feed (kg). ALWAYS follows FCR. */
    fun flockKg(r: DayRow): Double = r.cumFeed * k * s.count / 1000.0

    fun cumKgAtDay(day: Int): Double = flockKg(rows[day - 1])

    val totalKg: Double get() = cumKgAtDay(BroilerData.LAST_DAY)
    val starterKg: Double get() = cumKgAtDay(BroilerData.STARTER_END)
    val growerKg: Double get() = cumKgAtDay(BroilerData.GROWER_END) - starterKg
    val finisherKg: Double get() = totalKg - cumKgAtDay(BroilerData.GROWER_END)

    /** Final live weight of the whole flock in kg (0% mortality). */
    val flockWeightKg: Double get() = rows.last().weight * s.count / 1000.0

    fun bagsOf(kg: Double): Double = kg / s.bagKg

    /** (full bags, remaining kg) */
    fun bagParts(kg: Double): Pair<Int, Int> {
        val total = kg.roundToInt()
        return Pair(total / s.bagKg, total % s.bagKg)
    }
}

object Fmt {
    private val digits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

    fun bn(s: String): String {
        val sb = StringBuilder()
        for (ch in s) sb.append(if (ch in '0'..'9') digits[ch - '0'] else ch)
        return sb.toString()
    }

    /** Converts Bengali digits typed by the user into ASCII digits. */
    fun ascii(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            val i = digits.indexOf(ch)
            sb.append(if (i >= 0) ('0' + i) else if (ch == '\u0964' || ch == ',') '.' else ch)
        }
        return sb.toString()
    }

    fun int(n: Int): String = bn(String.format(Locale.US, "%,d", n))

    fun dec(v: Double, d: Int = 1): String {
        var s = String.format(Locale.US, "%,.${d}f", v)
        if (s.contains('.')) s = s.trimEnd('0').trimEnd('.')
        return bn(s)
    }

    /** Whole number if close, otherwise one decimal. */
    fun g(v: Double): String =
        if (abs(v - v.roundToInt()) < 0.05) int(v.roundToInt()) else dec(v, 1)
}
