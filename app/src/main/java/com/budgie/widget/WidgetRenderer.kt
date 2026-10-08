package com.budgie.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.budgie.R
import com.budgie.data.Budget
import com.budgie.data.money
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Draws the whole widget into one bitmap, in dp units, using the same grid the tap targets in
 * BudgieWidget use: 12 dp padding, 78 dp header + 10 dp gap, four key rows (8 dp gaps), 40 dp bottom row.
 */
object WidgetRenderer {
  const val PAD = 12
  const val HEADER = 78
  const val HEADER_BLOCK = 88
  const val GAP = 8
  const val BOTTOM = 40
  const val OPEN_W = 76

  data class State(val budget: Budget?, val typed: String, val undoable: Boolean, val frame: Int, val chip: Int)

  private const val SURFACE = 0xFF2A2C33.toInt()
  private const val INK = 0xFFE6E8EE.toInt()
  private const val SOFT = 0xFFC3C7D1.toInt()
  private const val ACCENT = 0xFF02CEFF.toInt()
  private const val WARN = 0xFFFFB84D.toInt()
  private const val DARK = 0x8C000000.toInt()
  private const val LIGHT = 0x11FFFFFF

  private val frameIds = intArrayOf(R.drawable.bird_0, R.drawable.bird_2, R.drawable.bird_3, R.drawable.bird_4, R.drawable.bird_5)
  private val birdCache = HashMap<Int, Bitmap>()
  private var baloo: Typeface? = null
  private var figtree: Typeface? = null

  private fun fonts(context: Context) {
    if (baloo == null) baloo = runCatching { ResourcesCompat.getFont(context, R.font.baloo2_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    if (figtree == null) figtree = runCatching { ResourcesCompat.getFont(context, R.font.figtree_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
  }

  private fun bird(context: Context, frame: Int): Bitmap? {
    val id = frameIds[frame.coerceIn(0, frameIds.size - 1)]
    return birdCache[id] ?: runCatching {
      val opts = BitmapFactory.Options().apply { inScaled = false }
      BitmapFactory.decodeResource(context.resources, id, opts)
    }.getOrNull()?.also { birdCache[id] = it }
  }

  fun render(context: Context, wDp: Float, hDp: Float, state: State): Bitmap {
    fonts(context)
    val w = if (wDp > 10f) wDp else 380f
    val h = if (hDp > 10f) hDp else 372f
    val dm = context.resources.displayMetrics
    var s = min(dm.density, 2.75f)
    // Widgets may hold about 1.5x a screenful of bitmap memory in total, and Glance sends one bitmap per
    // widget size (portrait + landscape), so keep each one at about a third of a screen.
    val maxPixels = min(1_200_000f, dm.widthPixels.toFloat() * dm.heightPixels * 0.35f)
    if (w * h * s * s > maxPixels) s = sqrt(maxPixels / (w * h))
    val bmp = Bitmap.createBitmap(max(1, (w * s).roundToInt()), max(1, (h * s).roundToInt()), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(s, s)

    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = SURFACE
    c.drawRoundRect(RectF(0f, 0f, w, h), 28f, 28f, p)

    drawHeader(context, c, RectF(PAD.toFloat(), PAD.toFloat(), w - PAD, (PAD + HEADER).toFloat()), state)
    drawKeys(c, w, h, state.budget != null)
    drawBottom(c, w, h, state)
    return bmp
  }

  private fun drawHeader(context: Context, c: Canvas, r: RectF, state: State) {
    val budget = state.budget
    val ratio = budget?.ratio ?: 1f
    val hue = 5f + 135f * ratio
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val clip = Path().apply { addRoundRect(r, 20f, 20f, Path.Direction.CW) }

    c.save()
    c.clipPath(clip)
    p.color = ColorUtils.HSLToColor(floatArrayOf(hue, 0.55f, 0.34f))
    c.drawRect(r, p)
    p.shader = RadialGradient(r.left + r.width() * 0.12f, r.top, r.width() * 0.55f, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
    c.drawRect(r, p)
    p.shader = RadialGradient(r.right, r.bottom + 16f, r.width() * 0.6f, 0x59000000, 0x00000000, Shader.TileMode.CLAMP)
    c.drawRect(r, p)
    p.shader = null

    // Per-day bars + curve behind the number
    if (budget != null) {
      val all = budget.perDay()
      val values = if (all.size > 31) all.takeLast(31) else all
      val n = values.size
      val mx = max(1, values.maxOrNull() ?: 1)
      val left = r.left + 10f
      val width = r.width() - 20f
      val g = if (n > 20) 2f else 4f
      val bw = max(1f, (width - g * (n - 1)) / n)
      val curve = Path()
      var px = 0f
      var py = 0f
      p.color = ColorUtils.HSLToColor(floatArrayOf(hue, 0.55f, 0.46f))
      values.forEachIndexed { i, v ->
        val bh = max(3f, HEADER * 0.52f * v / mx)
        val x = left + i * (bw + g)
        val y = r.bottom - bh
        c.drawRoundRect(RectF(x, y, x + bw, r.bottom + 6f), min(bw / 2f, 5f), min(bw / 2f, 5f), p)
        val cx = x + bw / 2f
        if (i == 0) curve.moveTo(cx, y) else {
          val m = (px + cx) / 2f
          curve.cubicTo(m, py, m, y, cx, y)
        }
        px = cx
        py = y
      }
      val line = Paint(Paint.ANTI_ALIAS_FLAG)
      line.style = Paint.Style.STROKE
      line.strokeWidth = 2f
      line.strokeCap = Paint.Cap.ROUND
      line.color = ColorUtils.HSLToColor(floatArrayOf(hue, 0.6f, 0.82f))
      c.drawPath(curve, line)
    }
    insetShadow(c, r, 20f, 3f)
    c.restore()

    // Bird (right)
    val birdRect = RectF(r.right - 10f - 60f, r.top + 9f, r.right - 10f, r.top + 69f)
    bird(context, state.frame)?.let {
      val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
      if (state.frame > 0) c.drawBitmap(it, null, RectF(birdRect.left, birdRect.top - 6f, birdRect.right, birdRect.bottom - 6f), bp)
      else c.drawBitmap(it, null, birdRect, bp)
    }

    // Label (+ running-low chip)
    val t = Paint(Paint.ANTI_ALIAS_FLAG)
    t.color = INK
    t.typeface = figtree
    t.textSize = 12.5f
    t.letterSpacing = 0.05f
    val label = when {
      budget == null -> "NO BUDGET YET"
      budget.remaining < 0 -> "OVER BUDGET"
      else -> "REMAINING BUDGET"
    }
    val lx = r.left + 16f
    c.drawText(label, lx, r.top + 26f, t)
    if (budget != null && budget.isLow) {
      val chipX = lx + t.measureText(label) + 8f
      val cp = Paint(Paint.ANTI_ALIAS_FLAG)
      cp.typeface = figtree
      cp.textSize = 10f
      cp.letterSpacing = 0.04f
      val chipText = "RUNNING LOW"
      val chipW = 8f + 6f + 5f + cp.measureText(chipText) + 8f
      if (chipX + chipW < birdRect.left - 4f) {
        cp.color = 0xB314151A.toInt()
        c.drawRoundRect(RectF(chipX, r.top + 13f, chipX + chipW, r.top + 31f), 9f, 9f, cp)
        cp.color = WARN
        c.drawCircle(chipX + 11f, r.top + 22f, 3f, cp)
        cp.color = INK
        c.drawText(chipText, chipX + 19f, r.top + 25.5f, cp)
      }
    }

    // Big number
    t.typeface = baloo
    t.letterSpacing = 0f
    t.textSize = if (budget == null) 26f else 42f
    val number = if (budget == null) "Tap to start" else money(budget.remaining)
    c.drawText(number, lx, r.top + (if (budget == null) 58f else 66f), t)

    // "−$15" chip while the budgie celebrates
    if (state.chip > 0) {
      val cp = Paint(Paint.ANTI_ALIAS_FLAG)
      cp.typeface = baloo
      cp.textSize = 17f
      val text = money(-state.chip)
      val cw = cp.measureText(text) + 18f
      val cx = birdRect.left - cw - 4f
      cp.color = INK
      c.drawRoundRect(RectF(cx, r.top + 10f, cx + cw, r.top + 32f), 11f, 11f, cp)
      cp.color = SURFACE
      c.drawText(text, cx + 9f, r.top + 27f, cp)
    }
  }

  private fun drawKeys(c: Canvas, w: Float, h: Float, enabled: Boolean) {
    val rowsTop = (PAD + HEADER_BLOCK).toFloat()
    val rowsBottom = h - PAD - BOTTOM - 2f
    val slot = (rowsBottom - rowsTop) / 4f
    val keyH = slot - GAP
    val colW = (w - 2 * PAD - 2 * GAP) / 3f
    val labels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "del", "0", "ok")
    val t = Paint(Paint.ANTI_ALIAS_FLAG)
    t.typeface = baloo
    t.textSize = min(28f, keyH * 0.62f)
    t.textAlign = Paint.Align.CENTER
    t.color = if (enabled) INK else 0xFF5B5F6A.toInt()
    labels.forEachIndexed { idx, k ->
      val row = idx / 3
      val col = idx % 3
      val left = PAD + col * (colW + GAP)
      val top = rowsTop + row * slot
      val rect = RectF(left, top, left + colW, top + keyH)
      raised(c, rect, 14f, 3f)
      val cx = rect.centerX()
      val cy = rect.centerY()
      val icon = min(26f, keyH * 0.6f)
      when (k) {
        "del" -> drawDelete(c, cx, cy, icon, t.color)
        "ok" -> drawReturn(c, cx, cy, icon, if (enabled) ACCENT else t.color)
        else -> c.drawText(k, cx, cy - (t.ascent() + t.descent()) / 2f, t)
      }
    }
  }

  private fun drawBottom(c: Canvas, w: Float, h: Float, state: State) {
    val top = h - PAD - BOTTOM
    val bottom = h - PAD
    val open = RectF(w - PAD - OPEN_W, top, w - PAD, bottom)
    val well = RectF(PAD.toFloat(), top, open.left - 10f, bottom)

    // Glass readout: a still aurora under frost
    val clip = Path().apply { addRoundRect(well, 14f, 14f, Path.Direction.CW) }
    c.save()
    c.clipPath(clip)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0xFF0D1030.toInt()
    c.drawRect(well, p)
    val blobs = listOf(
      Triple(0.15f, 0.2f, 0xFF3F3CC0.toInt()),
      Triple(0.45f, 0.9f, 0xFF0AA3D6.toInt()),
      Triple(0.7f, 0.1f, 0xFFE0457A.toInt()),
      Triple(0.9f, 0.8f, 0xFF2FD4A0.toInt()),
    )
    for ((fx, fy, color) in blobs) {
      val cx = well.left + well.width() * fx
      val cy = well.top + well.height() * fy
      val rad = well.width() * 0.32f
      p.shader = RadialGradient(cx, cy, rad, ColorUtils.setAlphaComponent(color, 220), ColorUtils.setAlphaComponent(color, 0), Shader.TileMode.CLAMP)
      c.drawRect(well, p)
    }
    p.shader = null
    p.color = 0x99262830.toInt()
    c.drawRect(well, p)
    insetShadow(c, well, 14f, 3f)
    c.restore()

    val t = Paint(Paint.ANTI_ALIAS_FLAG)
    val baseline = well.centerY()
    val budget = state.budget
    when {
      state.typed.isNotEmpty() -> {
        t.typeface = baloo
        t.textSize = 24f
        t.color = INK
        c.drawText("−$" + state.typed, well.left + 14f, baseline - (t.ascent() + t.descent()) / 2f, t)
      }
      state.undoable && budget != null && budget.entries.isNotEmpty() -> {
        t.typeface = figtree
        t.textSize = 14.5f
        t.color = ACCENT
        t.isUnderlineText = true
        c.drawText("Undo " + money(-budget.entries.last().amount), well.left + 14f, baseline - (t.ascent() + t.descent()) / 2f, t)
      }
      else -> {
        t.typeface = figtree
        t.textSize = 14.5f
        t.color = SOFT
        c.drawText(if (budget == null) "Set a budget in the app" else "Enter amount", well.left + 14f, baseline - (t.ascent() + t.descent()) / 2f, t)
      }
    }

    raised(c, open, 14f, 3f)
    t.isUnderlineText = false
    t.typeface = figtree
    t.textSize = 14.5f
    t.color = INK
    t.textAlign = Paint.Align.CENTER
    c.drawText("Open", open.centerX(), open.centerY() - (t.ascent() + t.descent()) / 2f, t)
  }

  /** Raised neumorphic plate: dark shadow lower-right, light upper-left, then the surface. */
  private fun raised(c: Canvas, r: RectF, radius: Float, d: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.maskFilter = BlurMaskFilter(d * 1.6f, BlurMaskFilter.Blur.NORMAL)
    p.color = DARK
    c.drawRoundRect(RectF(r.left + d, r.top + d, r.right + d, r.bottom + d), radius, radius, p)
    p.color = LIGHT
    c.drawRoundRect(RectF(r.left - d, r.top - d, r.right - d, r.bottom - d), radius, radius, p)
    p.maskFilter = null
    p.color = SURFACE
    c.drawRoundRect(r, radius, radius, p)
  }

  /** Recessed edge inside r (call while clipped to r). */
  private fun insetShadow(c: Canvas, r: RectF, radius: Float, b: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.style = Paint.Style.STROKE
    p.strokeWidth = b * 2f
    p.maskFilter = BlurMaskFilter(b * 1.4f, BlurMaskFilter.Blur.NORMAL)
    p.color = DARK
    c.drawRoundRect(RectF(r.left, r.top, r.right + 2 * b, r.bottom + 2 * b), radius, radius, p)
    p.color = LIGHT
    c.drawRoundRect(RectF(r.left - 2 * b, r.top - 2 * b, r.right, r.bottom), radius, radius, p)
  }

  private fun strokePaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    this.color = color
  }

  private fun drawDelete(c: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
    val k = size / 24f
    val x0 = cx - size / 2f
    val y0 = cy - size / 2f
    fun px(v: Float) = x0 + v * k
    fun py(v: Float) = y0 + v * k
    val path = Path().apply {
      moveTo(px(9f), py(5f)); lineTo(px(21f), py(5f)); lineTo(px(21f), py(19f)); lineTo(px(9f), py(19f)); lineTo(px(3f), py(12f)); close()
      moveTo(px(12f), py(9.5f)); lineTo(px(17f), py(14.5f))
      moveTo(px(17f), py(9.5f)); lineTo(px(12f), py(14.5f))
    }
    c.drawPath(path, strokePaint(color, 2.2f * k))
  }

  private fun drawReturn(c: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
    val k = size / 24f
    val x0 = cx - size / 2f
    val y0 = cy - size / 2f
    fun px(v: Float) = x0 + v * k
    fun py(v: Float) = y0 + v * k
    val path = Path().apply {
      moveTo(px(20f), py(5f)); lineTo(px(20f), py(13f)); quadTo(px(20f), py(15f), px(18f), py(15f)); lineTo(px(6f), py(15f))
      moveTo(px(10f), py(11f)); lineTo(px(6f), py(15f)); lineTo(px(10f), py(19f))
    }
    c.drawPath(path, strokePaint(color, 2.4f * k))
  }
}
