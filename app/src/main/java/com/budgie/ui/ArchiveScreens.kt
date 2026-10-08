package com.budgie.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgie.data.Budget
import com.budgie.data.day
import com.budgie.data.money
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val ShortDate = DateTimeFormatter.ofPattern("MMM d", Locale.US)

fun shortDate(ts: Long): String = ShortDate.format(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()))

fun rangeLabel(b: Budget): String = shortDate(b.startTs) + " – " + shortDate(if (b.endTs > 0) b.endTs else System.currentTimeMillis())

private val SortNames = listOf("Newest", "Oldest", "Lowest", "Highest")

@Composable
fun ArchiveScreen(archive: List<Budget>, onBack: () -> Unit, onOpen: (Budget) -> Unit) {
  var sort by rememberSaveable { mutableIntStateOf(0) }
  val sorted = when (sort) {
    1 -> archive.sortedBy { it.startTs }
    2 -> archive.sortedBy { it.amount }
    3 -> archive.sortedByDescending { it.amount }
    else -> archive.sortedByDescending { it.startTs }
  }
  Column(
    Modifier.fillMaxSize().background(Ui.Surface).systemBarsPadding().padding(horizontal = 10.dp, vertical = 20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Header("Archive", onBack, Modifier.padding(horizontal = 10.dp))
    Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("SORT BY", style = LabelStyle)
      Segmented(SortNames, sort, { sort = it })
    }
    if (archive.isEmpty()) {
      Text(
        "No archived budgets yet. When you start a new budget, the old one lands here with its graphs.",
        Modifier.padding(20.dp),
        style = figtreeStyle(15, Ui.Muted),
      )
    } else {
      LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        items(sorted, key = { it.startTs }) { b -> ArchiveCard(b) { onOpen(b) } }
      }
    }
  }
}

@Composable
private fun ArchiveCard(b: Budget, onClick: () -> Unit) {
  val left = b.remaining
  Column(
    Modifier
      .fillMaxWidth()
      .soft(1f, 22.dp, 5.dp)
      .clip(RoundedCornerShape(22.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(verticalAlignment = Alignment.Top) {
      Column(Modifier.weight(1f)) {
        Text(rangeLabel(b), style = figtreeStyle(17, Ui.Ink, FontWeight.Bold))
        Text("${b.days()} days · ${b.entries.size} entries", style = figtreeStyle(14, Ui.Muted))
      }
      Text(money(b.amount), style = balooStyle(26))
    }
    GlassWell(Modifier.fillMaxWidth().height(40.dp), corner = 12.dp, frost = 0.62f) {
      BarsCurve(
        b.perDay(),
        Ui.Accent.copy(alpha = 0.55f),
        Ui.Ink,
        Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 4.dp),
        lineWidth = 1.5.dp,
      )
    }
    Row {
      Text("Spent ${money(b.spent)}", style = figtreeStyle(14, Ui.Muted, FontWeight.Bold))
      Spacer(Modifier.weight(1f))
      Text(
        if (left >= 0) "${money(left)} left over" else "${money(-left)} over",
        style = figtreeStyle(14, if (left >= 0) Ui.Good else Ui.Bad, FontWeight.Bold),
      )
    }
  }
}

@Composable
fun Header(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
  Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
    BackButton(onBack)
    Text(title, Modifier.weight(1f), style = balooStyle(26))
    Bird(48.dp)
  }
}

@Composable
fun ArchiveDetailScreen(b: Budget, onBack: () -> Unit) {
  var sort by rememberSaveable { mutableIntStateOf(0) }
  val perDay = b.perDay()
  val biggestDay = perDay.indices.maxByOrNull { perDay[it] } ?: 0
  val entries = when (sort) {
    1 -> b.entries.sortedBy { it.ts }
    2 -> b.entries.sortedBy { it.amount }
    3 -> b.entries.sortedByDescending { it.amount }
    else -> b.entries.sortedByDescending { it.ts }
  }
  val left = b.remaining
  Column(
    Modifier.fillMaxSize().background(Ui.Surface).systemBarsPadding().padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Header(rangeLabel(b), onBack)

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      StatTile("Budget", money(b.amount), Ui.Ink, Modifier.weight(1f))
      StatTile("Spent", money(b.spent), Ui.Ink, Modifier.weight(1f))
      StatTile(if (left >= 0) "Left over" else "Over by", money(kotlin.math.abs(left)), if (left >= 0) Ui.Good else Ui.Bad, Modifier.weight(1f))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row {
        Text("Remaining over time", style = figtreeStyle(16, Ui.Ink, FontWeight.Bold))
        Spacer(Modifier.weight(1f))
        Text("${money(b.amount)} to $0", style = figtreeStyle(13, Ui.Muted))
      }
      GlassWell(Modifier.fillMaxWidth().height(104.dp), corner = 16.dp) {
        RemainingLine(b, Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp))
      }
      Row(Modifier.padding(horizontal = 12.dp)) {
        Text(shortDate(b.startTs), style = figtreeStyle(13, Ui.Muted))
        Spacer(Modifier.weight(1f))
        Text(shortDate(if (b.endTs > 0) b.endTs else System.currentTimeMillis()), style = figtreeStyle(13, Ui.Muted))
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row {
        Text("Spent per day", style = figtreeStyle(16, Ui.Ink, FontWeight.Bold))
        Spacer(Modifier.weight(1f))
        if (perDay.isNotEmpty() && perDay[biggestDay] > 0) {
          Text(
            "Biggest: ${money(perDay[biggestDay])} on ${shortDate(b.startTs + biggestDay * 86_400_000L)}",
            style = figtreeStyle(13, Ui.Muted),
          )
        }
      }
      GlassWell(Modifier.fillMaxWidth().height(68.dp), corner = 16.dp) {
        BarsCurve(perDay, Ui.Accent.copy(alpha = 0.55f), Ui.Ink, Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 8.dp), gap = 3.dp, lineWidth = 1.5.dp)
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("${b.entries.size} ENTRIES · SORT BY", style = LabelStyle)
      Segmented(SortNames, sort, { sort = it })
    }

    Box(Modifier.weight(1f).fillMaxWidth().pit(18.dp).clip(RoundedCornerShape(18.dp))) {
      LazyColumn(Modifier.fillMaxSize()) {
        items(entries) { e -> EntryRow(e.amount, e.ts) }
      }
    }
  }
}

@Composable
private fun StatTile(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
  Column(
    modifier.height(66.dp).soft(1f, 18.dp, 4.dp).padding(horizontal = 12.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.Center,
  ) {
    Text(label, style = figtreeStyle(13, Ui.Muted, FontWeight.Bold))
    Text(value, style = balooStyle(24, color))
  }
}

/** Step line of the remaining budget across the days the budget ran. */
@Composable
private fun RemainingLine(b: Budget, modifier: Modifier) {
  val days = b.days()
  val start = day(b.startTs)
  Canvas(modifier.semantics { contentDescription = "Remaining budget from ${money(b.amount)} down to ${money(b.remaining)}" }) {
    val w = size.width
    val h = size.height
    fun xOf(ts: Long): Float {
      if (days <= 1) return 0f
      return ChronoUnit.DAYS.between(start, day(ts)).toFloat() / (days - 1) * w
    }
    fun yOf(rem: Int): Float = ((1f - rem.toFloat() / b.amount.coerceAtLeast(1)).coerceIn(0f, 1f)) * h
    var rem = b.amount
    var y = 0f
    val line = Path().apply { moveTo(0f, 0f) }
    for (e in b.entries.sortedBy { it.ts }) {
      val x = xOf(e.ts)
      line.lineTo(x, y)
      rem -= e.amount
      y = yOf(rem)
      line.lineTo(x, y)
    }
    line.lineTo(w, y)
    val area = Path().apply {
      addPath(line)
      lineTo(w, h)
      lineTo(0f, h)
      close()
    }
    drawPath(area, Ui.Accent.copy(alpha = 0.22f))
    drawPath(line, Ui.Accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
  }
}
