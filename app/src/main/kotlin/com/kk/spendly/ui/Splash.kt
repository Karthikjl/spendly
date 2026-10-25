package com.kk.spendly.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kk.spendly.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Branded intro shown once per cold start: the logo springs in, a ring pulses out, then everything fades into the app. */
@Composable
fun IntroOverlay(onFinished: () -> Unit) {
    val logo = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch { logo.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
        launch { ring.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
        delay(260)
        launch { text.animateTo(1f, tween(480, easing = FastOutSlowInEasing)) }
        delay(950)
        exit.animateTo(0f, tween(380))
        onFinished()
    }

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = exit.value }
            .background(Brush.linearGradient(listOf(Palette.AccentLight, Palette.AccentDark)))
            // Swallow touches so the app underneath can't be tapped mid-intro.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = 0.09f), size.width * 0.85f, Offset(size.width * 1.0f, size.height * 0.05f))
            drawCircle(Color.White.copy(alpha = 0.06f), size.width * 0.7f, Offset(0f, size.height * 0.95f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val p = ring.value
                    drawCircle(
                        Color.White.copy(alpha = 0.35f * (1f - p)),
                        radius = size.minDimension / 2 * (0.62f + 0.38f * p),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                Box(
                    Modifier.size(132.dp)
                        .graphicsLayer { scaleX = logo.value; scaleY = logo.value; alpha = logo.value.coerceIn(0f, 1f) }
                        .background(Color.White.copy(alpha = 0.18f), CircleShape),
                )
                Image(
                    painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(150.dp)
                        .graphicsLayer { scaleX = logo.value; scaleY = logo.value; alpha = logo.value.coerceIn(0f, 1f) },
                )
            }
            Spacer(Modifier.size(18.dp))
            Text(
                "Spendly",
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.graphicsLayer { alpha = text.value; translationY = (1f - text.value) * 24.dp.toPx() },
            )
            Text(
                "Know where your money goes",
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.graphicsLayer { alpha = text.value; translationY = (1f - text.value) * 32.dp.toPx() },
            )
        }
    }
}
