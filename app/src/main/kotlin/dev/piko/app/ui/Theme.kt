package dev.piko.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import dev.piko.app.settings.SkinId

/**
 * The single theme entry point. [SkinCatalog] resolves the light/dark variant and supplies both
 * the MaterialTheme roles and the extra tokens that Material3 cannot express.
 */
@Composable
fun PikoTheme(skinId: SkinId = SkinId.XIAOMAN, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val skin = remember(skinId, dark) { SkinCatalog.of(skinId, dark) }
    CompositionLocalProvider(LocalSkin provides skin) {
        MaterialTheme(
            colorScheme = skin.colors,
            shapes = skin.shapes,
            typography = skin.typography,
            content = content,
        )
    }
}
