package dev.piko.app.ui

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MarkdownBlocksTest {

    @Test fun modelTableWithoutSpacesAroundDashesRendersAsTable() {
        val raw = "| 水果 | 单价（元/斤） | 库存（斤） |\n" +
            "|------|--------------|-----------|\n" +
            "| 红富士苹果 | 8.9 | 120 |"
        val table = assertIs<MdBlock.Table>(MarkdownParser.parse(raw).single())
        assertEquals(3, table.header.size)
        assertEquals(listOf("红富士苹果", "8.9", "120"), table.rows.single())
    }

    @Test fun closedTableParsesHeaderRowsAndAlignment() {
        val blocks = MarkdownParser.parse(
            """
            | 商品 | 价格 |
            | --- | ---: |
            | 笔记本 | 18.5 |
            """.trimIndent(),
        )
        val table = assertIs<MdBlock.Table>(blocks.single())
        assertEquals(listOf("商品", "价格"), table.header)
        assertEquals(listOf(listOf("笔记本", "18.5")), table.rows)
        assertEquals(listOf(ColumnAlign.START, ColumnAlign.END), table.align)
    }

    @Test fun unclosedTableWithoutSeparatorStillRendersAsTable() {
        val blocks = MarkdownParser.parse("| 项目 | 状态 |\n| 机票 | 已订 |")
        val table = assertIs<MdBlock.Table>(blocks.single())
        assertEquals(listOf("项目", "状态"), table.header)
        assertEquals(listOf(listOf("机票", "已订")), table.rows)
        assertEquals(listOf(ColumnAlign.START, ColumnAlign.START), table.align)
    }

    @Test fun escapedPipeStaysInOneCell() {
        val blocks = MarkdownParser.parse(
            """
            | 名称 | 备注 |
            | --- | --- |
            | A \| B | 正常 |
            """.trimIndent(),
        )
        val table = assertIs<MdBlock.Table>(blocks.single())
        assertEquals(listOf("A | B", "正常"), table.rows.single())
    }

    @Test fun raggedRowsArePaddedToTheWidestRow() {
        val blocks = MarkdownParser.parse(
            """
            | a | b |
            | --- | --- |
            | 1 | 2 | 3 |
            """.trimIndent(),
        )
        val table = assertIs<MdBlock.Table>(blocks.single())
        assertEquals(listOf("a", "b", ""), table.header)
        assertEquals(listOf(listOf("1", "2", "3")), table.rows)
        assertEquals(3, table.align.size)
    }

    @Test fun pipeInsideCodeFenceIsNotATable() {
        val blocks = MarkdownParser.parse("```\n| a | b |\n| --- | --- |\n```")
        val code = assertIs<MdBlock.Code>(blocks.single())
        assertEquals("| a | b |\n| --- | --- |", code.code)
    }

    @Test fun orderedAndUnorderedListsStaySeparate() {
        val blocks = MarkdownParser.parse("- 一\n- 二\n1. 甲\n2. 乙")
        assertEquals(2, blocks.size)
        val bullets = assertIs<MdBlock.Bullets>(blocks[0])
        assertEquals(false, bullets.ordered)
        assertEquals(listOf("一", "二"), bullets.items.map { it.text })
        val ordered = assertIs<MdBlock.Bullets>(blocks[1])
        assertEquals(true, ordered.ordered)
        assertEquals(listOf(1, 2), ordered.items.map { it.number })
    }

    @Test fun nestedListKeepsDepth() {
        val blocks = MarkdownParser.parse("- 一\n  - 子项")
        val bullets = assertIs<MdBlock.Bullets>(blocks.single())
        assertEquals(listOf(0, 1), bullets.items.map { it.depth })
    }

    @Test fun headingLevelsAreCaptured() {
        val blocks = MarkdownParser.parse("# 一级\n### 三级\n正文")
        assertEquals(MdBlock.Heading(1, "一级"), blocks[0])
        assertEquals(MdBlock.Heading(3, "三级"), blocks[1])
        assertEquals(MdBlock.Paragraph("正文"), blocks[2])
    }

    @Test fun literalScopeKeepsHeadingsAndListsAsTyped() {
        val blocks = MarkdownParser.parse("# 标题\n- 列表", MarkdownScope.LITERAL)
        assertEquals(MdBlock.Paragraph("# 标题\n- 列表"), blocks.single())
    }

    @Test fun literalScopeStillParsesTables() {
        val blocks = MarkdownParser.parse(
            "| a | b |\n| --- | --- |\n| 1 | 2 |",
            MarkdownScope.LITERAL,
        )
        assertIs<MdBlock.Table>(blocks.single())
    }

    @Test fun standaloneRuleIsDroppedInsteadOfLeakingDashes() {
        val blocks = MarkdownParser.parse("前\n\n---\n\n后")
        assertEquals(listOf(MdBlock.Paragraph("前"), MdBlock.Paragraph("后")), blocks)
    }

    @Test fun proseWithSinglePipeStaysAParagraph() {
        val blocks = MarkdownParser.parse("价格 | 说明")
        assertEquals(MdBlock.Paragraph("价格 | 说明"), blocks.single())
    }
}
