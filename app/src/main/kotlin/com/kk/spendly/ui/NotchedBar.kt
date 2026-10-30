package com.kk.spendly.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * A rounded bar with a round bite out of the middle of its top edge, so a button can float detached above it with a clear gap.
 *
 * @param cornerRadius radius of the bar's four outer corners.
 * @param notchRadius radius of the bite: the button's radius plus the gap to leave around it.
 * @param notchCenterY how far below the bar's top edge the bite's centre sits (the button's centre).
 * @param fillet radius of the soft shoulders where the flat top edge curves down into the bite.
 */
class NotchedBarShape(
    private val cornerRadius: Dp,
    private val notchRadius: Dp,
    private val notchCenterY: Dp,
    private val fillet: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val (r, big, d, f) = with(density) {
            listOf(cornerRadius.toPx().coerceAtMost(h / 2), notchRadius.toPx(), notchCenterY.toPx(), fillet.toPx())
        }
        val cx = w / 2

        // Each shoulder is a small circle of radius f that touches the flat top edge and sits against the outside of the bite.
        // Its centre is f below the top edge and xf to the side of the bite's centre, found from "distance between centres = big + f".
        val xf = sqrt((big + f) * (big + f) - (f - d) * (f - d))
        val leftX = cx - xf
        val rightX = cx + xf
        fun deg(radians: Double) = Math.toDegrees(radians).toFloat()

        // Angles are in degrees, clockwise from the positive x axis (y points down), as Path.arcTo expects.
        val leftSweep = deg(atan2((d - f).toDouble(), xf.toDouble())) + 90f          // shoulder: from its top point round to the bite
        val biteStart = deg(atan2((f - d).toDouble(), (-xf).toDouble()))             // bite: from the left tangent point...
        val biteEnd = deg(atan2((f - d).toDouble(), xf.toDouble()))                  // ...down round the bottom to the right one
        val rightStart = deg(atan2((d - f).toDouble(), (-xf).toDouble())).let { if (it < 0) it + 360f else it }

        val path = Path().apply {
            moveTo(0f, r)
            arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)                       // top-left corner
            lineTo(leftX, 0f)
            arcTo(Rect(leftX - f, 0f, leftX + f, 2 * f), 270f, leftSweep, false)       // left shoulder
            arcTo(Rect(cx - big, d - big, cx + big, d + big), biteStart, biteEnd - biteStart, false) // the bite
            arcTo(Rect(rightX - f, 0f, rightX + f, 2 * f), rightStart, 270f - rightStart, false)     // right shoulder
            lineTo(w - r, 0f)
            arcTo(Rect(w - 2 * r, 0f, w, 2 * r), 270f, 90f, false)                    // top-right corner
            lineTo(w, h - r)
            arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)                   // bottom-right corner
            lineTo(r, h)
            arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)                     // bottom-left corner
            close()
        }
        return Outline.Generic(path)
    }
}
