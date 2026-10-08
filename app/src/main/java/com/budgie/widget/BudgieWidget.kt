package com.budgie.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import com.budgie.MainActivity
import com.budgie.data.BudgetStore
import com.budgie.data.money
import kotlinx.coroutines.delay

class BudgieWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = BudgieWidget()
}

class BudgieWidget : GlanceAppWidget() {
  override val sizeMode: SizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    BudgetStore.ensureLoaded(context)
    provideContent { WidgetContent() }
  }
}

val KeyParam = ActionParameters.Key<String>("key")

private val KeyRows = listOf(
  listOf("1", "2", "3"),
  listOf("4", "5", "6"),
  listOf("7", "8", "9"),
  listOf("del", "0", "ok"),
)

private fun keyAction(k: String) = actionRunCallback<KeyAction>(actionParametersOf(KeyParam to k))

private fun keyLabel(k: String) = when (k) {
  "del" -> "Delete last digit"
  "ok" -> "Subtract from budget"
  else -> k
}

/**
 * The whole look is one bitmap drawn by WidgetRenderer (so it can use the app's fonts, soft shadows and
 * the graph). On top sit invisible tap targets laid out on the same grid the renderer uses.
 */
@Composable
private fun WidgetContent() {
  val context = LocalContext.current
  val size = LocalSize.current
  val budget = BudgetStore.current
  val bitmap = WidgetRenderer.render(
    context,
    size.width.value,
    size.height.value,
    WidgetRenderer.State(budget, BudgetStore.typed, BudgetStore.undoable, BudgetStore.frame, BudgetStore.chip),
  )
  val description = if (budget == null) "No budget yet" else "Remaining budget ${money(budget.remaining)}"

  Box(GlanceModifier.fillMaxSize()) {
    Image(
      provider = ImageProvider(bitmap),
      contentDescription = description,
      modifier = GlanceModifier.fillMaxSize(),
      contentScale = ContentScale.FillBounds,
    )
    if (budget == null) {
      Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>())) {}
    } else {
      Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
        Spacer(GlanceModifier.fillMaxWidth().height(WidgetRenderer.HEADER_BLOCK.dp))
        KeyRows.forEach { row ->
          Row(GlanceModifier.fillMaxWidth().defaultWeight().padding(bottom = WidgetRenderer.GAP.dp)) {
            row.forEachIndexed { i, k ->
              if (i > 0) Spacer(GlanceModifier.width(WidgetRenderer.GAP.dp))
              Box(
                GlanceModifier
                  .defaultWeight()
                  .fillMaxHeight()
                  .clickable(keyAction(k))
                  .semantics { contentDescription = keyLabel(k) },
              ) {}
            }
          }
        }
        Spacer(GlanceModifier.height(2.dp))
        Row(GlanceModifier.fillMaxWidth().height(WidgetRenderer.BOTTOM.dp)) {
          Box(GlanceModifier.defaultWeight().fillMaxHeight().clickable(keyAction("undo"))) {}
          Spacer(GlanceModifier.width(10.dp))
          Box(
            GlanceModifier
              .width(WidgetRenderer.OPEN_W.dp)
              .fillMaxHeight()
              .clickable(actionStartActivity<MainActivity>())
              .semantics { contentDescription = "Open Budgie" },
          ) {}
        }
      }
    }
  }
}

class KeyAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    BudgetStore.ensureLoaded(context)
    val k = parameters[KeyParam] ?: return
    val typed = BudgetStore.typed
    var celebrate = false
    when (k) {
      "del" -> BudgetStore.setTyped(context, typed.dropLast(1))
      "ok" -> {
        val n = typed.toIntOrNull() ?: 0
        if (n > 0 && BudgetStore.current != null) {
          BudgetStore.addEntry(context, n)
          BudgetStore.setTyped(context, "")
          BudgetStore.undoable = true
          BudgetStore.chip = n
          celebrate = true
        }
      }
      "undo" -> {
        if (BudgetStore.undoable && typed.isEmpty()) {
          BudgetStore.undoLast(context)
          BudgetStore.undoable = false
        }
      }
      else -> {
        val t = (typed + k).trimStart('0')
        if (t.length <= 5) {
          BudgetStore.setTyped(context, t)
          BudgetStore.undoable = false
        }
      }
    }
    val widget = BudgieWidget()
    widget.updateAll(context)
    if (celebrate) {
      // A short flap: swap the budgie's frames a few times, then settle.
      for (f in intArrayOf(1, 2, 3, 4, 3, 2)) {
        delay(140)
        BudgetStore.frame = f
        widget.updateAll(context)
      }
      delay(140)
      BudgetStore.frame = 0
      BudgetStore.chip = 0
      widget.updateAll(context)
    }
  }
}
