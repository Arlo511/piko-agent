package dev.piko.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.piko.app.settings.SkinId

@Preview(name = "小可", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun PikoSkinPreview() = SkinGallery(SkinId.PIKO)

@Preview(name = "GitHub", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun GithubSkinPreview() = SkinGallery(SkinId.GITHUB)

@Preview(name = "iOS", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun IosSkinPreview() = SkinGallery(SkinId.IOS)

@Preview(name = "2.5D", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun DepthSkinPreview() = SkinGallery(SkinId.DEPTH)

@Preview(name = "像素", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun PixelSkinPreview() = SkinGallery(SkinId.PIXEL)

@Composable
private fun SkinGallery(id: SkinId) {
    PikoTheme(id) {
        Box(Modifier.fillMaxSize()) {
            SkinBackground(Modifier.matchParentSize())
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("小可 · 皮肤预览", style = MaterialTheme.typography.titleLarge)
                SkinCard {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("这是一个卡片", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                SkinBubble(user = true) {
                    Text("今天想做什么？", Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                }
                SkinBubble(user = false) {
                    Text("收到，我来处理。", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
                SkinButton(onClick = {}) { Text("开始") }
            }
        }
    }
}
