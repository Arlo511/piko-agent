package dev.xiaoman.agent

import dev.xiaoman.api.AgentEvent
import dev.xiaoman.api.ChatRole
import dev.xiaoman.api.ToolCall
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonaPromptTest {
    @Test
    fun personaAndProfileShapeTheSystemPrompt() = runTest {
        val h = Harness()
        h.memory.write(Persona.note("知无不言的亲密导师", listOf("设计类问题直接给专业结论", "其他问题少用术语、解释更详细")))
        h.memory.write(Persona.profileNote("设计师"))
        h.chat.reply("好")

        h.agent.handle("这版海报的留白怎么调", h.events, h.stop)

        val system = h.chat.requests.single().messages[0].text
        assertTrue(Prompts.PERSONA_HEADER in system)
        assertTrue("性格基调：知无不言的亲密导师" in system)
        assertTrue(Prompts.PROFILE_HEADER in system)
        assertTrue("- [identity] 设计师" in system)
        assertTrue(Prompts.PERSONA_BOUNDARY in system)
        assertTrue(Prompts.PERSONA_ADAPT_RULES in system)
        assertTrue(system.indexOf(Prompts.PERSONA_HEADER) < system.indexOf(Prompts.PROFILE_HEADER))
        assertTrue(system.indexOf(Prompts.PROFILE_HEADER) < system.indexOf(Prompts.NOW_HEADER))
    }

    @Test
    fun designerExampleIsFullyEncoded() = runTest {
        val h = Harness()
        h.memory.write(
            Persona.note(
                "知无不言的亲密导师",
                listOf("设计类问题直接给专业结论，不解释基础概念", "设计以外的问题少用术语、解释更详细，并引导我思考"),
            ),
        )
        h.memory.write(Persona.profileNote("设计师"))
        h.chat.reply("好")

        h.agent.handle("这个页面的视觉层级怎么优化", h.events, h.stop)

        val system = h.chat.requests.single().messages[0].text
        assertTrue("知无不言的亲密导师" in system)
        assertTrue("设计类问题直接给专业结论，不解释基础概念" in system)
        assertTrue("设计以外的问题少用术语、解释更详细，并引导我思考" in system)
        assertTrue("[identity] 设计师" in system)
        assertTrue("直接用专业口吻给结论" in system && "少用专业术语" in system)
    }

    @Test
    fun noPersonaLeavesDefaultPromptUnchanged() = runTest {
        val h = Harness()
        h.chat.reply("你好")

        h.agent.handle("你好", h.events, h.stop)

        val system = h.chat.requests.single().messages[0].text
        assertTrue(system.startsWith("你是小满"))
        assertFalse(Prompts.PERSONA_HEADER in system)
        assertFalse(Prompts.PROFILE_HEADER in system)
    }

    @Test
    fun personaIsNotDuplicatedByMemoryHits() = runTest {
        val h = Harness()
        h.memory.write(Persona.note("知无不言的亲密导师", emptyList()))
        h.chat.reply("好")

        h.agent.handle("知无不言的亲密导师是什么意思", h.events, h.stop)

        assertEquals(1, h.chat.requests.single().messages[0].text.countOf("知无不言的亲密导师"))
    }

    @Test
    fun personaSetToolWritesNoteAndShowsInNextTurn() = runTest {
        val h = Harness()
        h.chat.tools(
            ToolCall(
                "p1",
                AgentTools.PERSONA_SET,
                """{"tone":"知无不言的亲密导师","details":["设计类问题直接给专业结论"]}""",
            ),
        ).reply("已设置")

        h.agent.handle("以后你做个知无不言的亲密导师", h.events, h.stop)

        val note = h.memory.notes[Persona.PERMALINK]!!
        assertEquals(Persona.TYPE, note.type)
        assertEquals("知无不言的亲密导师", Persona.tone(note))
        assertEquals(listOf("设计类问题直接给专业结论"), Persona.details(note))
        assertTrue(h.events.of<AgentEvent.MemoryUpdated>().any { it.permalink == Persona.PERMALINK })

        h.chat.reply("好的")
        h.agent.handle("你好", h.events, h.stop)
        assertTrue("性格基调：知无不言的亲密导师" in h.chat.requests.last().messages[0].text)
    }

    @Test
    fun personaSetRejectsOversizedAndSensitiveInput() = runTest {
        val h = Harness()
        h.chat.tools(
            ToolCall("p1", AgentTools.PERSONA_SET, """{"tone":"${"长".repeat(Persona.MAX_TONE_CHARS + 1)}"}"""),
            ToolCall("p2", AgentTools.PERSONA_SET, """{"tone":"导师","details":["联系我 13800138000"]}"""),
        ).reply("抱歉")

        h.agent.handle("设置性格", h.events, h.stop)

        val results = h.chat.requests[1].messages.filter { it.role == ChatRole.TOOL }.map { it.text }
        assertEquals(2, results.size)
        assertTrue(results.all { "\"error\"" in it })
        assertNull(h.memory.notes[Persona.PERMALINK])
    }
}

private fun String.countOf(part: String): Int {
    var count = 0
    var index = indexOf(part)
    while (index >= 0) {
        count++
        index = indexOf(part, index + part.length)
    }
    return count
}
