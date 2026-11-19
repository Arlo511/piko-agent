package dev.piko.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.piko.app.R
import dev.piko.app.settings.SkinId

/** The five built-in skins, each with a light and a dark variant. */
object SkinCatalog {

    private val pixelFont: FontFamily by lazy {
        FontFamily(Font(R.font.fusion_pixel_12px_proportional_zh_hans, FontWeight.Normal))
    }

    fun of(id: SkinId, dark: Boolean): Skin = when (id) {
        SkinId.XIAOMAN -> xiaoman(dark)
        SkinId.GITHUB -> github(dark)
        SkinId.IOS -> ios(dark)
        SkinId.DEPTH -> depth(dark)
        SkinId.PIXEL -> pixel(dark, pixelFont)
    }

    fun all(dark: Boolean): List<Skin> = SkinId.entries.map { of(it, dark) }

    // ---------------------------------------------------------------------------------------------
    // 小满默认：与改造前的视觉保持一致
    // ---------------------------------------------------------------------------------------------

    private fun xiaoman(dark: Boolean): Skin {
        val colors = if (dark) darkColorScheme(
            primary = Color(0xFF8BD6B5), onPrimary = Color(0xFF0B2A20),
            primaryContainer = Color(0xFF204D3C), onPrimaryContainer = Color(0xFFC6EBD9),
            secondary = Color(0xFF9ACBB4), onSecondary = Color(0xFF14291F),
            secondaryContainer = Color(0xFF26332C), onSecondaryContainer = Color(0xFFD5E5DB),
            background = Color(0xFF121815), onBackground = Color(0xFFE3ECE6),
            surface = Color(0xFF1B231F), onSurface = Color(0xFFE3ECE6),
            surfaceVariant = Color(0xFF202A24), onSurfaceVariant = Color(0xFFAEBDB3),
            surfaceContainerLow = Color(0xFF202A24), surfaceContainer = Color(0xFF1F2823),
            surfaceContainerHigh = Color(0xFF26312A),
            outline = Color(0xFF7D8C82), outlineVariant = Color(0xFF35463C),
            error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
            errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
        ) else lightColorScheme(
            primary = Color(0xFF1E6B52), onPrimary = Color.White,
            primaryContainer = Color(0xFFE1F0E8), onPrimaryContainer = Color(0xFF174332),
            secondary = Color(0xFF4E6B5C), onSecondary = Color.White,
            secondaryContainer = Color(0xFFEDF3EE), onSecondaryContainer = Color(0xFF254735),
            background = Color(0xFFF7F9F6), onBackground = Color(0xFF202C25),
            surface = Color.White, onSurface = Color(0xFF202C25),
            surfaceVariant = Color(0xFFF0F4EF), onSurfaceVariant = Color(0xFF637168),
            surfaceContainerLow = Color(0xFFF0F4EF), surfaceContainer = Color(0xFFF7F9F6),
            surfaceContainerHigh = Color(0xFFEDF1EC),
            outline = Color(0xFF8A988F), outlineVariant = Color(0xFFE0E7DF),
            error = Color(0xFFB3261E), onError = Color.White,
            errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
        )
        return Skin(
            id = SkinId.XIAOMAN,
            dark = dark,
            colors = colors,
            typography = Typography(
                bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
                bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
                titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
                titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
            ),
            shapes = Shapes(),
            tokens = SkinTokens(
                card = surface(18.dp, colors.surface, BorderStroke(1.dp, colors.outlineVariant)),
                inset = surface(14.dp, colors.surfaceContainerLow, BorderStroke(1.dp, colors.outlineVariant)),
                notice = surface(18.dp, colors.secondaryContainer),
                approval = surface(16.dp, colors.secondaryContainer),
                bubble = BubbleStyle(
                    userFill = colors.primaryContainer, userText = colors.onPrimaryContainer,
                    userShape = RoundedCornerShape(20.dp, 20.dp, 5.dp, 20.dp),
                    assistantFill = colors.surface, assistantText = colors.onSurface,
                    assistantBorder = BorderStroke(1.dp, colors.outlineVariant),
                    assistantShape = RoundedCornerShape(5.dp, 20.dp, 20.dp, 20.dp),
                ),
                nav = NavStyle(
                    container = colors.surface, topBorder = null,
                    indicator = colors.secondaryContainer, indicatorShape = CircleShape,
                    selected = colors.onSecondaryContainer, unselected = colors.onSurfaceVariant,
                    indicatorWidth = 64.dp, indicatorHeight = 32.dp,
                ),
                background = BackgroundStyle.Plain,
                motion = MotionStyle(durationMillis = 200, pressScale = 1f),
                avatarShape = CircleShape,
                chipShape = CircleShape,
                buttonShape = CircleShape,
                fieldShape = RoundedCornerShape(16.dp),
                noteFont = FontFamily.Monospace,
            ),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // GitHub：直角、发丝描边、零阴影、等宽元信息
    // ---------------------------------------------------------------------------------------------

    private fun github(dark: Boolean): Skin {
        val colors = if (dark) darkColorScheme(
            primary = Color(0xFF2F81F7), onPrimary = Color.White,
            primaryContainer = Color(0xFF121D2F), onPrimaryContainer = Color(0xFFB6D4FE),
            secondary = Color(0xFF6E7681), onSecondary = Color.White,
            secondaryContainer = Color(0xFF161B22), onSecondaryContainer = Color(0xFFE6EDF3),
            background = Color(0xFF0D1117), onBackground = Color(0xFFE6EDF3),
            surface = Color(0xFF161B22), onSurface = Color(0xFFE6EDF3),
            surfaceVariant = Color(0xFF21262D), onSurfaceVariant = Color(0xFF9198A1),
            surfaceContainerLow = Color(0xFF161B22), surfaceContainer = Color(0xFF1C2128),
            surfaceContainerHigh = Color(0xFF21262D),
            outline = Color(0xFF6E7681), outlineVariant = Color(0xFF30363D),
            error = Color(0xFFF85149), onError = Color(0xFF0D1117),
            errorContainer = Color(0xFF3D1518), onErrorContainer = Color(0xFFFFB4B1),
        ) else lightColorScheme(
            primary = Color(0xFF0969DA), onPrimary = Color.White,
            primaryContainer = Color(0xFFDDF4FF), onPrimaryContainer = Color(0xFF0A3069),
            secondary = Color(0xFF6E7781), onSecondary = Color.White,
            secondaryContainer = Color(0xFFF6F8FA), onSecondaryContainer = Color(0xFF1F2328),
            background = Color(0xFFFFFFFF), onBackground = Color(0xFF1F2328),
            surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1F2328),
            surfaceVariant = Color(0xFFF6F8FA), onSurfaceVariant = Color(0xFF59636E),
            surfaceContainerLow = Color(0xFFF6F8FA), surfaceContainer = Color(0xFFF6F8FA),
            surfaceContainerHigh = Color(0xFFEAEEF2),
            outline = Color(0xFF8C959F), outlineVariant = Color(0xFFD0D7DE),
            error = Color(0xFFCF222E), onError = Color.White,
            errorContainer = Color(0xFFFFEBE9), onErrorContainer = Color(0xFF82071E),
        )
        val border = BorderStroke(1.dp, colors.outlineVariant)
        return Skin(
            id = SkinId.GITHUB,
            dark = dark,
            colors = colors,
            typography = skinTypography(
                labelFont = FontFamily.Monospace,
                title = 18.sp, titleLine = 24.sp,
                subtitle = 15.sp, subtitleLine = 22.sp,
                label = 11.sp, labelLine = 16.sp,
                headline = 22.sp, headlineLine = 28.sp,
                largeTitle = 20.sp, largeTitleLine = 26.sp,
            ),
            shapes = skinShapes(4.dp, 6.dp, 6.dp, 8.dp, 10.dp),
            tokens = SkinTokens(
                card = surface(6.dp, colors.surface, border),
                inset = surface(6.dp, colors.surfaceContainerLow, border),
                notice = surface(6.dp, colors.secondaryContainer, border),
                approval = surface(6.dp, colors.surface, border),
                bubble = BubbleStyle(
                    userFill = colors.primaryContainer, userText = colors.onPrimaryContainer,
                    userShape = RoundedCornerShape(6.dp),
                    assistantFill = colors.surface, assistantText = colors.onSurface,
                    assistantBorder = border, assistantShape = RoundedCornerShape(6.dp),
                ),
                nav = NavStyle(
                    container = colors.surface, topBorder = border,
                    indicator = colors.primaryContainer, indicatorShape = RoundedCornerShape(6.dp),
                    selected = colors.primary, unselected = colors.onSurfaceVariant,
                    indicatorWidth = 48.dp, indicatorHeight = 26.dp,
                ),
                background = BackgroundStyle.Plain,
                motion = MotionStyle(durationMillis = 160, pressScale = 1f),
                avatarShape = RoundedCornerShape(4.dp),
                chipShape = RoundedCornerShape(2.dp),
                buttonShape = RoundedCornerShape(6.dp),
                fieldShape = RoundedCornerShape(6.dp),
                noteFont = FontFamily.Monospace,
            ),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // iOS：分组灰底、大圆角、发丝分隔线、无边框气泡
    // ---------------------------------------------------------------------------------------------

    private fun ios(dark: Boolean): Skin {
        val colors = if (dark) darkColorScheme(
            primary = Color(0xFF0A84FF), onPrimary = Color.White,
            primaryContainer = Color(0xFF0A2A4D), onPrimaryContainer = Color(0xFF9CCBFF),
            secondary = Color(0xFF30D158), onSecondary = Color(0xFF0B2E14),
            secondaryContainer = Color(0xFF26262A), onSecondaryContainer = Color(0xFFF2F2F7),
            background = Color(0xFF000000), onBackground = Color(0xFFF2F2F7),
            surface = Color(0xFF1C1C1E), onSurface = Color(0xFFF2F2F7),
            surfaceVariant = Color(0xFF26262A), onSurfaceVariant = Color(0xFFAEAEB2),
            surfaceContainerLow = Color(0xFF1C1C1E), surfaceContainer = Color(0xFF1C1C1E),
            surfaceContainerHigh = Color(0xFF2C2C2E),
            outline = Color(0xFF8E8E93), outlineVariant = Color(0xFF38383A),
            error = Color(0xFFFF453A), onError = Color.White,
            errorContainer = Color(0xFF4A1210), onErrorContainer = Color(0xFFFFCFCB),
        ) else lightColorScheme(
            primary = Color(0xFF007AFF), onPrimary = Color.White,
            primaryContainer = Color(0xFFE5F0FF), onPrimaryContainer = Color(0xFF003A75),
            secondary = Color(0xFF34C759), onSecondary = Color.White,
            secondaryContainer = Color(0xFFE9E9EB), onSecondaryContainer = Color(0xFF1C1C1E),
            background = Color(0xFFF2F2F7), onBackground = Color(0xFF000000),
            surface = Color(0xFFFFFFFF), onSurface = Color(0xFF000000),
            surfaceVariant = Color(0xFFE9E9EB), onSurfaceVariant = Color(0xFF6C6C70),
            surfaceContainerLow = Color(0xFFFFFFFF), surfaceContainer = Color(0xFFF2F2F7),
            surfaceContainerHigh = Color(0xFFE5E5EA),
            outline = Color(0xFF8E8E93), outlineVariant = Color(0xFFC6C6C8),
            error = Color(0xFFFF3B30), onError = Color.White,
            errorContainer = Color(0xFFFFE5E3), onErrorContainer = Color(0xFF8A1C15),
        )
        val shadow = ShadowStyle(
            offsetX = 0.dp, offsetY = 2.dp, blur = 8.dp,
            color = Color.Black.copy(alpha = if (dark) 0.35f else 0.06f), hard = false,
        )
        return Skin(
            id = SkinId.IOS,
            dark = dark,
            colors = colors,
            typography = skinTypography(
                body = 17.sp, bodyLine = 24.sp,
                small = 13.sp, smallLine = 18.sp,
                title = 20.sp, titleLine = 25.sp,
                subtitle = 17.sp, subtitleLine = 22.sp,
                label = 11.sp, labelLine = 14.sp,
                labelMedium = 13.sp, labelMediumLine = 17.sp,
                headline = 28.sp, headlineLine = 34.sp,
                largeTitle = 32.sp, largeTitleLine = 38.sp,
                largeBody = 17.sp, largeBodyLine = 24.sp,
                headlineWeight = FontWeight.Bold,
                largeTitleWeight = FontWeight.Bold,
            ),
            shapes = skinShapes(8.dp, 10.dp, 12.dp, 16.dp, 22.dp),
            tokens = SkinTokens(
                card = surface(16.dp, colors.surface, shadow = shadow),
                inset = surface(12.dp, colors.surfaceVariant),
                notice = surface(16.dp, colors.secondaryContainer),
                approval = surface(16.dp, colors.surface, shadow = shadow),
                bubble = BubbleStyle(
                    userFill = colors.primary, userText = colors.onPrimary,
                    userShape = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp),
                    assistantFill = colors.surfaceVariant, assistantText = colors.onSurface,
                    assistantBorder = null,
                    assistantShape = RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp),
                ),
                nav = NavStyle(
                    container = colors.surface,
                    topBorder = BorderStroke(0.5.dp, colors.outlineVariant),
                    indicator = Color.Transparent, indicatorShape = CircleShape,
                    selected = colors.primary, unselected = colors.onSurfaceVariant,
                    indicatorWidth = 0.dp, indicatorHeight = 0.dp,
                ),
                background = BackgroundStyle.Plain,
                motion = MotionStyle(durationMillis = 240, pressScale = 0.98f),
                avatarShape = CircleShape,
                chipShape = CircleShape,
                buttonShape = CircleShape,
                fieldShape = RoundedCornerShape(50),
                noteFont = FontFamily.Default,
            ),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 2.5D：渐变底、挤出层、轻微透视
    // ---------------------------------------------------------------------------------------------

    private fun depth(dark: Boolean): Skin {
        val colors = if (dark) darkColorScheme(
            primary = Color(0xFF818CF8), onPrimary = Color(0xFF1E1B4B),
            primaryContainer = Color(0xFF312E81), onPrimaryContainer = Color(0xFFC7D2FE),
            secondary = Color(0xFFFB923C), onSecondary = Color(0xFF1C1917),
            secondaryContainer = Color(0xFF7C2D12), onSecondaryContainer = Color(0xFFFFEDD5),
            background = Color(0xFF141625), onBackground = Color(0xFFE8E9F5),
            surface = Color(0xFF1E2130), onSurface = Color(0xFFE8E9F5),
            surfaceVariant = Color(0xFF262A3D), onSurfaceVariant = Color(0xFFA9AEC8),
            surfaceContainerLow = Color(0xFF232739), surfaceContainer = Color(0xFF1E2130),
            surfaceContainerHigh = Color(0xFF2B2F44),
            outline = Color(0xFF6B7090), outlineVariant = Color(0xFF353A52),
            error = Color(0xFFF87171), onError = Color(0xFF450A0A),
            errorContainer = Color(0xFF7F1D1D), onErrorContainer = Color(0xFFFEE2E2),
        ) else lightColorScheme(
            primary = Color(0xFF4F46E5), onPrimary = Color.White,
            primaryContainer = Color(0xFFE4E7FF), onPrimaryContainer = Color(0xFF2A2478),
            secondary = Color(0xFFF97316), onSecondary = Color.White,
            secondaryContainer = Color(0xFFFFEDD5), onSecondaryContainer = Color(0xFF7C2D12),
            background = Color(0xFFEEF2FF), onBackground = Color(0xFF1E1B4B),
            surface = Color.White, onSurface = Color(0xFF1E1B4B),
            surfaceVariant = Color(0xFFF1F3FF), onSurfaceVariant = Color(0xFF5B5F7A),
            surfaceContainerLow = Color(0xFFF5F6FF), surfaceContainer = Color.White,
            surfaceContainerHigh = Color(0xFFE9ECFF),
            outline = Color(0xFF9AA0C0), outlineVariant = Color(0xFFD7DBF5),
            error = Color(0xFFDC2626), onError = Color.White,
            errorContainer = Color(0xFFFEE2E2), onErrorContainer = Color(0xFF7F1D1D),
        )
        val shadow = ShadowStyle(
            offsetX = 0.dp, offsetY = 8.dp, blur = 0.dp,
            color = if (dark) Color(0xFF05060F) else Color(0xFFC7CDF5), hard = true,
        )
        return Skin(
            id = SkinId.DEPTH,
            dark = dark,
            colors = colors,
            typography = skinTypography(
                title = 19.sp, titleLine = 26.sp,
                subtitle = 16.sp, subtitleLine = 22.sp,
                headline = 26.sp, headlineLine = 32.sp,
                largeTitle = 24.sp, largeTitleLine = 30.sp,
                titleWeight = FontWeight.Bold,
                headlineWeight = FontWeight.Bold,
                largeTitleWeight = FontWeight.Bold,
            ),
            shapes = skinShapes(10.dp, 12.dp, 14.dp, 18.dp, 22.dp),
            tokens = SkinTokens(
                card = surface(16.dp, colors.surface, shadow = shadow, extrusion = 6.dp),
                inset = surface(14.dp, colors.surfaceContainerLow, shadow = shadow, extrusion = 4.dp),
                notice = surface(16.dp, colors.secondaryContainer),
                approval = surface(16.dp, colors.surface, shadow = shadow, extrusion = 6.dp),
                bubble = BubbleStyle(
                    userFill = colors.primary, userText = colors.onPrimary,
                    userShape = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp),
                    assistantFill = colors.surface, assistantText = colors.onSurface,
                    assistantBorder = null,
                    assistantShape = RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp),
                ),
                nav = NavStyle(
                    container = colors.surface, topBorder = null,
                    indicator = colors.primaryContainer, indicatorShape = RoundedCornerShape(12.dp),
                    selected = colors.primary, unselected = colors.onSurfaceVariant,
                    indicatorWidth = 56.dp, indicatorHeight = 30.dp,
                ),
                background = BackgroundStyle.Glow(
                    top = if (dark) Color(0xFF141625) else Color(0xFFEEF2FF),
                    bottom = if (dark) Color(0xFF101120) else Color(0xFFF8FAFF),
                ),
                motion = MotionStyle(durationMillis = 200, pressScale = 0.97f),
                avatarShape = CircleShape,
                chipShape = CircleShape,
                buttonShape = RoundedCornerShape(14.dp),
                fieldShape = RoundedCornerShape(14.dp),
                noteFont = FontFamily.Default,
                tiltDegrees = 8f,
            ),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 像素风：0 圆角、粗描边、硬阴影、棋盘格、点阵字体
    // ---------------------------------------------------------------------------------------------

    private fun pixel(dark: Boolean, font: FontFamily): Skin {
        val colors = if (dark) darkColorScheme(
            primary = Color(0xFFFFE66D), onPrimary = Color(0xFF1B1B2F),
            primaryContainer = Color(0xFF3A3A24), onPrimaryContainer = Color(0xFFFFF3A8),
            secondary = Color(0xFF4ECDC4), onSecondary = Color(0xFF0F0F1B),
            secondaryContainer = Color(0xFF1F3D3A), onSecondaryContainer = Color(0xFFBFF5EF),
            background = Color(0xFF0F0F1B), onBackground = Color(0xFFF7F7F7),
            surface = Color(0xFF1B1B2F), onSurface = Color(0xFFF7F7F7),
            surfaceVariant = Color(0xFF252540), onSurfaceVariant = Color(0xFFB9B9D0),
            surfaceContainerLow = Color(0xFF202036), surfaceContainer = Color(0xFF1B1B2F),
            surfaceContainerHigh = Color(0xFF2A2A45),
            outline = Color(0xFF6E6E8E), outlineVariant = Color(0xFF6E6E8E),
            error = Color(0xFFFF6B6B), onError = Color(0xFF1B1B2F),
            errorContainer = Color(0xFF5A1A1A), onErrorContainer = Color(0xFFFFD9D9),
        ) else lightColorScheme(
            primary = Color(0xFFE07A5F), onPrimary = Color(0xFF1B1B2F),
            primaryContainer = Color(0xFFFFE3D9), onPrimaryContainer = Color(0xFF5A1F10),
            secondary = Color(0xFF3D405B), onSecondary = Color.White,
            secondaryContainer = Color(0xFFEDE5D4), onSecondaryContainer = Color(0xFF2A2C40),
            background = Color(0xFFF4F1DE), onBackground = Color(0xFF1B1B2F),
            surface = Color.White, onSurface = Color(0xFF1B1B2F),
            surfaceVariant = Color(0xFFF1EBDD), onSurfaceVariant = Color(0xFF4A4A5E),
            surfaceContainerLow = Color(0xFFF7F2E6), surfaceContainer = Color.White,
            surfaceContainerHigh = Color(0xFFEDE5D4),
            outline = Color(0xFF1B1B2F), outlineVariant = Color(0xFF1B1B2F),
            error = Color(0xFFB3261E), onError = Color.White,
            errorContainer = Color(0xFFF7D7D2), onErrorContainer = Color(0xFF5F1510),
        )
        val border = BorderStroke(2.dp, colors.outlineVariant)
        val hardShadow = ShadowStyle(
            offsetX = 4.dp, offsetY = 4.dp, blur = 0.dp,
            color = Color(0xFF000000), hard = true,
        )
        return Skin(
            id = SkinId.PIXEL,
            dark = dark,
            colors = colors,
            typography = skinTypography(
                font = font,
                body = 14.sp, bodyLine = 22.sp,
                small = 12.sp, smallLine = 18.sp,
                title = 18.sp, titleLine = 26.sp,
                subtitle = 15.sp, subtitleLine = 22.sp,
                label = 11.sp, labelLine = 16.sp,
                labelMedium = 12.sp, labelMediumLine = 18.sp,
                headline = 24.sp, headlineLine = 30.sp,
                largeTitle = 21.sp, largeTitleLine = 28.sp,
                largeBody = 15.sp, largeBodyLine = 24.sp,
            ),
            shapes = skinShapes(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
            tokens = SkinTokens(
                card = surface(0.dp, colors.surface, border, hardShadow),
                inset = surface(0.dp, colors.surfaceContainerLow, border, hardShadow),
                notice = surface(0.dp, colors.secondaryContainer, border),
                approval = surface(0.dp, colors.surface, border, hardShadow),
                bubble = BubbleStyle(
                    userFill = colors.primary, userText = colors.onPrimary,
                    userShape = RoundedCornerShape(0.dp),
                    assistantFill = colors.surface, assistantText = colors.onSurface,
                    assistantBorder = border, assistantShape = RoundedCornerShape(0.dp),
                ),
                nav = NavStyle(
                    container = colors.surface, topBorder = border,
                    indicator = colors.primary, indicatorShape = RoundedCornerShape(0.dp),
                    selected = colors.onPrimary, unselected = colors.onSurfaceVariant,
                    indicatorWidth = 44.dp, indicatorHeight = 26.dp,
                ),
                background = BackgroundStyle.Grid(
                    step = 16.dp, line = 0.dp,
                    color = colors.background,
                    checker = colors.surfaceVariant.copy(alpha = 0.28f),
                ),
                motion = MotionStyle(durationMillis = 0, pressScale = 1f),
                avatarShape = RoundedCornerShape(0.dp),
                chipShape = RoundedCornerShape(0.dp),
                buttonShape = RoundedCornerShape(0.dp),
                fieldShape = RoundedCornerShape(0.dp),
                noteFont = font,
                iconStyle = IconStyle.PIXEL,
            ),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------

    private fun surface(
        radius: Dp,
        fill: Color,
        border: BorderStroke? = null,
        shadow: ShadowStyle? = null,
        extrusion: Dp = 0.dp,
    ) = SurfaceStyle(
        shape = RoundedCornerShape(radius),
        fill = fill,
        border = border,
        shadow = shadow,
        extrusion = extrusion,
    )

    private fun skinShapes(
        extraSmall: Dp,
        small: Dp,
        medium: Dp,
        large: Dp,
        extraLarge: Dp,
    ) = Shapes(
        extraSmall = RoundedCornerShape(extraSmall),
        small = RoundedCornerShape(small),
        medium = RoundedCornerShape(medium),
        large = RoundedCornerShape(large),
        extraLarge = RoundedCornerShape(extraLarge),
    )

    private fun skinTypography(
        font: FontFamily = FontFamily.Default,
        labelFont: FontFamily = font,
        body: TextUnit = 14.sp,
        bodyLine: TextUnit = 22.sp,
        small: TextUnit = 12.sp,
        smallLine: TextUnit = 18.sp,
        title: TextUnit = 18.sp,
        titleLine: TextUnit = 24.sp,
        subtitle: TextUnit = 15.sp,
        subtitleLine: TextUnit = 22.sp,
        label: TextUnit = 11.sp,
        labelLine: TextUnit = 16.sp,
        labelMedium: TextUnit = 12.sp,
        labelMediumLine: TextUnit = 16.sp,
        headline: TextUnit = 24.sp,
        headlineLine: TextUnit = 30.sp,
        largeTitle: TextUnit = 22.sp,
        largeTitleLine: TextUnit = 28.sp,
        largeBody: TextUnit = 15.sp,
        largeBodyLine: TextUnit = 22.sp,
        titleWeight: FontWeight = FontWeight.SemiBold,
        headlineWeight: FontWeight = FontWeight.SemiBold,
        largeTitleWeight: FontWeight = FontWeight.SemiBold,
    ) = Typography(
        bodyLarge = TextStyle(fontFamily = font, fontSize = largeBody, lineHeight = largeBodyLine),
        bodyMedium = TextStyle(fontFamily = font, fontSize = body, lineHeight = bodyLine),
        bodySmall = TextStyle(fontFamily = font, fontSize = small, lineHeight = smallLine),
        titleLarge = TextStyle(fontFamily = font, fontSize = largeTitle, lineHeight = largeTitleLine, fontWeight = largeTitleWeight),
        titleMedium = TextStyle(fontFamily = font, fontSize = title, lineHeight = titleLine, fontWeight = titleWeight),
        titleSmall = TextStyle(fontFamily = font, fontSize = subtitle, lineHeight = subtitleLine, fontWeight = titleWeight),
        labelMedium = TextStyle(fontFamily = labelFont, fontSize = labelMedium, lineHeight = labelMediumLine),
        labelSmall = TextStyle(fontFamily = labelFont, fontSize = label, lineHeight = labelLine),
        headlineSmall = TextStyle(fontFamily = font, fontSize = headline, lineHeight = headlineLine, fontWeight = headlineWeight),
    )
}
