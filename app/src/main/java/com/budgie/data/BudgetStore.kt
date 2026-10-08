package com.budgie.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.updateAll
import com.budgie.widget.BudgieWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

data class Entry(val amount: Int, val ts: Long)

/** endTs == 0 means the budget is the active one. */
data class Budget(val amount: Int, val startTs: Long, val endTs: Long, val entries: List<Entry>) {
  val spent: Int get() = entries.sumOf { it.amount }
  val remaining: Int get() = amount - spent
  val ratio: Float get() = if (amount <= 0) 0f else (remaining.toFloat() / amount).coerceIn(0f, 1f)
  val isLow: Boolean get() = remaining > 0 && ratio <= 0.2f

  fun days(now: Long = System.currentTimeMillis()): Int {
    val end = if (endTs > 0) endTs else now
    return (ChronoUnit.DAYS.between(day(startTs), day(end)).toInt() + 1).coerceAtLeast(1)
  }

  /** Total spent on each day the budget was active, oldest first. */
  fun perDay(now: Long = System.currentTimeMillis()): List<Int> {
    val n = days(now)
    val out = IntArray(n)
    val start = day(startTs)
    for (e in entries) {
      val i = ChronoUnit.DAYS.between(start, day(e.ts)).toInt().coerceIn(0, n - 1)
      out[i] += e.amount
    }
    return out.toList()
  }
}

fun day(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()

fun money(n: Int): String = (if (n < 0) "−$" else "$") + String.format(Locale.US, "%,d", abs(n))

/**
 * Single source of truth for the app and the widget. Values are Compose snapshot state, so any screen
 * or widget reading them redraws when they change. Everything is persisted to SharedPreferences.
 */
object BudgetStore {
  var current by mutableStateOf<Budget?>(null)
    private set
  var archive by mutableStateOf<List<Budget>>(emptyList())
    private set

  // Widget-only state (the widget's own number pad).
  var typed by mutableStateOf("")
    private set
  var undoable by mutableStateOf(false)
  var frame by mutableIntStateOf(0)
  var chip by mutableIntStateOf(0)

  private var loaded = false
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences("budgie", Context.MODE_PRIVATE)

  @Synchronized
  fun ensureLoaded(context: Context) {
    if (loaded) return
    val p = prefs(context)
    current = p.getString("current", null)?.let { runCatching { readBudget(JSONObject(it)) }.getOrNull() }
    archive = p.getString("archive", null)?.let { s ->
      runCatching {
        val arr = JSONArray(s)
        List(arr.length()) { readBudget(arr.getJSONObject(it)) }
      }.getOrNull()
    } ?: emptyList()
    typed = p.getString("typed", "") ?: ""
    loaded = true
  }

  /** Archives the active budget (if any) and starts a fresh one. Leftovers do not carry over. */
  fun startBudget(context: Context, amount: Int) {
    ensureLoaded(context)
    val now = System.currentTimeMillis()
    val old = current
    if (old != null) archive = listOf(old.copy(endTs = now)) + archive
    current = Budget(amount, now, 0L, emptyList())
    typed = ""
    undoable = false
    save(context)
  }

  fun addEntry(context: Context, amount: Int) {
    ensureLoaded(context)
    val b = current ?: return
    current = b.copy(entries = b.entries + Entry(amount, System.currentTimeMillis()))
    save(context)
  }

  fun undoLast(context: Context) {
    ensureLoaded(context)
    val b = current ?: return
    if (b.entries.isEmpty()) return
    current = b.copy(entries = b.entries.dropLast(1))
    save(context)
  }

  fun setTyped(context: Context, value: String) {
    typed = value
    prefs(context).edit().putString("typed", value).apply()
  }

  private fun save(context: Context) {
    val arr = JSONArray()
    archive.forEach { arr.put(writeBudget(it)) }
    prefs(context).edit()
      .putString("current", current?.let { writeBudget(it).toString() })
      .putString("archive", arr.toString())
      .putString("typed", typed)
      .apply()
    refreshWidget(context)
  }

  fun refreshWidget(context: Context) {
    val app = context.applicationContext
    scope.launch { runCatching { BudgieWidget().updateAll(app) } }
  }

  private fun writeBudget(b: Budget): JSONObject {
    val entries = JSONArray()
    b.entries.forEach { e -> entries.put(JSONObject().put("a", e.amount).put("t", e.ts)) }
    return JSONObject()
      .put("amount", b.amount)
      .put("start", b.startTs)
      .put("end", b.endTs)
      .put("entries", entries)
  }

  private fun readBudget(o: JSONObject): Budget {
    val arr = o.optJSONArray("entries") ?: JSONArray()
    val entries = List(arr.length()) { i ->
      val e = arr.getJSONObject(i)
      Entry(e.getInt("a"), e.getLong("t"))
    }
    return Budget(o.getInt("amount"), o.getLong("start"), o.optLong("end", 0L), entries)
  }
}
