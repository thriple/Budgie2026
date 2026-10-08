package com.budgie.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.budgie.data.Budget
import com.budgie.data.money
import kotlinx.coroutines.launch

@Composable
fun NewBudgetScreen(current: Budget?, onBack: (() -> Unit)?, onStart: (Int) -> Unit) {
  var value by rememberSaveable { mutableStateOf("") }
  var done by rememberSaveable { mutableStateOf(false) }
  val amount = value.toIntOrNull() ?: 0
  val ready = amount > 0
  val shake = remember { Animatable(0f) }
  val scope = rememberCoroutineScope()
  val focus = remember { FocusRequester() }
  val keyboard = LocalSoftwareKeyboardController.current
  val frost by animateFloatAsState(if (ready) 0.25f else 0.55f, tween(400), label = "frost")
  val ctaColor by animateColorAsState(if (ready) Ui.Accent else Ui.Muted, tween(300), label = "cta")
  val dotColor by animateColorAsState(if (ready) Ui.Accent else Color(0xFF5B5F6A), tween(300), label = "dot")

  LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
  BackHandler(enabled = done) { done = false }
  if (onBack != null) BackHandler(enabled = !done) { onBack() }

  fun start() {
    if (ready) {
      keyboard?.hide()
      done = true
    } else {
      scope.launch {
        for (dx in listOf(-8f, 8f, -5f, 5f, 0f)) shake.animateTo(dx, tween(70))
      }
    }
  }

  Box(Modifier.fillMaxSize().background(Ui.Surface)) {
    Column(
      Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (onBack != null) BackButton(onBack)
        Text("New budget", style = balooStyle(28))
      }

      Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Bird(104.dp)
        Box(Modifier.weight(1f).padding(bottom = 26.dp).soft(1f, 20.dp, 5.dp).padding(horizontal = 16.dp, vertical = 14.dp)) {
          Text("How much seed are we working with this time?", style = figtreeStyle(17))
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("BUDGET AMOUNT", style = LabelStyle)
        GlassWell(
          Modifier.fillMaxWidth().height(96.dp).graphicsLayer { translationX = shake.value * density },
          corner = 24.dp,
          frost = frost,
        ) {
          Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$", style = balooStyle(44, Ui.Soft))
            val big = balooStyle(54)
            BasicTextField(
              value = value,
              onValueChange = { v -> value = v.filter { it.isDigit() }.trimStart('0').take(6) },
              modifier = Modifier.weight(1f).focusRequester(focus),
              textStyle = big.copy(color = Color.Transparent),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
              keyboardActions = KeyboardActions(onDone = { start() }),
              cursorBrush = SolidColor(Ui.Ink),
              decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                  if (value.isEmpty()) Text("0", style = big.copy(color = Ui.Ink.copy(alpha = 0.3f))) else SpringDigits(value, big)
                  inner()
                }
              },
            )
          }
        }
        Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Box(Modifier.size(8.dp).drawBehind { drawCircle(dotColor) })
          if (ready) {
            Text("Ready to start fresh at ${money(amount)}", style = figtreeStyle(15))
          } else {
            Text("Whole dollars. Tap the field and type.", style = figtreeStyle(15, Ui.Muted))
          }
        }
      }

      SoftButton({ start() }, Modifier.fillMaxWidth().height(60.dp), corner = 20.dp) {
        Text("Start new budget", style = balooStyle(22, ctaColor))
      }

      if (current != null) {
        Column(Modifier.fillMaxWidth().soft(1f, 22.dp, 5.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Your current budget will be archived", style = figtreeStyle(17, Ui.Ink, FontWeight.Bold))
          Row(Modifier.fillMaxWidth().pit(14.dp).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text(money(current.amount), style = balooStyle(22))
              Text("${shortDate(current.startTs)} – today · ${current.days()} days", style = figtreeStyle(14, Ui.Muted))
            }
            Column(horizontalAlignment = Alignment.End) {
              Text(money(current.remaining), style = balooStyle(22, if (current.remaining >= 0) Ui.Good else Ui.Bad))
              Text(if (current.remaining >= 0) "left over" else "over", style = figtreeStyle(14, Ui.Muted))
            }
          }
          Text(
            "What's left over is recorded in the Archive but does not carry over. Every budget starts fresh.",
            style = figtreeStyle(15, Ui.Muted, FontWeight.Medium),
          )
        }
      }
    }

    AnimatedVisibility(visible = done, enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.92f), exit = fadeOut(tween(200))) {
      Confirmation(amount, current, onGo = { onStart(amount) }, onChange = { done = false })
    }
  }
}

@Composable
private fun Confirmation(amount: Int, previous: Budget?, onGo: () -> Unit, onChange: () -> Unit) {
  val stamp = remember { Animatable(0.4f) }
  LaunchedEffect(Unit) { stamp.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f)) }
  Column(
    Modifier.fillMaxSize().background(Ui.Surface).systemBarsPadding().padding(20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Spacer(Modifier.height(40.dp))
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
      GlassWell(Modifier.size(220.dp), corner = 110.dp, frost = 0.25f)
      Bird(180.dp, loop = true)
    }
    Text("NEW BUDGET SET", style = LabelStyle)
    Text(
      money(amount),
      Modifier.graphicsLayer { scaleX = stamp.value; scaleY = stamp.value; alpha = ((stamp.value - 0.4f) / 0.6f).coerceIn(0f, 1f) },
      style = balooStyle(76),
    )
    val note = if (previous != null) {
      "Your ${money(previous.amount)} budget is in the Archive with ${money(previous.remaining)} " +
        (if (previous.remaining >= 0) "left over" else "over") + ". This one starts fresh."
    } else {
      "This one starts fresh."
    }
    Text(note, style = figtreeStyle(16, Ui.Muted, FontWeight.Medium).copy(textAlign = TextAlign.Center), modifier = Modifier.padding(horizontal = 24.dp))
    Spacer(Modifier.weight(1f))
    SoftButton(onGo, Modifier.fillMaxWidth().height(60.dp), corner = 20.dp) {
      Text("Go to budget", style = balooStyle(22, Ui.Accent))
    }
    Text(
      "Change amount",
      Modifier.clickable(onClick = onChange).padding(12.dp),
      style = figtreeStyle(15, Ui.Accent, FontWeight.Bold).copy(textDecoration = TextDecoration.Underline),
    )
  }
}
