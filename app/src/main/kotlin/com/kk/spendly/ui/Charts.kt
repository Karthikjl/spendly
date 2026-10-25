package com.kk.spendly.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class Slice(val label: String, val value: Long, val color: Color)

@Composable
fun Donut(slices: List<Slice>, modifier: Modifier = Modifier, center: @Composable () -> Unit) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1L)
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val description = slices.joinToString { "${it.label} ${it.value * 100 / total} percent" }
    Box(modifier.size(150.dp).semantics { contentDescription = "Spending by category: $description" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(150.dp)) {
            val stroke = 20.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Palette.SurfaceHigh, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            var start = -90f
            slices.forEach {
                val sweep = 360f * it.value / total
                // Small gap between segments keeps neighbouring colors distinct.
                val gap = if (slices.size > 1) 4f else 0f
                drawArc(
                    it.color, start + gap / 2, ((sweep - gap) * reveal.value).coerceAtLeast(0.01f), false,
                    Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        center()
    }
}

@Composable
fun BarChart(bars: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    val max = (bars.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    Row(modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        bars.forEachIndexed { i, (label, value) ->
            val last = i == bars.lastIndex
            val height by animateFloatAsState((84 * value / max.toFloat()).coerceAtLeast(4f), tween(700, easing = FastOutSlowInEasing), label = "bar")
            Column(
                Modifier.semantics { contentDescription = "$label ${rupees(value)}" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier.width(28.dp).height(height.dp)
                        .background(if (last) Palette.Accent else Palette.Lavender, RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.size(6.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = if (last) Palette.Accent else Palette.TextMuted)
            }
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, track: Color = Palette.SurfaceHigh) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "progress")
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
    }
}
