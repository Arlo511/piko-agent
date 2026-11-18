package dev.xiaoman.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.xiaoman.app.settings.SkinId

/**
 * Everything one skin needs. [colors], [typography] and [shapes] feed MaterialTheme; [tokens]
 * carries the parts Material3 cannot express (borders, hard shadows, bubbles, navigation, texture).
 */
data class Skin(
    val id: SkinId,
    val dark: Boolean,
    val colors: ColorScheme,
    val typography: Typography,
    val shapes: Shapes,
    val tokens: SkinTokens,
)

data class SkinTokens(
    val card: SurfaceStyle,
    val inset: SurfaceStyle,
    val notice: SurfaceStyle,
    val approval: SurfaceStyle,
    val bubble: BubbleStyle,
    val nav: NavStyle,
    val background: BackgroundStyle,
    val motion: MotionStyle,
    val avatarShape: Shape,
    val chipShape: Shape,
    val buttonShape: Shape,
    val fieldShape: Shape,
    val noteFont: FontFamily,
    val iconStyle: IconStyle = IconStyle.MATERIAL,
    val tiltDegrees: Float = 0f,
    /** Markdown table colours; null falls back to the skin's surface and outline colours. */
    val tableHeaderFill: Color? = null,
    val tableDividerColor: Color? = null,
    val tableDividerWidth: Dp? = null,
)

data class SurfaceStyle(
    val shape: Shape,
    val fill: Color,
    val border: BorderStroke? = null,
    val shadow: ShadowStyle? = null,
    /** Height of the solid "side" drawn under the surface; 0 for flat skins. */
    val extrusion: Dp = 0.dp,
)

data class ShadowStyle(
    val offsetX: Dp,
    val offsetY: Dp,
    val blur: Dp,
    val color: Color,
    /** Hard shadows are drawn as a solid offset outline instead of a blurred elevation. */
    val hard: Boolean,
)

data class BubbleStyle(
    val userFill: Color,
    val userText: Color,
    val userShape: Shape,
    val assistantFill: Color,
    val assistantText: Color,
    val assistantBorder: BorderStroke?,
    val assistantShape: Shape,
)

data class NavStyle(
    val container: Color,
    val topBorder: BorderStroke?,
    val indicator: Color,
    val indicatorShape: Shape,
    val selected: Color,
    val unselected: Color,
    val indicatorWidth: Dp,
    val indicatorHeight: Dp,
)

sealed interface BackgroundStyle {
    data object Plain : BackgroundStyle

    /** Checkerboard and/or line grid drawn behind every screen. */
    data class Grid(
        val step: Dp,
        val line: Dp,
        val color: Color,
        val checker: Color? = null,
    ) : BackgroundStyle

    data class Glow(val top: Color, val bottom: Color) : BackgroundStyle
}

data class MotionStyle(
    val durationMillis: Int,
    val pressScale: Float,
)

enum class IconStyle { MATERIAL, PIXEL }

val LocalSkin = staticCompositionLocalOf<Skin> { error("XiaomanTheme is missing") }
