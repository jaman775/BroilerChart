package com.broilerchart.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws the whole 4-section infographic (cover, charts, 32-day table) into ONE tall black PNG.
 * Everything is drawn with android.graphics.Canvas, so there is no screenshot / layout dependency.
 */
class InfographicRenderer(private val st: Settings) {

    companion object {
        const val W = 1080
        private const val COVER_H = 1650
        private const val CHART_H = 2040
        private const val TABLE_H = 3290
        private const val M = 54f
        private const val CW = 972f

        val BG: Int = Color.parseColor("#070707")
        val SC: Int = Color.parseColor("#FFD23F")   // starter
        val GC: Int = Color.parseColor("#FF9A1F")   // grower
        val FC: Int = Color.parseColor("#FF5A36")   // finisher
        val AMBER: Int = Color.parseColor("#FFB627")
        val CARD: Int = Color.parseColor("#151515")
        val TXT: Int = Color.parseColor("#F2F2F2")
        val MUTED: Int = Color.parseColor("#9A9A9A")
        val SOFT: Int = Color.parseColor("#B8B8B8")
        val RED: Int = Color.parseColor("#FF2E3B")
        val BEAK: Int = Color.parseColor("#FFC21A")

        private val NICE_UNITS = intArrayOf(1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000, 5000, 10000)
    }

    private val calc = Computed(st)
    private val rows = BroilerData.rows

    // ------------------------------------------------------------------ public API
    fun render(): Bitmap {
        val h = COVER_H + CHART_H + TABLE_H
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(BG)
        var top = 0f
        section(c, top, COVER_H.toFloat()) { drawCover(it) }
        top += COVER_H
        section(c, top, CHART_H.toFloat()) { drawCharts(it) }
        top += CHART_H
        section(c, top, TABLE_H.toFloat()) { drawTable(it) }
        return bmp
    }

    private fun section(c: Canvas, top: Float, h: Float, body: (Canvas) -> Unit) {
        c.save()
        c.translate(0f, top)
        c.clipRect(0f, 0f, W.toFloat(), h)
        glow(c, W * 0.9f, 80f, 720f, AMBER, 46)
        glow(c, W * 0.05f, h - 60f, 700f, FC, 38)
        body(c)
        c.restore()
    }

    // ------------------------------------------------------------------ paint helpers
    private fun alpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a shl 24)

    private fun fillP(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun strokeP(color: Int, w: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = w
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun tp(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign = align
        }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
                     bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
        c.drawText(s, x, y, tp(size, color, bold, align))
    }

    private fun fitText(c: Canvas, s: String, x: Float, y: Float, maxW: Float, size: Float, color: Int,
                        bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
        val p = tp(size, color, bold, align)
        val w = p.measureText(s)
        if (w > maxW && w > 0f) p.textSize = size * maxW / w
        c.drawText(s, x, y, p)
    }

    private fun measure(s: String, size: Float, bold: Boolean = false): Float = tp(size, TXT, bold).measureText(s)

    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float,
                   fillColor: Int?, strokeColor: Int? = null, sw: Float = 2f) {
        val rect = RectF(l, t, r, b)
        if (fillColor != null) c.drawRoundRect(rect, rad, rad, fillP(fillColor))
        if (strokeColor != null) c.drawRoundRect(rect, rad, rad, strokeP(strokeColor, sw))
    }

    private fun card(c: Canvas, l: Float, t: Float, r: Float, b: Float, border: Int = alpha(Color.WHITE, 40)) {
        rr(c, l, t, r, b, 30f, CARD, border, 2f)
    }

    private fun glow(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, a: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(cx, cy, r, alpha(color, a), alpha(color, 0), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
    }

    private fun phaseColor(day: Int): Int = when (BroilerData.phaseOf(day)) {
        0 -> SC
        1 -> GC
        else -> FC
    }

    private fun pill(c: Canvas, s: String, y: Float, withHen: Boolean = false) {
        val tw = measure(s, 28f)
        val w = tw + (if (withHen) 104f else 56f)
        rr(c, M, y, M + w, y + 62f, 31f, alpha(AMBER, 30), alpha(AMBER, 150), 2f)
        var tx = M + 28f
        if (withHen) {
            hen(c, M + 18f, y + 11f, 40f, SC)
            tx = M + 70f
        }
        text(c, s, tx, y + 41f, 28f, Color.parseColor("#FFC94D"))
    }

    private fun valueUnit(c: Canvas, v: String, unit: String, x: Float, y: Float, maxW: Float,
                          vSize: Float, color: Int) {
        val pv = tp(vSize, color, true)
        val pu = tp(vSize * 0.62f, MUTED)
        val total = pv.measureText(v) + 6f + pu.measureText(unit)
        if (total > maxW && total > 0f) {
            val f = maxW / total
            pv.textSize = vSize * f
            pu.textSize = vSize * 0.62f * f
        }
        val w = pv.measureText(v)
        c.drawText(v, x, y, pv)
        c.drawText(unit, x + w + 6f, y, pu)
    }

    private fun bar(c: Canvas, x: Float, y: Float, w: Float, frac: Float, color: Int) {
        rr(c, x, y, x + w, y + 9f, 4.5f, alpha(Color.WHITE, 30))
        rr(c, x, y, x + max(8f, w * frac.coerceIn(0f, 1f)), y + 9f, 4.5f, color)
    }

    // ------------------------------------------------------------------ icons
    private fun hen(c: Canvas, x: Float, y: Float, size: Float, color: Int) {
        c.save()
        c.translate(x, y)
        val s = size / 100f
        c.scale(s, s)
        val f = fillP(color)
        val tail = Path().apply {
            moveTo(20f, 52f); quadTo(2f, 34f, 8f, 20f); quadTo(22f, 30f, 32f, 44f); close()
        }
        c.drawPath(tail, f)
        c.drawOval(RectF(16f, 35f, 76f, 81f), f)
        val wing = Path().apply {
            moveTo(32f, 58f); quadTo(46f, 46f, 62f, 58f); quadTo(48f, 72f, 32f, 58f); close()
        }
        c.drawPath(wing, fillP(alpha(Color.BLACK, 56)))
        c.drawCircle(72f, 36f, 13f, f)
        val comb = Path().apply {
            moveTo(64f, 27f); quadTo(64f, 14f, 70f, 22f); quadTo(74f, 11f, 78f, 22f)
            quadTo(85f, 16f, 81f, 28f); close()
        }
        c.drawPath(comb, fillP(RED))
        val beak = Path().apply { moveTo(84f, 33f); lineTo(98f, 39f); lineTo(84f, 43f); close() }
        c.drawPath(beak, fillP(BEAK))
        c.drawOval(RectF(76.5f, 41.5f, 83.5f, 52.5f), fillP(RED))
        c.drawCircle(76f, 33f, 2.4f, fillP(Color.BLACK))
        val lg = strokeP(BEAK, 3f)
        c.drawLine(38f, 80f, 36f, 95f, lg)
        c.drawLine(34f, 95f, 42f, 95f, lg)
        c.drawLine(52f, 80f, 52f, 95f, lg)
        c.drawLine(49f, 95f, 57f, 95f, lg)
        c.restore()
    }

    private fun sackShape(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        c.save()
        c.translate(x, y)
        c.scale(w / 60f, h / 80f)
        val body = Path().apply {
            moveTo(10f, 24f); quadTo(5f, 44f, 8f, 70f); quadTo(9f, 77f, 17f, 77f)
            lineTo(43f, 77f); quadTo(51f, 77f, 52f, 70f); quadTo(55f, 44f, 50f, 24f); close()
        }
        c.drawPath(body, fillP(color))
        val top = Path().apply {
            moveTo(13f, 24f); lineTo(19f, 9f); quadTo(30f, 1f, 41f, 9f); lineTo(47f, 24f); close()
        }
        c.drawPath(top, fillP(alpha(color, 205)))
        rr(c, 12f, 20f, 48f, 25f, 2f, alpha(Color.BLACK, 90))
        rr(c, 15f, 37f, 45f, 59f, 4f, alpha(Color.BLACK, 77))
        val g = strokeP(alpha(Color.WHITE, 200), 1.8f)
        val grain = Path().apply {
            moveTo(30f, 56f); lineTo(30f, 41f)
            moveTo(30f, 46f); quadTo(24f, 44f, 23f, 40f)
            moveTo(30f, 46f); quadTo(36f, 44f, 37f, 40f)
            moveTo(30f, 52f); quadTo(24f, 50f, 23f, 46f)
            moveTo(30f, 52f); quadTo(36f, 50f, 37f, 46f)
        }
        c.drawPath(grain, g)
        c.restore()
    }

    /** A sack that can be only partly filled (frac 0..1) – used for the "bag" pictograph. */
    private fun sack(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, frac: Float = 1f) {
        if (frac >= 0.999f) {
            sackShape(c, x, y, w, h, color)
        } else {
            sackShape(c, x, y, w, h, alpha(Color.WHITE, 30))
            c.save()
            c.clipRect(x, y, x + w * frac, y + h)
            sackShape(c, x, y, w, h, color)
            c.restore()
        }
    }

    // kinds: 0 calendar, 1 scale, 2 ratio, 3 truck, 4 hen, 5 sack
    private fun icon(c: Canvas, kind: Int, x: Float, y: Float, size: Float, color: Int) {
        if (kind == 4) { hen(c, x, y, size, color); return }
        if (kind == 5) { sack(c, x + size * 0.12f, y, size * 0.75f, size, color); return }
        c.save()
        c.translate(x, y)
        val s = size / 60f
        c.scale(s, s)
        val p = strokeP(color, 4f)
        when (kind) {
            0 -> {
                c.drawRoundRect(RectF(6f, 12f, 54f, 54f), 8f, 8f, p)
                c.drawLine(6f, 25f, 54f, 25f, p)
                c.drawLine(18f, 6f, 18f, 17f, p)
                c.drawLine(42f, 6f, 42f, 17f, p)
                val f = fillP(color)
                c.drawCircle(20f, 38f, 3.5f, f); c.drawCircle(32f, 38f, 3.5f, f); c.drawCircle(44f, 38f, 3.5f, f)
            }
            1 -> {
                c.drawRoundRect(RectF(6f, 10f, 54f, 54f), 10f, 10f, p)
                c.drawArc(RectF(16f, 14f, 44f, 42f), 180f, 180f, false, p)
                c.drawLine(30f, 28f, 38f, 18f, p)
                c.drawCircle(30f, 28f, 3f, fillP(color))
            }
            2 -> {
                val a = Path().apply { moveTo(10f, 22f); lineTo(44f, 22f); moveTo(36f, 12f); lineTo(46f, 22f); lineTo(36f, 32f) }
                val b = Path().apply { moveTo(50f, 40f); lineTo(16f, 40f); moveTo(24f, 30f); lineTo(14f, 40f); lineTo(24f, 50f) }
                c.drawPath(a, p); c.drawPath(b, p)
            }
            else -> {
                c.drawRoundRect(RectF(4f, 16f, 38f, 42f), 4f, 4f, p)
                val cab = Path().apply { moveTo(38f, 24f); lineTo(48f, 24f); lineTo(56f, 33f); lineTo(56f, 42f); lineTo(38f, 42f); close() }
                c.drawPath(cab, p)
                c.drawCircle(16f, 46f, 6f, fillP(BG)); c.drawCircle(16f, 46f, 6f, p)
                c.drawCircle(46f, 46f, 6f, fillP(BG)); c.drawCircle(46f, 46f, 6f, p)
            }
        }
        c.restore()
    }

    // ------------------------------------------------------------------ shared chart pieces
    private fun areaAndLine(c: Canvas, pts: List<Pair<Float, Float>>, xl: Float, xr: Float,
                            yTop: Float, yBase: Float, lineW: Float) {
        val path = Path()
        path.moveTo(pts[0].first, pts[0].second)
        for (i in 1 until pts.size) path.lineTo(pts[i].first, pts[i].second)
        val area = Path(path)
        area.lineTo(pts.last().first, yBase)
        area.lineTo(pts[0].first, yBase)
        area.close()
        val ap = Paint(Paint.ANTI_ALIAS_FLAG)
        ap.shader = LinearGradient(0f, yTop, 0f, yBase, alpha(AMBER, 150), alpha(FC, 0), Shader.TileMode.CLAMP)
        c.drawPath(area, ap)
        val colors = intArrayOf(SC, GC, FC)
        val pos = floatArrayOf(0f, 0.45f, 1f)
        val glowP = strokeP(Color.WHITE, lineW * 3f)
        glowP.shader = LinearGradient(xl, 0f, xr, 0f, colors, pos, Shader.TileMode.CLAMP)
        glowP.alpha = 38
        c.drawPath(path, glowP)
        val lp = strokeP(Color.WHITE, lineW)
        lp.shader = LinearGradient(xl, 0f, xr, 0f, colors, pos, Shader.TileMode.CLAMP)
        c.drawPath(path, lp)
    }

    // ================================================================== COVER
    private fun drawCover(c: Canvas) {
        pill(c, "${Fmt.int(st.count)} মুরগির জন্য বিশেষ চার্ট", 56f, withHen = true)
        fitText(c, "ব্রয়লার মুরগি", M, 240f, CW, 112f, Color.WHITE, true)
        fitText(c, "৩২ দিনের গ্রোথ ও ফিড গাইড", M, 328f, CW, 62f, AMBER, true)
        fitText(c, "ওজন বৃদ্ধি  •  খাদ্য গ্রহণ  •  বস্তার হিসাব — এক নজরে", M, 384f, CW, 29f, SOFT)

        // hero growth curve with three hens
        val axisY = 940f
        val x0 = M + 10f
        val x1 = W - M - 20f
        fun xOf(d: Int) = x0 + d / 32f * (x1 - x0)
        fun yOf(w: Int) = axisY - w / 1895f * 320f
        val pts = ArrayList<Pair<Float, Float>>()
        pts.add(Pair(xOf(0), yOf(BroilerData.START_WEIGHT)))
        for (r in rows) pts.add(Pair(xOf(r.day), yOf(r.weight)))
        areaAndLine(c, pts, x0, x1, yOf(1895), axisY, 9f)
        c.drawLine(x0 - 10f, axisY, x1 + 10f, axisY, strokeP(alpha(Color.WHITE, 50), 2f))

        data class Marker(val day: Int, val size: Float, val color: Int, val label: String, val wl: String)
        val markers = listOf(
            Marker(2, 90f, SC, "২ দিন", "${Fmt.int(rows[1].weight)} গ্রাম"),
            Marker(16, 160f, GC, "১৬ দিন", "${Fmt.int(rows[15].weight)} গ্রাম"),
            Marker(32, 230f, FC, "৩২ দিন", "${Fmt.int(rows[31].weight)} গ্রাম")
        )
        for (m in markers) {
            val px = xOf(m.day)
            val py = yOf(rows[m.day - 1].weight)
            val hx = if (m.day == 32) px - m.size * 0.85f else px - m.size * 0.5f
            hen(c, hx, py - m.size * 0.94f, m.size, m.color)
            val dash = strokeP(alpha(m.color, 170), 2f)
            dash.pathEffect = DashPathEffect(floatArrayOf(6f, 10f), 0f)
            c.drawLine(px, py + 12f, px, axisY, dash)
            c.drawCircle(px, py, 11f, fillP(BG))
            c.drawCircle(px, py, 11f, strokeP(m.color, 5f))
            val al = if (m.day == 32) Paint.Align.RIGHT else Paint.Align.CENTER
            text(c, m.label, px, axisY + 48f, 30f, m.color, true, al)
            text(c, m.wl, px, axisY + 86f, 27f, Color.parseColor("#D8D8D8"), false, al)
        }

        // stat cards
        class Stat(val icon: Int, val color: Int, val big: String, val label: String)
        val stats = listOf(
            Stat(4, SC, Fmt.int(st.count), "মুরগির ব্যাচ (হিসাবের ভিত্তি)"),
            Stat(0, GC, "৩২ দিন", "পালনের সময়কাল"),
            Stat(1, FC, "${Fmt.int(rows[31].weight)} গ্রাম", "৩২ দিনে ১টি মুরগির ওজন"),
            Stat(5, SC, "${Fmt.dec(calc.bagsOf(calc.totalKg), 1)} বস্তা",
                "মোট খাদ্য ${Fmt.dec(calc.totalKg, 1)} কেজি (১ বস্তা = ${Fmt.int(st.bagKg)} কেজি)"),
            Stat(2, GC, Fmt.dec(st.fcr, 2), "খাদ্য রূপান্তর হার (FCR)"),
            Stat(3, FC, "≈ ${Fmt.dec(calc.flockWeightKg, 0)} কেজি", "ব্যাচের মোট ওজন (মৃত্যুহার ০% ধরে)")
        )
        val cw = (CW - 36f) / 3f
        val ch = 180f
        for ((i, s) in stats.withIndex()) {
            val l = M + (i % 3) * (cw + 18f)
            val t = 1070f + (i / 3) * (ch + 18f)
            card(c, l, t, l + cw, t + ch, alpha(s.color, 95))
            icon(c, s.icon, l + 24f, t + 20f, 46f, s.color)
            fitText(c, s.big, l + 24f, t + 118f, cw - 44f, 50f, s.color, true)
            fitText(c, s.label, l + 24f, t + 156f, cw - 44f, 23f, SOFT)
        }

        // phase strip
        val total = CW - 28f
        val days = intArrayOf(14, 11, 7)
        val names = arrayOf("স্টার্টার", "গ্রোয়ার", "ফিনিশার")
        val ranges = arrayOf("১–১৪ দিন", "১৫–২৫ দিন", "২৬–৩২+ দিন")
        val colors = intArrayOf(SC, GC, FC)
        val kgs = doubleArrayOf(calc.starterKg, calc.growerKg, calc.finisherKg)
        val henSizes = floatArrayOf(56f, 66f, 76f)
        var l = M
        val top = 1460f
        for (i in 0..2) {
            val w = total * days[i] / 32f
            card(c, l, top, l + w, top + 180f, alpha(colors[i], 120))
            hen(c, l + 14f, top + 14f, henSizes[i], colors[i])
            val nx = l + 14f + henSizes[i] + 8f
            fitText(c, names[i], nx, top + 56f, l + w - nx - 10f, 30f, colors[i], true)
            fitText(c, ranges[i], nx, top + 88f, l + w - nx - 10f, 22f, SOFT)
            rr(c, l + 14f, top + 108f, l + w - 14f, top + 116f, 4f, colors[i])
            fitText(c, "${Fmt.dec(kgs[i], 1)} কেজি", l + 14f, top + 148f, w - 28f, 27f, TXT, true)
            fitText(c, "≈ ${Fmt.dec(calc.bagsOf(kgs[i]), 1)} বস্তা", l + 14f, top + 172f, w - 28f, 21f, MUTED)
            l += w + 14f
        }
    }

    // ================================================================== CHARTS
    private fun drawCharts(c: Canvas) {
        pill(c, "গ্রাফে দেখুন", 46f)
        val t1 = "কত বাড়ছে, কত খাচ্ছে — "
        val t2 = "এক নজরে"
        val size = 54f
        text(c, t1, M, 176f, size, Color.WHITE, true)
        text(c, t2, M + measure(t1, size, true), 176f, size, AMBER, true)

        // chips
        val maxDaily = rows.maxOf { calc.daily(it) }
        val chips = listOf(
            Pair("সর্বোচ্চ দৈনিক বৃদ্ধি: ${Fmt.int(rows.maxOf { it.gain })} গ্রাম (৩২ দিন)", FC),
            Pair("সর্বোচ্চ দৈনিক খাদ্য: ${Fmt.g(maxDaily)} গ্রাম/মুরগি", GC)
        )
        var cx = M
        for (ch in chips) {
            val w = measure(ch.first, 23f) + 36f
            rr(c, cx, 206f, cx + w, 254f, 24f, alpha(ch.second, 28), alpha(ch.second, 130), 2f)
            text(c, ch.first, cx + 18f, 238f, 23f, ch.second)
            cx += w + 14f
        }

        drawGrowthCard(c, 290f, 850f)
        drawFeedCard(c, 880f, 1410f)
        drawSackCard(c, 1440f, 1990f)
    }

    private fun cardTitle(c: Canvas, title: String, sub: String, top: Float) {
        text(c, title, M + 28f, top + 56f, 36f, Color.WHITE, true)
        fitText(c, sub, M + 28f, top + 92f, CW - 56f, 23f, MUTED)
    }

    private fun drawGrowthCard(c: Canvas, top: Float, bottom: Float) {
        card(c, M, top, W - M, bottom)
        cardTitle(c, "ওজন বৃদ্ধির গ্রাফ", "১টি মুরগির বয়স অনুসারে ওজন (গ্রাম)", top)
        val l = M + 92f
        val r = W - M - 30f
        val t = top + 160f
        val b = bottom - 64f
        fun xOf(d: Int) = l + d / 32f * (r - l)
        fun yOf(g: Int) = b - g / 2400f * (b - t)

        val bands = listOf(Triple(0, 14, SC), Triple(14, 25, GC), Triple(25, 32, FC))
        val bandNames = arrayOf("স্টার্টার", "গ্রোয়ার", "ফিনিশার")
        for ((i, bd) in bands.withIndex()) {
            c.drawRect(xOf(bd.first), t - 30f, xOf(bd.second), b, fillP(alpha(bd.third, 26)))
            text(c, bandNames[i], (xOf(bd.first) + xOf(bd.second)) / 2f, t - 10f, 22f, bd.third, true, Paint.Align.CENTER)
        }
        for (g in 0..2000 step 500) {
            c.drawLine(l, yOf(g), r, yOf(g), strokeP(alpha(Color.WHITE, 40), 1.5f))
            text(c, Fmt.int(g), l - 12f, yOf(g) + 7f, 20f, MUTED, false, Paint.Align.RIGHT)
        }
        for (d in 0..32 step 4) text(c, Fmt.int(d), xOf(d), b + 34f, 20f, MUTED, false, Paint.Align.CENTER)
        text(c, "বয়স (দিন)", r, bottom - 14f, 20f, MUTED, false, Paint.Align.RIGHT)

        val pts = ArrayList<Pair<Float, Float>>()
        pts.add(Pair(xOf(0), yOf(BroilerData.START_WEIGHT)))
        for (row in rows) pts.add(Pair(xOf(row.day), yOf(row.weight)))
        areaAndLine(c, pts, l, r, t, b, 6f)

        for (d in intArrayOf(7, 14, 21, 28, 32)) {
            val px = xOf(d)
            val py = yOf(rows[d - 1].weight)
            val col = phaseColor(d)
            c.drawCircle(px, py, 10f, fillP(BG))
            c.drawCircle(px, py, 10f, strokeP(col, 5f))
            val label = "${Fmt.int(rows[d - 1].weight)} গ্রাম"
            val w = measure(label, 22f, true) + 28f
            rr(c, px - w - 12f, py - 62f, px - 12f, py - 22f, 20f, Color.parseColor("#111111"), col, 2f)
            text(c, label, px - 12f - w / 2f, py - 33f, 22f, Color.WHITE, true, Paint.Align.CENTER)
        }
    }

    private fun drawFeedCard(c: Canvas, top: Float, bottom: Float) {
        card(c, M, top, W - M, bottom)
        val note = if (st.scaleDaily) "প্রতিদিন ১টি মুরগি কত গ্রাম খায় (FCR ${Fmt.dec(st.fcr, 2)} অনুযায়ী সমন্বয় করা)"
        else "প্রতিদিন ১টি মুরগি কত গ্রাম খায় — রং দেখে ফেজ বুঝুন"
        cardTitle(c, "দৈনিক খাদ্য গ্রহণ", note, top)
        val l = M + 78f
        val r = W - M - 24f
        val t = top + 140f
        val b = bottom - 60f
        val maxV = rows.maxOf { calc.daily(it) }
        val step = max(20, (ceil(maxV / 3.0 / 20.0) * 20).toInt())
        val yMax = step * 3f
        fun yOf(g: Float) = b - g / yMax * (b - t)

        for (i in 0..3) {
            val g = i * step.toFloat()
            c.drawLine(l, yOf(g), r, yOf(g), strokeP(alpha(Color.WHITE, 40), 1.5f))
            text(c, Fmt.int(g.toInt()), l - 10f, yOf(g) + 7f, 20f, MUTED, false, Paint.Align.RIGHT)
        }
        val slot = (r - l) / 32f
        val bw = slot * 0.74f
        for (row in rows) {
            val v = calc.daily(row).toFloat()
            val x = l + (row.day - 1) * slot + (slot - bw) / 2f
            val y = yOf(v)
            val col = phaseColor(row.day)
            c.drawRoundRect(RectF(x, y, x + bw, b), 5f, 5f, fillP(col))
            c.drawRoundRect(RectF(x, y, x + bw, y + (b - y) * 0.35f), 5f, 5f, fillP(alpha(Color.WHITE, 46)))
            text(c, Fmt.int(v.roundToInt()), x + bw / 2f, y - 8f, 15f, Color.parseColor("#E8E8E8"), false, Paint.Align.CENTER)
            text(c, Fmt.int(row.day), x + bw / 2f, b + 28f, 16f, MUTED, false, Paint.Align.CENTER)
        }
        text(c, "বয়স (দিন)", r, bottom - 12f, 20f, MUTED, false, Paint.Align.RIGHT)
    }

    private fun drawSackCard(c: Canvas, top: Float, bottom: Float) {
        card(c, M, top, W - M, bottom)
        val kgs = doubleArrayOf(calc.starterKg, calc.growerKg, calc.finisherKg)
        val bagsArr = DoubleArray(3) { calc.bagsOf(kgs[it]) }
        val need = max(1, ceil(bagsArr.max() / 28.0).toInt())
        var unit = need
        for (u in NICE_UNITS) if (u >= need) { unit = u; break }
        cardTitle(c, "খাদ্য বস্তার হিসাব — ${Fmt.int(st.count)} মুরগি",
            "১টি আইকন = ${Fmt.int(unit)} বস্তা (১ বস্তা = ${Fmt.int(st.bagKg)} কেজি)", top)

        val names = arrayOf("স্টার্টার", "গ্রোয়ার", "ফিনিশার")
        val ranges = arrayOf("১–১৪ দিন", "১৫–২৫ দিন", "২৬–৩২+ দিন")
        val colors = intArrayOf(SC, GC, FC)
        val iw = 28f
        val ih = 37f
        val stepX = (CW - 56f) / 28f
        for (i in 0..2) {
            val y0 = top + 124f + i * 134f
            val lbl = names[i]
            text(c, lbl, M + 28f, y0 + 26f, 28f, colors[i], true)
            text(c, "(${ranges[i]})", M + 28f + measure(lbl, 28f, true) + 10f, y0 + 26f, 22f, MUTED)
            val right = "${Fmt.dec(kgs[i], 1)} কেজি = "
            val bg = "${Fmt.dec(bagsArr[i], 1)} বস্তা"
            val bw = measure(bg, 26f, true)
            text(c, bg, W - M - 28f, y0 + 26f, 26f, colors[i], true, Paint.Align.RIGHT)
            text(c, right, W - M - 28f - bw - 4f, y0 + 26f, 24f, TXT, false, Paint.Align.RIGHT)

            val units = bagsArr[i] / unit
            val full = floor(units).toInt()
            val frac = (units - full).toFloat()
            val n = full + (if (frac > 0.02f) 1 else 0)
            for (k in 0 until n) {
                val x = M + 28f + k * stepX
                val y = y0 + 48f
                if (k < full) sack(c, x, y, iw, ih, colors[i])
                else sack(c, x, y, iw, ih, colors[i], frac)
            }
        }
    }

    // ================================================================== TABLE
    private fun drawTable(c: Canvas) {
        pill(c, "বিস্তারিত টেবিল", 46f)
        val h1 = "দিন "
        text(c, h1, M, 176f, 56f, Color.WHITE, true)
        text(c, "১–৩২", M + measure(h1, 56f, true), 176f, 56f, AMBER, true)
        fitText(c, "${Fmt.int(st.count)}টি মুরগি  •  FCR ${Fmt.dec(st.fcr, 2)}  •  ১ বস্তা = ${Fmt.int(st.bagKg)} কেজি",
            M, 226f, CW - 260f, 27f, SOFT)
        text(c, "শুরুর ওজন (০ দিন)", W - M, 112f, 22f, MUTED, false, Paint.Align.RIGHT)
        text(c, "${Fmt.int(BroilerData.START_WEIGHT)} গ্রাম", W - M, 162f, 40f, SC, true, Paint.Align.RIGHT)

        // 32-cell timeline
        val gap = 4f
        val cellW = (CW - 31f * gap) / 32f
        for (d in 1..32) {
            val x = M + (d - 1) * (cellW + gap)
            rr(c, x, 262f, x + cellW, 288f, 6f, phaseColor(d))
        }
        fun cellCx(d: Int) = M + (d - 1) * (cellW + gap) + cellW / 2f
        text(c, "১ দিন", M, 322f, 20f, MUTED)
        text(c, "১৪", cellCx(14), 322f, 20f, MUTED, false, Paint.Align.CENTER)
        text(c, "২৫", cellCx(25), 322f, 20f, MUTED, false, Paint.Align.CENTER)
        text(c, "৩২ দিন", W - M, 322f, 20f, MUTED, false, Paint.Align.RIGHT)

        // legend
        val lg = arrayOf("স্টার্টার ১–১৪", "গ্রোয়ার ১৫–২৫", "ফিনিশার ২৬–৩২+")
        val lc = intArrayOf(SC, GC, FC)
        var lx = M
        for (i in 0..2) {
            c.drawCircle(lx + 9f, 362f, 9f, fillP(lc[i]))
            text(c, lg[i], lx + 26f, 370f, 23f, SOFT)
            lx += 26f + measure(lg[i], 23f) + 34f
        }

        // column header
        val dayCx = M + 50f
        val wx = M + 96f
        val gx = M + 244f
        val dx = M + 338f
        val cx = M + 482f
        val fx = M + 610f
        val bx = M + 768f
        val hy = 424f
        fitText(c, "বয়স", dayCx, hy, 70f, 21f, MUTED, false, Paint.Align.CENTER)
        fitText(c, "ওজন (১টি)", wx, hy, 140f, 21f, MUTED)
        fitText(c, "বৃদ্ধি", gx, hy, 86f, 21f, MUTED)
        fitText(c, "দৈনিক খাদ্য", dx, hy, 136f, 21f, MUTED)
        fitText(c, "মোট খাদ্য (১টি)", cx, hy, 120f, 21f, MUTED)
        fitText(c, "মোট (${Fmt.int(st.count)}টি)", fx, hy, 150f, 21f, MUTED)
        fitText(c, "খাবার বস্তা", bx, hy, 190f, 21f, MUTED)

        val maxDaily = rows.maxOf { calc.daily(it) }
        val maxFlock = calc.flockKg(rows.last())
        val maxW = rows.last().weight.toFloat()

        for ((i, r) in rows.withIndex()) {
            val top = 444f + i * 86f
            val col = phaseColor(r.day)
            rr(c, M, top, W - M, top + 76f, 18f, alpha(Color.WHITE, if (i % 2 == 0) 11 else 22))
            rr(c, M, top + 6f, M + 8f, top + 70f, 4f, col)

            c.drawCircle(dayCx, top + 38f, 30f, fillP(col))
            text(c, Fmt.int(r.day), dayCx, top + 49f, 30f, Color.parseColor("#111111"), true, Paint.Align.CENTER)

            valueUnit(c, Fmt.int(r.weight), "গ্রাম", wx, top + 36f, 140f, 30f, TXT)
            bar(c, wx, top + 50f, 130f, r.weight / maxW, col)

            val tri = Path().apply {
                moveTo(gx, top + 50f); lineTo(gx + 20f, top + 50f); lineTo(gx + 10f, top + 34f); close()
            }
            c.drawPath(tri, fillP(col))
            fitText(c, Fmt.int(r.gain), gx + 28f, top + 50f, 56f, 30f, col, true)

            val daily = calc.daily(r)
            valueUnit(c, Fmt.g(daily), "গ্রাম", dx, top + 36f, 136f, 30f, TXT)
            bar(c, dx, top + 50f, 126f, (daily / maxDaily).toFloat(), col)

            valueUnit(c, Fmt.g(calc.cum(r)), "গ্রাম", cx, top + 47f, 120f, 28f, Color.parseColor("#DDDDDD"))

            val flock = calc.flockKg(r)
            valueUnit(c, Fmt.dec(flock, 1), "কেজি", fx, top + 36f, 150f, 30f, TXT)
            bar(c, fx, top + 50f, 140f, (flock / maxFlock).toFloat(), col)

            sack(c, bx, top + 17f, 30f, 40f, col)
            val parts = calc.bagParts(flock)
            val tx = bx + 42f
            if (parts.first > 0 && parts.second > 0) {
                fitText(c, "${Fmt.int(parts.first)} বস্তা", tx, top + 36f, 150f, 27f, TXT, true)
                fitText(c, "${Fmt.int(parts.second)} কেজি", tx, top + 62f, 150f, 22f, MUTED)
            } else if (parts.first > 0) {
                fitText(c, "${Fmt.int(parts.first)} বস্তা", tx, top + 47f, 150f, 28f, TXT, true)
            } else {
                fitText(c, "${Fmt.int(parts.second)} কেজি", tx, top + 47f, 150f, 28f, TXT, true)
            }
        }

        val fy = 444f + 32 * 86f + 36f
        fitText(c, "১ বস্তা = ${Fmt.int(st.bagKg)} কেজি  •  ${Fmt.int(st.count)} মুরগির খাদ্য = ১টির মোট খাদ্য × ${Fmt.int(st.count)}",
            M, fy, CW, 23f, MUTED)
        if (calc.k < 0.9995 || calc.k > 1.0005) {
            fitText(c, "মূল চার্ট FCR ${Fmt.dec(BroilerData.BASE_FCR, 2)} ধরে; আপনার FCR ${Fmt.dec(st.fcr, 2)} অনুযায়ী খাদ্য সমন্বয় করা হয়েছে",
                M, fy + 34f, CW, 23f, AMBER)
        }
    }
}
