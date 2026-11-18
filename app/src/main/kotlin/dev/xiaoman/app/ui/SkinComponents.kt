package dev.xiaoman.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.min

/** Paints the current skin's flat, gradient or grid background behind all screens. */
@Composable
fun SkinBackground(modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    when (val background = skin.tokens.background) {
        BackgroundStyle.Plain -> Box(modifier.background(skin.colors.background))

        is BackgroundStyle.Glow -> Box(
            modifier.background(Brush.verticalGradient(listOf(background.top, background.bottom))),
        )

        is BackgroundStyle.Grid -> Canvas(modifier) {
            drawRect(background.color)
            val step = background.step.toPx()
            if (step <= 0f) return@Canvas
            val cols = ceil(size.width / step).toInt()
            val rows = ceil(size.height / step).toInt()
            background.checker?.let { checker ->
                for (row in 0 until rows) {
                    for (col in 0 until cols) {
                        if ((row + col) % 2 == 0) {
                            drawRect(
                                color = checker,
                                topLeft = Offset(col * step, row * step),
                                size = Size(step, step),
                            )
                        }
                    }
                }
            }
            if (background.line > 0.dp) {
                val line = background.line.toPx().coerceAtLeast(1f)
                for (col in 0..cols) {
                    drawRect(
                        color = skin.colors.outlineVariant.copy(alpha = 0.35f),
                        topLeft = Offset(col * step, 0f),
                        size = Size(line, size.height),
                    )
                }
                for (row in 0..rows) {
                    drawRect(
                        color = skin.colors.outlineVariant.copy(alpha = 0.35f),
                        topLeft = Offset(0f, row * step),
                        size = Size(size.width, line),
                    )
                }
            }
        }
    }
}

/**
 * A surface with the skin's radius, border and shadow. Hard shadows are drawn as solid offset
 * outlines so pixel skins stay crisp; soft shadows use Compose elevation.
 */
@Composable
fun SkinCard(
    modifier: Modifier = Modifier,
    style: SurfaceStyle = LocalSkin.current.tokens.card,
    fill: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val skin = LocalSkin.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) skin.tokens.motion.pressScale else 1f,
        animationSpec = tween(skin.tokens.motion.durationMillis),
        label = "skin-press",
    )
    val clickable = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClick = onClick,
        )
    } else Modifier
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin.Center
            }
            .then(clickable)
            .skinShadow(style),
    ) {
        Surface(
            shape = style.shape,
            color = fill ?: style.fill,
            border = style.border,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            content = content,
        )
    }
}

private fun Modifier.skinShadow(style: SurfaceStyle): Modifier {
    val spec = style.shadow ?: return this
    if (!spec.hard) {
        return shadow(
            elevation = spec.blur,
            shape = style.shape,
            clip = false,
            ambientColor = spec.color,
            spotColor = spec.color,
        )
    }
    return drawBehind {
        val outline = style.shape.createOutline(size, layoutDirection, this)
        val x = spec.offsetX.toPx()
        val y = spec.offsetY.toPx()
        val extrusion = style.extrusion.toPx()
        if (extrusion > 0f) {
            translate(x, y) { drawOutline(outline, spec.color) }
            translate(x, (y - extrusion / 2f).coerceAtLeast(0f)) {
                drawOutline(outline, spec.color.copy(alpha = 0.55f))
            }
        } else {
            translate(x, y) { drawOutline(outline, spec.color) }
        }
    }
}

/** The user/assistant chat bubble. Content color comes from the skin instead of the Material role. */
@Composable
fun SkinBubble(user: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val bubble = LocalSkin.current.tokens.bubble
    Surface(
        modifier = modifier,
        shape = if (user) bubble.userShape else bubble.assistantShape,
        color = if (user) bubble.userFill else bubble.assistantFill,
        contentColor = if (user) bubble.userText else bubble.assistantText,
        border = if (user) null else bubble.assistantBorder,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = content,
    )
}

/** Bottom navigation with skin-specific indicator, border and icon treatment. */
@Composable
fun SkinNavigationBar(tabs: List<Tab>, selected: Tab, onSelect: (Tab) -> Unit) {
    val skin = LocalSkin.current
    val nav = skin.tokens.nav
    Surface(color = nav.container, contentColor = skin.colors.onSurface) {
        Column {
            nav.topBorder?.let { border ->
                Box(border.width.let { Modifier.fillMaxWidth().height(it) }.background(border.brush))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(64.dp),
            ) {
                tabs.forEach { tab ->
                    val active = tab == selected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(selected = active, role = Role.Tab) { onSelect(tab) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (nav.indicatorWidth > 0.dp) {
                            Box(
                                modifier = Modifier
                                    .size(nav.indicatorWidth, nav.indicatorHeight)
                                    .background(
                                        if (active) nav.indicator else Color.Transparent,
                                        nav.indicatorShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                SkinNavIcon(tab, if (active) nav.selected else nav.unselected, Modifier.size(24.dp))
                            }
                        } else {
                            SkinNavIcon(tab, if (active) nav.selected else nav.unselected, Modifier.size(24.dp))
                        }
                        Text(
                            text = stringResource(tab.label),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) nav.selected else nav.unselected,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** Material icons for most skins; 24×24 block glyphs for the pixel skin. */
@Composable
fun SkinNavIcon(tab: Tab, tint: Color, modifier: Modifier) {
    if (LocalSkin.current.tokens.iconStyle == IconStyle.PIXEL) {
        Canvas(modifier) { drawPixelGlyph(tab, tint) }
    } else {
        Icon(tab.icon, contentDescription = null, tint = tint, modifier = modifier)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPixelGlyph(tab: Tab, color: Color) {
    val unit = min(size.width, size.height) / 24f
    fun block(x: Int, y: Int, w: Int, h: Int) = drawRect(
        color = color,
        topLeft = Offset(x * unit, y * unit),
        size = Size(w * unit, h * unit),
    )
    when (tab) {
        Tab.CHAT -> {
            block(4, 4, 16, 3)
            block(4, 7, 3, 9)
            block(17, 7, 3, 9)
            block(4, 16, 12, 3)
            block(12, 19, 4, 2)
        }

        Tab.SKILLS -> {
            block(4, 4, 6, 6)
            block(14, 4, 6, 6)
            block(4, 14, 6, 6)
            block(14, 14, 6, 6)
        }

        Tab.SERVICES -> {
            block(10, 3, 4, 5)
            block(10, 16, 4, 5)
            block(3, 10, 5, 4)
            block(16, 10, 5, 4)
            block(8, 8, 8, 8)
        }

        Tab.MEMORY -> {
            block(4, 4, 16, 5)
            block(4, 11, 12, 5)
            block(4, 18, 8, 3)
        }

        Tab.SETTINGS -> {
            block(10, 2, 4, 3)
            block(10, 19, 4, 3)
            block(2, 10, 3, 4)
            block(19, 10, 3, 4)
            block(7, 7, 10, 3)
            block(7, 14, 10, 3)
            block(7, 7, 3, 10)
            block(14, 7, 3, 10)
        }
    }
}

/** Indeterminate progress that snaps to blocks in the pixel skin. */
@Composable
fun SkinProgress(modifier: Modifier = Modifier) {
    if (LocalSkin.current.tokens.iconStyle == IconStyle.PIXEL) {
        PixelBlocks(modifier, count = 2)
    } else {
        CircularProgressIndicator(modifier = modifier, strokeWidth = 2.dp)
    }
}

@Composable
fun SkinLinearProgress(modifier: Modifier = Modifier) {
    if (LocalSkin.current.tokens.iconStyle == IconStyle.PIXEL) {
        PixelBlocks(modifier.fillMaxWidth(), count = 12)
    } else {
        LinearProgressIndicator(modifier = modifier.fillMaxWidth())
    }
}

@Composable
private fun PixelBlocks(modifier: Modifier, count: Int) {
    val colors = LocalSkin.current.colors
    val transition = rememberInfiniteTransition(label = "pixel-blocks")
    val tick by transition.animateFloat(
        initialValue = 0f,
        targetValue = count.toFloat(),
        animationSpec = infiniteRepeatable(tween(720, easing = LinearEasing), RepeatMode.Restart),
        label = "pixel-tick",
    )
    val active = tick.toInt().coerceAtMost(count - 1)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(width = 4.dp, height = 10.dp)
                    .background(if (index == active) colors.primary else colors.outlineVariant),
            )
        }
    }
}

/** Buttons whose shape follows the skin instead of Material's built-in pill. */
@Composable
fun SkinButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val pixel = LocalSkin.current.tokens.iconStyle == IconStyle.PIXEL
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = LocalSkin.current.tokens.buttonShape,
        elevation = if (pixel) {
            ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
        } else {
            ButtonDefaults.buttonElevation()
        },
        content = content,
    )
}

@Composable
fun SkinOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = LocalSkin.current.tokens.buttonShape,
        content = content,
    )
}
