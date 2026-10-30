package com.kk.spendly.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.animation.Crossfade
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

/**
 * Seven days of spending as bars. Tap a day to select it; the strip's detail panel below shows what was spent and received.
 * A green dot under a day's letter marks money received that day. The reveal animation is read only while drawing, so it
 * does not re-run layout on every frame.
 */
@Composable
internal fun WeekChart(days: List<DayBar>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val max = (days.maxOfOrNull { it.spent } ?: 0L).coerceAtLeast(1L)
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(days) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    Row(modifier.fillMaxWidth().height(132.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        days.forEachIndexed { i, day ->
            val on = i == selectedIndex
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp))
                    .background(if (on) Palette.Accent.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable(onClickLabel = "Show ${Dates.label(day.date)}") { onSelect(i) }
                    .semantics {
                        this.selected = on
                        contentDescription = "${Dates.label(day.date)}: spent ${rupees(day.spent)}, received ${rupees(day.received)}"
                    }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.width(22.dp).fillMaxHeight((day.spent.toFloat() / max).coerceAtLeast(0.04f))
                            .graphicsLayer { scaleY = reveal.value; transformOrigin = TransformOrigin(0.5f, 1f) }
                            .background(if (on) Palette.Accent else Palette.Lavender, RoundedCornerShape(8.dp)),
                    )
                }
                Spacer(Modifier.size(4.dp))
                Text(
                    day.letter, style = MaterialTheme.typography.labelSmall,
                    color = if (on) Palette.Accent else Palette.TextMuted, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                )
                Box(Modifier.padding(top = 3.dp).size(5.dp).background(if (day.received > 0) Palette.Positive else Color.Transparent, CircleShape))
            }
        }
    }
}

/** What happened on the selected day: spent and received side by side. */
@Composable
internal fun DayPanel(day: DayBar) {
    Crossfade(targetState = day, animationSpec = tween(150), label = "dayPanel") { d ->
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Dates.label(d.date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    when (d.count) { 0 -> "No transactions"; 1 -> "1 transaction"; else -> "${d.count} transactions" },
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DayFigure("Spent", d.spent, "-", Palette.Accent, Modifier.weight(1f))
                DayFigure("Received", d.received, "+", Palette.Positive, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DayFigure(label: String, paise: Long, sign: String, tint: Color, modifier: Modifier) {
    Column(modifier.background(tint.copy(alpha = 0.10f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
        Text(
            if (paise == 0L) rupees(0) else sign + rupees(paise),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            color = if (paise == 0L) Palette.TextMuted else tint,
        )
    }
}

/** A thin progress meter. The animated fill is read at draw time, so each frame only repaints it instead of re-laying out. */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, track: Color = Palette.SurfaceHigh) {
    val animated = animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "progress")
    Box(
        modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(track).drawBehind {
            drawRoundRect(color, size = Size(size.width * animated.value, size.height), cornerRadius = CornerRadius(size.height / 2))
        },
    )
}
