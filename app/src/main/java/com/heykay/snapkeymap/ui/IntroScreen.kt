package com.heykay.snapkeymap.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

@Composable
fun IntroScreen(onContinue: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding()
                .verticalScroll(rememberScrollState()).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            IntroLogo()
            Text("SnapKey Map", style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() })
            Text("Give your Snap Key a new job.", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
            TextButton(onClick = onContinue) { Text("Skip") }
        }
    }
}

@Composable
private fun IntroLogo() {
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    var completed by rememberSaveable { mutableStateOf(false) }
    val initialValue = if (completed || !animate) 1f else 0f
    val background = remember { Animatable(initialValue) }
    val capsule = remember { Animatable(initialValue) }
    val dots = remember { List(3) { Animatable(initialValue) } }
    LaunchedEffect(animate) {
        if (animate && !completed) {
            coroutineScope {
                launch { background.animateTo(1f, tween(220)) }
                launch { capsule.animateTo(1f, tween(300, delayMillis = 160)) }
                dots.forEachIndexed { index, dot ->
                    launch {
                        dot.animateTo(1f, tween(240, delayMillis = 420 + index * 90,
                            easing = CubicBezierEasing(0.2f, 1.45f, 0.5f, 1f)))
                    }
                }
            }
        }
        completed = true
    }
    val branches = remember {
        listOf(
            Path().apply { moveTo(241f, 198f); cubicTo(286f, 198f, 274f, 136f, 341f, 136f) },
            Path().apply { moveTo(241f, 256f); lineTo(341f, 256f) },
            Path().apply { moveTo(241f, 314f); cubicTo(286f, 314f, 274f, 376f, 341f, 376f) },
        )
    }
    Canvas(Modifier.size(200.dp)) {
        scale(size.width / 512f, size.height / 512f, pivot = Offset.Zero) {
            scale(0.8f + 0.2f * background.value, pivot = Offset(256f, 256f)) {
                drawRoundRect(
                    brush = Brush.linearGradient(listOf(Color(0xFF0A7CFA), Color(0xFF0657EA)),
                        start = Offset.Zero, end = Offset(512f, 512f)),
                    size = Size(512f, 512f), cornerRadius = CornerRadius(112f), alpha = background.value,
                )
            }
            scale(0.72f, pivot = Offset(256f, 256f)) {
                clipRect(left = 0f, top = 0f, right = 512f, bottom = 88f + 336f * capsule.value) {
                    branches.forEachIndexed { index, path ->
                        drawPath(path, listOf(Color(0xFF6AD8FF), Color(0xFF68CCFF), Color(0xFF77DFFF))[index],
                            style = Stroke(24f, cap = StrokeCap.Round))
                    }
                    drawRoundRect(Color(0xFF072C86), Offset(89f, 88f), Size(164f, 336f), CornerRadius(82f))
                    drawRoundRect(Color(0xFFC7F2FF), Offset(105f, 104f), Size(132f, 304f), CornerRadius(66f))
                    drawRoundRect(
                        Brush.verticalGradient(listOf(Color(0xFF38BCFB), Color(0xFF1689F6)), 120f, 392f),
                        Offset(121f, 120f), Size(100f, 272f), CornerRadius(50f),
                    )
                }
                dots.forEachIndexed { index, dot ->
                    val center = Offset(366f, 136f + index * 120f)
                    scale(dot.value.coerceAtLeast(0.001f), pivot = center) {
                        drawCircle(listOf(Color(0xFF08338F), Color(0xFF1499F8), Color(0xFF61CEF9))[index],
                            radius = 50f, center = center, alpha = dot.value.coerceIn(0f, 1f))
                        drawCircle(Color.White, radius = 18f, center = center, alpha = dot.value.coerceIn(0f, 1f))
                    }
                }
            }
        }
    }
}
