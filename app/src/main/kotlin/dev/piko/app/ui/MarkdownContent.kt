package dev.piko.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.piko.app.settings.SkinId

/** Columns above this count, or cells longer than this, fall back to stacked key/value cards. */
private const val TABLE_MAX_COLUMNS = 4
private const val TABLE_MAX_CELL_CHARS = 24
private const val TABLE_CELL_HORIZONTAL = 8
private val TABLE_CELL_VERTICAL = 6.dp

/** Renders the supported Markdown subset with the current skin's colours, fonts and shapes. */
@Composable
internal fun MarkdownContent(
    raw: String,
    modifier: Modifier = Modifier,
    scope: MarkdownScope = MarkdownScope.FULL,
) {
    val skin = LocalSkin.current
    val blocks = remember(raw, scope) { MarkdownParser.parse(raw, scope) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Paragraph -> Text(
                    text = if (scope == MarkdownScope.FULL) {
                        inlineMarkdown(block.text, skin.colors.surfaceContainerHigh)
                    } else {
                        AnnotatedString(block.text)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )

                is MdBlock.Heading -> Text(
                    text = inlineMarkdown(block.text, skin.colors.surfaceContainerHigh),
                    style = if (block.level <= 2) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    },
                )

                is MdBlock.Bullets -> BulletList(block)

                is MdBlock.Code -> CodeBlock(block)

                is MdBlock.Table -> TableBlock(block)
            }
        }
    }
}

@Composable
private fun BulletList(block: MdBlock.Bullets) {
    val skin = LocalSkin.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(Modifier.padding(start = (item.depth * 14).dp)) {
                Text(
                    text = if (block.ordered) "${item.number ?: (index + 1)}." else "•",
                    modifier = Modifier.width(22.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = skin.colors.onSurfaceVariant,
                )
                Text(
                    text = inlineMarkdown(item.text, skin.colors.surfaceContainerHigh),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(block: MdBlock.Code) {
    val skin = LocalSkin.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = skin.tokens.inset.shape,
        color = skin.colors.surfaceContainerLow,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(10.dp),
        ) {
            Text(
                text = block.code,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                ),
                color = skin.colors.onSurface,
            )
        }
    }
}

@Composable
private fun TableBlock(block: MdBlock.Table) {
    val longest = (block.header + block.rows.flatten()).maxOfOrNull { it.length } ?: 0
    when {
        block.header.size <= 3 && longest <= TABLE_MAX_CELL_CHARS -> GridTable(block)
        block.header.size <= TABLE_MAX_COLUMNS && longest <= TABLE_MAX_CELL_CHARS -> FrozenTable(block)
        else -> KeyValueTable(block)
    }
}

/** Up to three columns: equal widths, no scrolling, cells wrap freely. */
@Composable
private fun GridTable(block: MdBlock.Table) {
    val skin = LocalSkin.current
    val shape = skin.tokens.inset.shape
    Column(Modifier.fillMaxWidth().clip(shape)) {
        Row(Modifier.fillMaxWidth().background(skin.tableHeaderColor)) {
            block.header.forEachIndexed { column, text ->
                TableCell(text, block.align[column], Modifier.weight(1f), header = true)
            }
        }
        block.rows.forEach { row ->
            TableDivider()
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { column, text ->
                    TableCell(text, block.align[column], Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Four columns: the first column stays pinned while the rest scroll sideways. Every cell has the
 * same fixed height so the pinned column and the scrolling columns stay aligned row by row.
 */
@Composable
private fun FrozenTable(block: MdBlock.Table) {
    val skin = LocalSkin.current
    val shape = skin.tokens.inset.shape
    val scroll = rememberScrollState()
    val rowHeight = tableRowHeight()
    val firstWidth = 76.dp
    val restWidth = 96.dp
    val allRows = listOf(block.header) + block.rows
    Row(Modifier.fillMaxWidth().clip(shape)) {
        Column(Modifier.width(firstWidth)) {
            allRows.forEachIndexed { index, row ->
                TableCell(
                    text = row[0],
                    align = block.align[0],
                    modifier = Modifier.width(firstWidth),
                    header = index == 0,
                    height = rowHeight,
                    fill = if (index == 0) skin.tableHeaderColor else Color.Transparent,
                )
                if (index < allRows.lastIndex) TableDivider()
            }
        }
        Box(Modifier.weight(1f).horizontalScroll(scroll)) {
            Row(Modifier.width(restWidth * (block.header.size - 1))) {
                for (column in 1 until block.header.size) {
                    Column(Modifier.width(restWidth)) {
                        allRows.forEachIndexed { index, row ->
                            TableCell(
                                text = row[column],
                                align = block.align[column],
                                modifier = Modifier.width(restWidth),
                                header = index == 0,
                                height = rowHeight,
                                fill = if (index == 0) skin.tableHeaderColor else Color.Transparent,
                            )
                            if (index < allRows.lastIndex) TableDivider()
                        }
                    }
                }
            }
        }
    }
}

/** Wide or long tables: each row becomes a small card, first cell as the title. */
@Composable
private fun KeyValueTable(block: MdBlock.Table) {
    val skin = LocalSkin.current
    val shape = skin.tokens.inset.shape
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        block.rows.forEach { row ->
            Surface(Modifier.fillMaxWidth(), shape = shape, color = skin.colors.surfaceContainerLow) {
                Column(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = inlineMarkdown(row.first(), skin.colors.surfaceContainerHigh),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    for (column in 1 until row.size) {
                        if (row[column].isBlank()) continue
                        Row {
                            Text(
                                text = block.header.getOrElse(column) { "" } + "：",
                                style = MaterialTheme.typography.bodySmall,
                                color = skin.colors.onSurfaceVariant,
                            )
                            Text(
                                text = inlineMarkdown(row[column], skin.colors.surfaceContainerHigh),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    align: ColumnAlign,
    modifier: Modifier = Modifier,
    header: Boolean = false,
    height: Dp? = null,
    fill: Color = Color.Transparent,
) {
    val sized = if (height != null) modifier.height(height).background(fill) else modifier
    Box(sized, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = TABLE_CELL_HORIZONTAL.dp,
                vertical = TABLE_CELL_VERTICAL,
            ),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
            ),
            textAlign = when (align) {
                ColumnAlign.START -> TextAlign.Start
                ColumnAlign.CENTER -> TextAlign.Center
                ColumnAlign.END -> TextAlign.End
            },
            maxLines = if (height != null) 2 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TableDivider() {
    val skin = LocalSkin.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(skin.tableDividerWidth)
            .background(skin.tableDividerColor),
    )
}

@Composable
private fun tableRowHeight(): Dp {
    val lineHeight = MaterialTheme.typography.bodySmall.lineHeight
    if (!lineHeight.isSp) return 48.dp
    val density = LocalDensity.current
    return with(density) { lineHeight.toDp() * 2 } + TABLE_CELL_VERTICAL * 2
}

private val Skin.tableHeaderColor: Color
    get() = tokens.tableHeaderFill ?: colors.surfaceContainerHigh

/** GitHub keeps its 1dp hairline; iOS uses its 0.5dp separator; pixel keeps the 2dp ink border. */
private val Skin.tableDividerWidth: Dp
    get() = tokens.tableDividerWidth
        ?: tokens.inset.border?.width
        ?: if (id == SkinId.IOS) 0.5.dp else 1.dp

private val Skin.tableDividerColor: Color
    get() = tokens.tableDividerColor ?: colors.outlineVariant
