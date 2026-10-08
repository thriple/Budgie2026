package com.budgie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.budgie.data.BudgetStore
import com.budgie.ui.ArchiveDetailScreen
import com.budgie.ui.ArchiveScreen
import com.budgie.ui.HomeScreen
import com.budgie.ui.NewBudgetScreen

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )
    BudgetStore.ensureLoaded(this)
    setContent { App() }
  }
}

@Composable
private fun App() {
  val context = LocalContext.current
  var screen by rememberSaveable { mutableStateOf("home") }
  var detailStart by rememberSaveable { mutableLongStateOf(0L) }
  val current = BudgetStore.current

  BackHandler(enabled = screen == "archive" || screen == "detail") {
    screen = if (screen == "detail") "archive" else "home"
  }

  if (current == null || screen == "new") {
    NewBudgetScreen(
      current = current,
      onBack = if (current != null) ({ screen = "home" }) else null,
      onStart = { amount ->
        BudgetStore.startBudget(context, amount)
        screen = "home"
      },
    )
  } else {
    when (screen) {
      "archive" -> ArchiveScreen(
        archive = BudgetStore.archive,
        onBack = { screen = "home" },
        onOpen = { b ->
          detailStart = b.startTs
          screen = "detail"
        },
      )
      "detail" -> {
        val b = BudgetStore.archive.firstOrNull { it.startTs == detailStart }
        if (b != null) {
          ArchiveDetailScreen(b, onBack = { screen = "archive" })
        } else {
          LaunchedEffect(Unit) { screen = "archive" }
        }
      }
      else -> HomeScreen(current, onArchive = { screen = "archive" }, onNew = { screen = "new" })
    }
  }
}
