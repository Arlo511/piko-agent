package dev.piko.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.piko.app.R
import dev.piko.app.settings.SkinId

/** 设置 → 外观：即时切换，不参与模型表单的保存按钮。 */
@Composable
fun SkinPicker(selected: SkinId, onSelect: (SkinId) -> Unit) {
    Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.settings_skin_hint), style = MaterialTheme.typography.bodySmall)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SkinId.entries.forEach { id ->
            SkinSwatch(
                id = id,
                selected = id == selected,
                onClick = { onSelect(id) },
            )
        }
    }
}

@Composable
private fun SkinSwatch(id: SkinId, selected: Boolean, onClick: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val skin = remember(id, dark) { SkinCatalog.of(id, dark) }
    val tokens = skin.tokens
    val outline = if (selected) skin.colors.primary else skin.colors.outlineVariant
    val shape = if (id == SkinId.PIXEL) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(shape)
            .background(skin.colors.surface)
            .border(BorderStroke(if (id == SkinId.PIXEL) 2.dp else 1.dp, outline), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(skin.colors.background),
        ) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 10.dp)
                    .size(width = 58.dp, height = 18.dp)
                    .background(tokens.bubble.assistantFill, tokens.bubble.assistantShape)
                    .then(
                        tokens.bubble.assistantBorder?.let { border ->
                            Modifier.border(border, tokens.bubble.assistantShape)
                        } ?: Modifier,
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 10.dp)
                    .size(width = 48.dp, height = 16.dp)
                    .background(tokens.bubble.userFill, tokens.bubble.userShape),
            )
        }
        Text(
            text = stringResource(id.label()),
            style = skin.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = skin.colors.onSurface,
        )
        Text(
            text = stringResource(if (selected) R.string.skin_in_use else R.string.skin_tap_to_use),
            style = skin.typography.labelSmall,
            color = if (selected) skin.colors.primary else skin.colors.onSurfaceVariant,
        )
    }
}

@StringRes
private fun SkinId.label(): Int = when (this) {
    SkinId.XIAOMAN -> R.string.skin_piko
    SkinId.GITHUB -> R.string.skin_github
    SkinId.IOS -> R.string.skin_ios
    SkinId.DEPTH -> R.string.skin_depth
    SkinId.PIXEL -> R.string.skin_pixel
}
