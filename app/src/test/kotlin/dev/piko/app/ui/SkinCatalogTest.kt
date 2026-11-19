package dev.piko.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.piko.app.settings.SkinId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SkinCatalogTest {

    private val all = SkinId.entries.flatMap { id -> listOf(false, true).map { dark -> SkinCatalog.of(id, dark) } }

    @Test fun `every skin resolves in light and dark`() {
        assertEquals(SkinId.entries.size * 2, all.size)
        all.forEach { skin ->
            assertEquals(skin.id, SkinId.entries.first { it == skin.id })
            assertTrue(skin.tokens.card.fill != Color.Unspecified)
            assertTrue(skin.tokens.bubble.userFill != Color.Unspecified)
            assertTrue(skin.tokens.nav.indicatorShape != null)
        }
    }

    @Test fun `body text keeps readable contrast on every background`() {
        all.forEach { skin ->
            val ratio = contrast(skin.colors.onSurface, skin.colors.surface)
            assertTrue(ratio >= 4.5, "${skin.id} ${if (skin.dark) "dark" else "light"} body contrast=$ratio")
            val button = contrast(skin.colors.onPrimary, skin.colors.primary)
            assertTrue(button >= 3.0, "${skin.id} button contrast=$button")
        }
    }

    @Test fun `small labels stay at least 11sp`() {
        all.forEach { skin ->
            assertTrue(skin.typography.labelSmall.fontSize.value >= 11f, "${skin.id} labelSmall")
            assertTrue(skin.typography.bodySmall.fontSize.value >= 12f, "${skin.id} bodySmall")
        }
    }

    @Test fun `default skin matches the original palette`() {
        val light = SkinCatalog.of(SkinId.XIAOMAN, dark = false)
        assertEquals(Color(0xFF1E6B52), light.colors.primary)
        assertEquals(Color(0xFFF7F9F6), light.colors.background)
        assertNull(light.tokens.card.shadow)
        assertEquals(1f, light.tokens.card.border?.width?.value)
    }

    @Test fun `github is flat with hairline borders`() {
        val skin = SkinCatalog.of(SkinId.GITHUB, dark = false)
        assertNull(skin.tokens.card.shadow)
        assertEquals(1f, skin.tokens.card.border?.width?.value)
        assertEquals(1f, skin.tokens.inset.border?.width?.value)
        assertNotNull(skin.tokens.nav.topBorder)
    }

    @Test fun `ios uses soft shadows and no card border`() {
        val skin = SkinCatalog.of(SkinId.IOS, dark = false)
        assertNull(skin.tokens.card.border)
        val shadow = assertNotNull(skin.tokens.card.shadow)
        assertTrue(!shadow.hard)
        assertTrue(shadow.blur.value > 0f)
    }

    @Test fun `depth skin uses a gradient and layered extrusion`() {
        val skin = SkinCatalog.of(SkinId.DEPTH, dark = true)
        assertTrue(skin.tokens.background is BackgroundStyle.Glow)
        assertTrue(skin.tokens.card.extrusion.value >= 4f)
        assertEquals(true, skin.tokens.card.shadow?.hard)
        assertTrue(skin.tokens.tiltDegrees > 0f)
    }

    @Test fun `pixel skin snaps shapes and motion`() {
        val skin = SkinCatalog.of(SkinId.PIXEL, dark = false)
        assertEquals(IconStyle.PIXEL, skin.tokens.iconStyle)
        assertEquals(0, skin.tokens.motion.durationMillis)
        assertEquals(2f, skin.tokens.card.border?.width?.value)
        assertEquals(true, skin.tokens.card.shadow?.hard)
        assertTrue(skin.tokens.background is BackgroundStyle.Grid)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}
