package com.budgie.ui

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgie.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

/** Soft dark theme, ported from the motion kit's soft.css (dark). */
object Ui {
  val Surface = Color(0xFF2A2C33)
  val Pit = Color(0xFF25272D)
  val Ink = Color(0xFFE6E8EE)
  val Muted = Color(0xFF9A9FAB)
  val Soft = Color(0xFFC3C7D1)
  val Accent = Color(0xFF02CEFF)
  val Good = Color(0xFF5FE0A8)
  val Bad = Color(0xFFFF8A6E)
  val Warn = Color(0xFFFFB84D)
}

private const val DARK = 0x8C000000.toInt()   // rgba(0,0,0,.55)
private const val LIGHT = 0x11FFFFFF          // rgba(255,255,255,.065)

val Baloo = FontFamily(Font(R.font.baloo2_bold, FontWeight.Bold))
val Figtree = FontFamily(
  Font(R.font.figtree_medium, FontWeight.Medium),
  Font(R.font.figtree_semibold, FontWeight.SemiBold),
  Font(R.font.figtree_bold, FontWeight.Bold),
)

fun balooStyle(size: Int, color: Color = Ui.Ink) =
  TextStyle(fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = size.sp, lineHeight = (size * 1.1f).sp, color = color)

fun figtreeStyle(size: Int, color: Color = Ui.Ink, weight: FontWeight = FontWeight.SemiBold) =
  TextStyle(fontFamily = Figtree, fontWeight = weight, fontSize = size.sp, color = color)

val LabelStyle = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.7.sp, color = Ui.Muted)

/** Green (full) to red (empty), the remaining-budget colour. */
fun statusColor(ratio: Float, lightness: Float, saturation: Float = 0.55f): Color =
  Color.hsl(5f + 135f * ratio.coerceIn(0f, 1f), saturation, lightness)

/**
 * Neumorphic surface. depth 1 = raised, 0 = flat, -1 = pressed in (same model as softShadow() in Glass.tsx).
 */
fun Modifier.soft(depth: Float, corner: Dp, spread: Dp = 5.dp, color: Color = Ui.Surface): Modifier = drawBehind {
  val r = corner.toPx()
  val a = max(depth, 0f) * spread.toPx()
  val b = max(-depth, 0f) * spread.toPx()
  val w = size.width
  val h = size.height
  if (a > 0.5f) {
    drawIntoCanvas { canvas ->
      val nc = canvas.nativeCanvas
      val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
      p.maskFilter = BlurMaskFilter(a * 1.3f, BlurMaskFilter.Blur.NORMAL)
      p.color = DARK
      nc.drawRoundRect(a, a, w + a, h + a, r, r, p)
      p.color = LIGHT
      nc.drawRoundRect(-a, -a, w - a, h - a, r, r, p)
    }
  }
  drawRoundRect(color, cornerRadius = CornerRadius(r, r))
  insetShadow(r, b)
}

/** Recessed shadow inside the current bounds. */
fun DrawScope.insetShadow(r: Float, b: Float) {
  if (b < 0.5f) return
  val w = size.width
  val h = size.height
  drawIntoCanvas { canvas ->
    val nc = canvas.nativeCanvas
    val path = android.graphics.Path()
    path.addRoundRect(0f, 0f, w, h, r, r, android.graphics.Path.Direction.CW)
    nc.save()
    nc.clipPath(path)
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    p.style = android.graphics.Paint.Style.STROKE
    p.strokeWidth = b * 2f
    p.maskFilter = BlurMaskFilter(b * 1.4f, BlurMaskFilter.Blur.NORMAL)
    p.color = DARK
    nc.drawRoundRect(0f, 0f, w + 2f * b, h + 2f * b, r, r, p)
    p.color = LIGHT
    nc.drawRoundRect(-2f * b, -2f * b, w, h, r, r, p)
    nc.restore()
  }
}

/** A plain recess (no glass). */
fun Modifier.pit(corner: Dp): Modifier = drawBehind {
  val r = corner.toPx()
  drawRoundRect(Ui.Pit, cornerRadius = CornerRadius(r, r))
  insetShadow(r, 3.dp.toPx())
}

/** SoftButton: hover-less port. Press sinks it in; release springs back past rest. */
@Composable
fun SoftButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  corner: Dp = 16.dp,
  spread: Dp = 5.dp,
  description: String? = null,
  content: @Composable BoxScope.() -> Unit,
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val depth by animateFloatAsState(
    targetValue = if (pressed) -0.7f else 1f,
    animationSpec = if (pressed) spring(dampingRatio = 1f, stiffness = 1400f) else spring(dampingRatio = 0.33f, stiffness = 380f),
    label = "depth",
  )
  val scale by animateFloatAsState(
    targetValue = if (pressed) 0.94f else 1f,
    animationSpec = spring(dampingRatio = 0.4f, stiffness = 500f),
    label = "scale",
  )
  var semanticsModifier: Modifier = Modifier
  if (description != null) semanticsModifier = Modifier.semantics { contentDescription = description }
  Box(
    modifier
      .soft(depth, corner, spread)
      .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
      .then(semanticsModifier),
    contentAlignment = Alignment.Center,
  ) {
    Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }, contentAlignment = Alignment.Center, content = content)
  }
}

private class Blob(val x: Float, val y: Float, val r: Float, val sx: Float, val sy: Float, val color: Color)

private val Blobs = listOf(
  Blob(0.20f, 0.25f, 0.30f, 1.0f, 0.7f, Color(0xFF3F3CC0)),
  Blob(0.70f, 0.20f, 0.28f, 0.8f, 1.2f, Color(0xFFE0457A)),
  Blob(0.50f, 0.70f, 0.34f, 0.6f, 0.9f, Color(0xFF0AA3D6)),
  Blob(0.15f, 0.75f, 0.26f, 0.9f, 0.5f, Color(0xFFA177FF)),
  Blob(0.85f, 0.65f, 0.28f, 0.5f, 0.8f, Color(0xFF2FD4A0)),
)

/** GlassWell: a recessed window onto a slowly moving aurora, frosted. */
@Composable
fun GlassWell(
  modifier: Modifier = Modifier,
  corner: Dp = 16.dp,
  frost: Float = 0.6f,
  content: @Composable BoxScope.() -> Unit = {},
) {
  val t = rememberInfiniteTransition(label = "aurora")
  val phase by t.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(41000, easing = LinearEasing), RepeatMode.Reverse),
    label = "phase",
  )
  Box(
    modifier
      .clip(RoundedCornerShape(corner))
      .drawBehind {
        drawRect(Color(0xFF0D1030))
        val w = size.width
        val h = size.height
        val m = max(w, h)
        Blobs.forEachIndexed { i, b ->
          val a = phase * 2f * PI.toFloat()
          val c = Offset((b.x + 0.18f * sin(a * b.sx + i)) * w, (b.y + 0.2f * cos(a * b.sy + i * 2)) * h)
          val radius = m * b.r
          drawCircle(
            Brush.radialGradient(listOf(b.color.copy(alpha = 0.9f), b.color.copy(alpha = 0f)), center = c, radius = radius),
            radius = radius,
            center = c,
          )
        }
        drawRect(Color(0xFF262830).copy(alpha = frost))
        insetShadow(corner.toPx(), 3.dp.toPx())
      },
    content = content,
  )
}

/** Segmented control with a raised pill that springs between options. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
  BoxWithConstraints(modifier.fillMaxWidth().height(52.dp).pit(16.dp).padding(4.dp)) {
    val segW = maxWidth / options.size
    val x by animateDpAsState(segW * selected, spring(dampingRatio = 0.6f, stiffness = 380f), label = "seg")
    Box(Modifier.offset(x = x).width(segW).fillMaxHeight().soft(1f, 12.dp, 3.dp))
    Row(Modifier.fillMaxSize()) {
      options.forEachIndexed { i, label ->
        Box(
          Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(i) },
          contentAlignment = Alignment.Center,
        ) {
          Text(label, style = figtreeStyle(15, if (i == selected) Ui.Ink else Ui.Muted, FontWeight.Bold))
        }
      }
    }
  }
}

/** Bars with a thin curved line through their tops. Bars run past the bottom edge, so clip the parent. */
@Composable
fun BarsCurve(
  values: List<Int>,
  barColor: Color,
  lineColor: Color,
  modifier: Modifier = Modifier,
  gap: Dp = 4.dp,
  lineWidth: Dp = 2.dp,
) {
  Canvas(modifier) {
    val n = values.size
    if (n == 0) return@Canvas
    val mx = max(1, values.max())
    val g = if (n > 20) gap.toPx() / 2f else gap.toPx()
    val bw = max(1f, (size.width - g * (n - 1)) / n)
    val path = Path()
    var px = 0f
    var py = 0f
    values.forEachIndexed { i, v ->
      val bh = max(size.height * 0.05f, size.height * v / mx)
      val x = i * (bw + g)
      val y = size.height - bh
      drawRoundRect(barColor, Offset(x, y), Size(bw, bh + 8.dp.toPx()), CornerRadius(min(bw / 2f, 5.dp.toPx())))
      val cx = x + bw / 2f
      if (i == 0) {
        path.moveTo(cx, y)
      } else {
        val m = (px + cx) / 2f
        path.cubicTo(m, py, m, y, cx, y)
      }
      px = cx
      py = y
    }
    drawPath(path, lineColor, style = Stroke(lineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
  }
}

/** AnimatedInput port: characters spring up as they're typed and drop away when deleted. */
@Composable
fun SpringDigits(text: String, style: TextStyle, modifier: Modifier = Modifier) {
  var prev by remember { mutableStateOf(text) }
  var ghost by remember { mutableStateOf("") }
  LaunchedEffect(text) {
    val old = prev
    prev = text
    if (old.length > text.length && old.startsWith(text)) {
      ghost = old.substring(text.length)
      delay(170)
      ghost = ""
    } else {
      ghost = ""
    }
  }
  Row(modifier) {
    text.forEachIndexed { i, ch -> key(i) { LandingChar(ch, style) } }
    ghost.forEachIndexed { i, ch -> key("g$i") { DroppingChar(ch, style) } }
  }
}

@Composable
private fun LandingChar(ch: Char, style: TextStyle) {
  val y = remember { Animatable(0.7f) }
  LaunchedEffect(Unit) { y.animateTo(0f, spring(dampingRatio = 0.37f, stiffness = 600f)) }
  Text(
    ch.toString(),
    style = style,
    modifier = Modifier.graphicsLayer {
      translationY = y.value * size.height
      alpha = (1f - y.value / 0.7f).coerceIn(0f, 1f)
    },
  )
}

@Composable
private fun DroppingChar(ch: Char, style: TextStyle) {
  val v = remember { Animatable(0f) }
  LaunchedEffect(Unit) { v.animateTo(1f, tween(160, easing = FastOutLinearInEasing)) }
  Text(
    ch.toString(),
    style = style,
    modifier = Modifier.graphicsLayer {
      translationY = v.value * 0.7f * size.height
      alpha = 1f - v.value
    },
  )
}

private val Frames = intArrayOf(R.drawable.bird_0, R.drawable.bird_2, R.drawable.bird_3, R.drawable.bird_4, R.drawable.bird_5)

/**
 * The budgie. flapKey > 0 plays one flap + hop each time it changes; loop keeps it flying (celebration);
 * worry adds a nervous shake every few seconds (running low).
 */
@Composable
fun Bird(size: Dp, modifier: Modifier = Modifier, flapKey: Int = 0, loop: Boolean = false, worry: Boolean = false) {
  var frame by remember { mutableIntStateOf(0) }
  val hop = remember { Animatable(0f) }
  LaunchedEffect(flapKey, loop) {
    if (loop) {
      while (true) {
        for (f in 1..4) { frame = f; delay(200) }
      }
    } else if (flapKey > 0) {
      for (f in intArrayOf(1, 2, 3, 4, 1)) { frame = f; delay(190) }
      frame = 0
    }
  }
  LaunchedEffect(flapKey) {
    if (flapKey > 0) {
      hop.animateTo(-1f, tween(260))
      hop.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 300f))
    }
  }
  val t = rememberInfiniteTransition(label = "bird")
  val cycle by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "cycle")
  val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "bob")
  val shake = if (worry && cycle > 0.68f && cycle < 0.88f) sin((cycle - 0.68f) / 0.2f * 4f * PI.toFloat()) else 0f
  Image(
    painter = painterResource(Frames[frame]),
    contentDescription = "Budgie mascot",
    modifier = modifier
      .size(size)
      .graphicsLayer {
        translationY = hop.value * 12.dp.toPx() + (if (loop) -bob * 12.dp.toPx() else 0f)
        translationX = shake * 3.dp.toPx()
        rotationZ = shake * 5f + hop.value * 9f
      },
  )
}

/** Backspace icon (drawn, so no icon library is needed). */
@Composable
fun DeleteIcon(color: Color, size: Dp = 30.dp) {
  Canvas(Modifier.size(size)) {
    val k = this.size.width / 24f
    val p = Path().apply {
      moveTo(9f * k, 5f * k); lineTo(21f * k, 5f * k); lineTo(21f * k, 19f * k); lineTo(9f * k, 19f * k); lineTo(3f * k, 12f * k); close()
      moveTo(12f * k, 9.5f * k); lineTo(17f * k, 14.5f * k)
      moveTo(17f * k, 9.5f * k); lineTo(12f * k, 14.5f * k)
    }
    drawPath(p, color, style = Stroke(2.2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
  }
}

/** Return (enter) icon. */
@Composable
fun ReturnIcon(color: Color, size: Dp = 30.dp) {
  Canvas(Modifier.size(size)) {
    val k = this.size.width / 24f
    val p = Path().apply {
      moveTo(20f * k, 5f * k); lineTo(20f * k, 13f * k); quadraticBezierTo(20f * k, 15f * k, 18f * k, 15f * k); lineTo(6f * k, 15f * k)
      moveTo(10f * k, 11f * k); lineTo(6f * k, 15f * k); lineTo(10f * k, 19f * k)
    }
    drawPath(p, color, style = Stroke(2.4f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
  }
}

/** Back arrow button used on the inner screens. */
@Composable
fun BackButton(onClick: () -> Unit) {
  SoftButton(onClick, Modifier.size(44.dp), corner = 14.dp, spread = 4.dp, description = "Back") {
    Canvas(Modifier.size(24.dp)) {
      val k = this.size.width / 24f
      val p = Path().apply { moveTo(15f * k, 5f * k); lineTo(8f * k, 12f * k); lineTo(15f * k, 19f * k) }
      drawPath(p, Ui.Ink, style = Stroke(2.4f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
  }
}

/** Small "Running low" pill with a blinking amber dot. */
@Composable
fun LowChip() {
  val t = rememberInfiniteTransition(label = "blink")
  val a by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")
  Row(
    Modifier
      .clip(RoundedCornerShape(50))
      .drawBehind { drawRect(Color(0xB314151A)) }
      .padding(horizontal = 8.dp, vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(6.dp).graphicsLayer { alpha = a }.drawBehind { drawCircle(Ui.Warn) })
    Box(Modifier.width(5.dp))
    Text("RUNNING LOW", style = figtreeStyle(11, Ui.Ink, FontWeight.Bold).copy(letterSpacing = 0.5.sp))
  }
}
