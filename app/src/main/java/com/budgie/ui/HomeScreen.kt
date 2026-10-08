package com.budgie.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.budgie.data.Budget
import com.budgie.data.BudgetStore
import com.budgie.data.money
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WhenFormat = DateTimeFormatter.ofPattern("h:mm a · EEE, MMM d", Locale.US)

fun formatWhen(ts: Long): String = WhenFormat.format(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()))

private val Keys = listOf(
  listOf("1", "2", "3"),
  listOf("4", "5", "6"),
  listOf("7", "8", "9"),
  listOf("del", "0", "ok"),
)

@Composable
fun HomeScreen(budget: Budget, onArchive: () -> Unit, onNew: () -> Unit) {
  val context = LocalContext.current
  var typed by rememberSaveable { mutableStateOf("") }
  var undoable by remember { mutableStateOf(false) }
  var fx by remember { mutableIntStateOf(0) }
  val now = System.currentTimeMillis()
  val perDay = budget.perDay(now)
  val days = budget.days(now)
  val ratio = budget.ratio
  val bg by animateColorAsState(statusColor(ratio, 0.34f), tween(600), label = "bg")
  val pop = remember { Animatable(1f) }
  LaunchedEffect(fx) {
    if (fx > 0) {
      pop.animateTo(1.03f, tween(140))
      pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 400f))
    }
  }

  fun press(k: String) {
    when (k) {
      "del" -> typed = typed.dropLast(1)
      "ok" -> {
        val n = typed.toIntOrNull() ?: 0
        if (n > 0) {
          BudgetStore.addEntry(context, n)
          typed = ""
          undoable = true
          fx += 1
        }
      }
      else -> {
        val t = (typed + k).trimStart('0')
        if (t.length <= 5) {
          typed = t
          undoable = false
        }
      }
    }
  }

  val lastAmount = budget.entries.lastOrNull()?.amount ?: 0
  val showUndo = undoable && typed.isEmpty() && budget.entries.isNotEmpty()

  Column(
    Modifier.fillMaxSize().background(Ui.Surface).systemBarsPadding().padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Top bar
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
      Text("Budgie", style = balooStyle(28))
      Spacer(Modifier.weight(1f))
      SoftButton(onArchive, Modifier.height(44.dp), corner = 14.dp, spread = 4.dp) {
        Text("Archive", Modifier.padding(horizontal = 16.dp), style = figtreeStyle(15, Ui.Ink, FontWeight.Bold))
      }
      Spacer(Modifier.padding(6.dp))
      SoftButton(onNew, Modifier.height(44.dp), corner = 14.dp, spread = 4.dp) {
        Text("New budget", Modifier.padding(horizontal = 16.dp), style = figtreeStyle(15, Ui.Accent, FontWeight.Bold))
      }
    }

    // Remaining budget, colour-coded, with the per-day graph behind it
    Box(
      Modifier
        .fillMaxWidth()
        .height(172.dp)
        .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
        .clip(RoundedCornerShape(26.dp))
        .drawBehind {
          drawRect(bg)
          drawRect(Brush.radialGradient(listOf(Color(0x2EFFFFFF), Color.Transparent), center = Offset(size.width * 0.12f, 0f), radius = size.width * 0.55f))
          drawRect(Brush.radialGradient(listOf(Color(0x59000000), Color.Transparent), center = Offset(size.width, size.height * 1.2f), radius = size.width * 0.6f))
        },
    ) {
      BarsCurve(
        perDay,
        statusColor(ratio, 0.46f),
        statusColor(ratio, 0.82f, 0.6f),
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
        gap = 6.dp,
      )
      Box(Modifier.matchParentSize().drawBehind { insetShadow(26.dp.toPx(), 4.dp.toPx()) })
      Row(Modifier.fillMaxSize().padding(start = 22.dp, top = 18.dp, end = 16.dp, bottom = 18.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (budget.remaining < 0) "OVER BUDGET" else "REMAINING BUDGET", style = LabelStyle.copy(color = Ui.Ink))
            if (budget.isLow) LowChip()
          }
          Text(money(budget.remaining), style = balooStyle(58))
          Text(
            "of ${money(budget.amount)} · day $days · ${money(budget.spent / days)} a day",
            style = figtreeStyle(15),
          )
        }
        Bird(84.dp, flapKey = fx, worry = budget.isLow)
      }
    }

    // Entered amount (spring digits) + undo
    GlassWell(Modifier.fillMaxWidth().height(52.dp), corner = 18.dp) {
      Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showUndo) {
          Text(
            "Undo ${money(-lastAmount)}",
            Modifier.clickable {
              BudgetStore.undoLast(context)
              undoable = false
            },
            style = figtreeStyle(15, Ui.Accent, FontWeight.Bold).copy(textDecoration = TextDecoration.Underline),
          )
        } else {
          Text("Subtract", style = figtreeStyle(15, Ui.Soft))
        }
        Spacer(Modifier.weight(1f))
        val digitStyle = balooStyle(30, if (typed.isEmpty()) Ui.Muted else Ui.Ink)
        Text("−$", style = digitStyle)
        if (typed.isEmpty()) Text("0", style = digitStyle) else SpringDigits(typed, digitStyle)
      }
    }

    // Number pad
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Keys.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          row.forEach { k ->
            val label = when (k) { "del" -> "Delete last digit"; "ok" -> "Subtract from budget"; else -> null }
            SoftButton({ press(k) }, Modifier.weight(1f).height(56.dp), corner = 18.dp, spread = 4.dp, description = label) {
              when (k) {
                "del" -> DeleteIcon(Ui.Ink)
                "ok" -> ReturnIcon(Ui.Accent)
                else -> Text(k, style = balooStyle(30))
              }
            }
          }
        }
      }
    }

    // Entries log
    Text("ENTRIES", style = LabelStyle)
    Box(Modifier.weight(1f).fillMaxWidth().pit(18.dp).clip(RoundedCornerShape(18.dp))) {
      if (budget.entries.isEmpty()) {
        Text("No entries yet", Modifier.align(Alignment.Center), style = figtreeStyle(15, Ui.Muted))
      } else {
        LazyColumn(Modifier.fillMaxSize()) {
          items(budget.entries.reversed()) { e -> EntryRow(e.amount, e.ts) }
        }
      }
    }
  }
}

@Composable
fun EntryRow(amount: Int, ts: Long) {
  Row(
    Modifier
      .fillMaxWidth()
      .height(46.dp)
      .drawBehind { drawRect(Color(0x0FFFFFFF), topLeft = Offset(0f, size.height - 1f)) }
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(money(-amount), style = balooStyle(20))
    Spacer(Modifier.weight(1f))
    Text(formatWhen(ts), style = figtreeStyle(14, Ui.Muted))
  }
}
