package dev.xiaoman.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** How much Markdown structure a message may expose. [LITERAL] keeps headings and lists as typed. */
internal enum class MarkdownScope { FULL, LITERAL }

internal enum class ColumnAlign { START, CENTER, END }

/** One block of a chat message, parsed without Compose so it can be unit tested on the JVM. */
internal sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullets(val ordered: Boolean, val items: List<MdListItem>) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data class Table(
        val header: List<String>,
        val rows: List<List<String>>,
        val align: List<ColumnAlign>,
    ) : MdBlock
}

internal data class MdListItem(val depth: Int, val number: Int?, val text: String)

/**
 * A deliberately small Markdown subset: fenced code, GFM tables, headings, lists and paragraphs.
 * Anything else stays literal text, which is why half-typed markers never disappear.
 */
internal object MarkdownParser {

    private val headingRe = Regex("^(#{1,6})\\s+(.*)$")
    private val bulletRe = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val orderedRe = Regex("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$")
    private val ruleRe = Regex("^\\s*([-*_])(?:\\s*\\1){2,}\\s*$")
    private val fenceRe = Regex("^\\s*```\\s*([A-Za-z0-9_+.-]*)")
    private val separatorRe = Regex("^\\s*\\|?(?:\\s*:?-{3,}:?\\s*\\|)*\\s*:?-{3,}:?\\s*\\|?\\s*$")

    fun parse(raw: String, scope: MarkdownScope = MarkdownScope.FULL): List<MdBlock> {
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = mutableListOf<MdBlock>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) {
                i++
                continue
            }
            val fence = fenceRe.find(line)
            if (fence != null) {
                i++
                val code = mutableListOf<String>()
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code += lines[i]
                    i++
                }
                if (i < lines.size) i++
                blocks += MdBlock.Code(fence.groupValues[1], code.joinToString("\n"))
                continue
            }
            if (isTableStart(lines, i)) {
                val hasSeparator = lines.getOrNull(i + 1)?.let(separatorRe::matches) == true
                val header = splitRow(lines[i])
                val align = if (hasSeparator) parseAlign(lines[i + 1]) else emptyList()
                i += if (hasSeparator) 2 else 1
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].isNotBlank() && lines[i].contains('|')) {
                    rows += splitRow(lines[i])
                    i++
                }
                blocks += normalizeTable(header, align, rows)
                continue
            }
            if (scope == MarkdownScope.FULL) {
                val heading = headingRe.matchEntire(line)
                if (heading != null) {
                    blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
                    i++
                    continue
                }
            }
            if (scope == MarkdownScope.FULL && (bulletRe.matches(line) || orderedRe.matches(line))) {
                i = parseList(lines, i, blocks)
                continue
            }
            if (scope == MarkdownScope.FULL && ruleRe.matches(line)) {
                i++
                continue
            }
            val paragraph = mutableListOf<String>()
            while (i < lines.size && lines[i].isNotBlank()) {
                if (paragraph.isNotEmpty() && isBlockStart(lines, i, scope)) break
                paragraph += lines[i]
                i++
            }
            blocks += MdBlock.Paragraph(paragraph.joinToString("\n").trim())
        }
        return blocks
    }

    private fun parseList(lines: List<String>, start: Int, blocks: MutableList<MdBlock>): Int {
        var i = start
        val ordered = orderedRe.matches(lines[i])
        val items = mutableListOf<MdListItem>()
        while (i < lines.size && lines[i].isNotBlank()) {
            val match = if (ordered) orderedRe.matchEntire(lines[i]) else bulletRe.matchEntire(lines[i])
            if (match == null) {
                if (items.isEmpty() || isBlockStart(lines, i, MarkdownScope.FULL)) break
                val last = items.removeAt(items.lastIndex)
                items += last.copy(text = last.text + "\n" + lines[i].trim())
                i++
                continue
            }
            val indent = match.groupValues[1].length
            val number = if (ordered) match.groupValues[2].toIntOrNull() else null
            val text = match.groupValues[if (ordered) 3 else 2]
            items += MdListItem(depth = (indent / 2).coerceAtMost(2), number = number, text = text)
            i++
        }
        blocks += MdBlock.Bullets(ordered, items)
        return i
    }

    private fun isTableStart(lines: List<String>, index: Int): Boolean {
        val line = lines[index]
        val next = lines.getOrNull(index + 1)
        if (line.trimStart().startsWith("|")) {
            // A separator row, or at least one more pipe row: render an unclosed table instead of raw pipes.
            return (next != null && separatorRe.matches(next)) || (next != null && next.trimStart().startsWith("|"))
        }
        return line.contains('|') && next != null && separatorRe.matches(next)
    }

    private fun isBlockStart(lines: List<String>, index: Int, scope: MarkdownScope): Boolean {
        val line = lines[index]
        if (fenceRe.find(line) != null) return true
        if (isTableStart(lines, index)) return true
        if (scope == MarkdownScope.FULL) {
            return headingRe.matches(line) || bulletRe.matches(line) ||
                orderedRe.matches(line) || ruleRe.matches(line)
        }
        return false
    }

    private fun parseAlign(separator: String): List<ColumnAlign> = splitRow(separator).map { cell ->
        val value = cell.trim()
        when {
            value.startsWith(":") && value.endsWith(":") -> ColumnAlign.CENTER
            value.endsWith(":") -> ColumnAlign.END
            else -> ColumnAlign.START
        }
    }

    private fun normalizeTable(
        header: List<String>,
        align: List<ColumnAlign>,
        rows: List<List<String>>,
    ): MdBlock.Table {
        val columns = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0, 1)
        fun pad(cells: List<String>) = List(columns) { cells.getOrElse(it) { "" } }
        return MdBlock.Table(
            header = pad(header),
            rows = rows.map(::pad),
            align = List(columns) { align.getOrElse(it) { ColumnAlign.START } },
        )
    }

    /** Splits one table row, honouring `\|` escapes and optional leading/trailing pipes. */
    private fun splitRow(line: String): List<String> {
        var text = line.trim()
        if (text.startsWith("|")) text = text.substring(1)
        if (text.endsWith("|") && !text.endsWith("\\|")) text = text.dropLast(1)
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var i = 0
        while (i < text.length) {
            val char = text[i]
            if (char == '\\' && i + 1 < text.length && text[i + 1] == '|') {
                cell.append('|')
                i += 2
                continue
            }
            if (char == '|') {
                cells += cell.toString().trim()
                cell.clear()
            } else {
                cell.append(char)
            }
            i++
        }
        cells += cell.toString().trim()
        return cells
    }
}

private val inlineRe = Regex("\\*\\*(.+?)\\*\\*|`([^`\\n]+)`")

/** Renders `**bold**` and `` `code` ``; unmatched markers stay exactly as the model wrote them. */
internal fun inlineMarkdown(text: String, codeBackground: Color = Color.Unspecified): AnnotatedString =
    buildAnnotatedString {
        var position = 0
        for (match in inlineRe.findAll(text)) {
            if (match.range.first > position) append(text.substring(position, match.range.first))
            val bold = match.groupValues[1]
            if (bold.isNotEmpty()) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
            } else {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                    append(match.groupValues[2])
                }
            }
            position = match.range.last + 1
        }
        if (position < text.length) append(text.substring(position))
    }
