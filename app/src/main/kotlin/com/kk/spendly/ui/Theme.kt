package com.kk.spendly.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kk.spendly.R
import com.kk.spendly.data.CustomCategory
import com.kk.spendly.parser.Categorizer

object Palette {
    val Ink = Color(0xFF1B1D3A)
    val TextMuted = Color(0xFF7C809C)
    val Accent = Color(0xFF6C5CE7)
    val AccentDark = Color(0xFF4F46E5)
    val AccentLight = Color(0xFF8B7CF6)
    val Lavender = Color(0xFFC9C4FA)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceHigh = Color(0xFFE4E6F8)
    val Positive = Color(0xFF16A34A)
    val Warning = Color(0xFFF59E0B)
    val Danger = Color(0xFFEF4444)
    val BackgroundTop = Color(0xFFE8EDFF)
    val BackgroundMid = Color(0xFFF3F0FF)
    val BackgroundBottom = Color(0xFFFAF9FF)
}

/** Poppins is the one typeface for the whole app; hierarchy comes from size and weight, not from mixing fonts. */
private val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

private fun type(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = Poppins, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = 0.sp)

// Sizes, weights and line heights follow the design spec: SemiBold headings, Medium buttons and small labels, Regular body.
private fun appTypography() = Typography(
    displayLarge = type(48, 56, FontWeight.SemiBold),
    displayMedium = type(40, 48, FontWeight.SemiBold),
    displaySmall = type(36, 44, FontWeight.SemiBold),     // hero heading
    headlineLarge = type(32, 40, FontWeight.SemiBold),
    headlineMedium = type(32, 40, FontWeight.SemiBold),   // screen title
    headlineSmall = type(24, 32, FontWeight.SemiBold),    // section heading
    titleLarge = type(24, 32, FontWeight.SemiBold),
    titleMedium = type(20, 28, FontWeight.Medium),        // card title
    titleSmall = type(16, 24, FontWeight.Medium),
    bodyLarge = type(16, 24, FontWeight.Normal),          // body
    bodyMedium = type(14, 20, FontWeight.Normal),         // secondary text
    bodySmall = type(12, 16, FontWeight.Normal),          // small metadata
    labelLarge = type(15, 20, FontWeight.Medium),         // button text
    labelMedium = type(12, 16, FontWeight.Medium),
    labelSmall = type(12, 16, FontWeight.Medium),         // navigation labels
)

private val GlassLight = lightColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.White,
    background = Palette.BackgroundMid,
    onBackground = Palette.Ink,
    surface = Palette.Surface,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextMuted,
    error = Palette.Danger,
)

// A soft indigo wash instead of the default gray press highlight, which looks muddy on frosted glass.
@OptIn(ExperimentalMaterial3Api::class)
private val GlassRipple = RippleConfiguration(
    color = Palette.Accent,
    rippleAlpha = RippleAlpha(draggedAlpha = 0.12f, focusedAlpha = 0.12f, hoveredAlpha = 0.08f, pressedAlpha = 0.14f),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FintechTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = GlassLight, typography = appTypography()) {
        CompositionLocalProvider(
            LocalContentColor provides Palette.Ink,
            LocalRippleConfiguration provides GlassRipple,
            content = content,
        )
    }

/** Soft lavender gradient with blurred color blobs; the frosted cards sit on top of it. */
@Composable
fun GlassBackground(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Palette.BackgroundTop, Palette.BackgroundMid, Palette.BackgroundBottom))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            fun blob(color: Color, center: Offset, radius: Float) =
                drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, radius), radius, center)
            blob(Color(0x668B7CF6), Offset(size.width * 0.95f, size.height * 0.08f), size.width * 0.75f)
            blob(Color(0x5560A5FA), Offset(size.width * 0.0f, size.height * 0.42f), size.width * 0.7f)
            blob(Color(0x44C084FC), Offset(size.width * 0.85f, size.height * 0.85f), size.width * 0.8f)
        }
        content()
    }
}

// Built once and shared: a new brush and two new colors per card per redraw is needless garbage in a scrolling list.
private val GlassEdge = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.25f)))
private val GlassAmbientShadow = Palette.Accent.copy(alpha = 0.10f)
private val GlassSpotShadow = Palette.Accent.copy(alpha = 0.22f)

/**
 * Frosted-glass surface: translucent white, a bright edge highlight and a soft indigo shadow.
 *
 * [elevation] is the shadow depth. Every shadow is blurred by the GPU on every frame of a scroll or tab switch, so the few big
 * cards keep a soft one while rows in long lists pass a small value, or 0 for none.
 */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = Color.White.copy(alpha = 0.62f),
    elevation: Dp = 10.dp,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    // shadow() already clips to the shape, so a second clip would only add another layer.
    val surface = if (elevation > 0.dp) {
        modifier.shadow(elevation, shape, ambientColor = GlassAmbientShadow, spotColor = GlassSpotShadow)
    } else {
        modifier.clip(shape)
    }
    Box(
        surface
            .background(tint)
            .border(1.dp, GlassEdge, shape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier),
    ) { content() }
}

/** One category's name, color and icon as shown in charts, rows and avatars. */
data class CategoryInfo(val name: String, val color: Color, val icon: ImageVector, val custom: Boolean = false)

/** Icons and colors a user can pick when creating a category. */
object CategoryChoices {
    val icons: Map<String, ImageVector> = linkedMapOf(
        "label" to Icons.Rounded.Label, "pets" to Icons.Rounded.Pets, "school" to Icons.Rounded.School,
        "flight" to Icons.Rounded.Flight, "fitness" to Icons.Rounded.FitnessCenter, "gift" to Icons.Rounded.CardGiftcard,
        "work" to Icons.Rounded.Work, "favorite" to Icons.Rounded.Favorite,
    )
    val colors = listOf(
        0xFF6C5CE7, 0xFFFF8A65, 0xFF3B9EFF, 0xFFD946EF, 0xFFF5B301, 0xFFF43F8E, 0xFF22C55E, 0xFF14B8A6,
    )

    fun icon(key: String) = icons[key] ?: Icons.Rounded.Label
}

class Catalog(val all: List<CategoryInfo>) {
    private val byName = all.associateBy { it.name }
    val names get() = all.map { it.name }

    fun of(name: String): CategoryInfo = byName[name] ?: byName.getValue(Categorizer.OTHER)

    companion object {
        private val defaults = listOf(
            CategoryInfo("Food", Color(0xFFFF8A65), Icons.Rounded.Restaurant),
            CategoryInfo("Groceries", Color(0xFF22C55E), Icons.Rounded.ShoppingCart),
            CategoryInfo("Transport", Color(0xFF3B9EFF), Icons.Rounded.DirectionsCar),
            CategoryInfo("Shopping", Color(0xFFD946EF), Icons.Rounded.ShoppingBag),
            CategoryInfo("Bills", Color(0xFFF5B301), Icons.Rounded.Bolt),
            CategoryInfo("Entertainment", Color(0xFFF43F8E), Icons.Rounded.Movie),
            CategoryInfo("Medical", Color(0xFF14B8A6), Icons.Rounded.MedicalServices),
            CategoryInfo("Transfers", Color(0xFF8B7CF6), Icons.Rounded.SwapHoriz),
        )
        private val tail = listOf(
            CategoryInfo(Categorizer.OTHER, Color(0xFF94A3B8), Icons.Rounded.Category),
            CategoryInfo(Categorizer.INCOME, Color(0xFF16A34A), Icons.Rounded.Savings),
        )

        fun build(custom: List<CustomCategory>) = Catalog(
            defaults + custom.map { CategoryInfo(it.name, Color(it.colorArgb), CategoryChoices.icon(it.iconKey), custom = true) } + tail,
        )
    }
}

val LocalCatalog = staticCompositionLocalOf { Catalog.build(emptyList()) }
