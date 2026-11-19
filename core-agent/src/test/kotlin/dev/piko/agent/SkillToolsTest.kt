package dev.piko.agent

import dev.piko.api.AgentEvent
import dev.piko.api.ToolCall
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkillToolsTest {
    private val markdown = "---\nname: tea-note\ndescription: 测试技能\n---\n按步骤操作\n"

    private fun createArgs(name: String = "tea-note", draftId: String? = null, body: String = markdown): String {
        val fields = mutableListOf("\"name\":\"$name\"", "\"markdown\":${JsonPrimitive(body)}")
        if (draftId != null) fields += "\"draftId\":\"$draftId\""
        return "{${fields.joinToString(",")}}"
    }

    private fun Harness.toolResult(): String = chat.requests.last().messages.last { it.toolCallId != null }.text

    private suspend fun Harness.turn(text: String) = agent.handle(text, events, stop)

    @Test
    fun createNeedsConfirmationAndCommitsOnNextTurn() = runTest {
        val writer = FakeSkillWriter()
        val h = Harness(skillWriter = writer)

        h.chat.tools(ToolCall("c1", AgentTools.CREATE_SKILL, createArgs())).reply("草稿如下，确认吗")
        h.turn("帮我建一个泡茶技能")
        assertTrue(writer.created.isEmpty(), "first call must not write")
        assertTrue(h.toolResult().contains("needsConfirmation"))
        assertTrue(h.toolResult().contains("draft-1"))

        h.chat.tools(ToolCall("c2", AgentTools.CREATE_SKILL, createArgs(draftId = "draft-1"))).reply("已写入")
        h.turn("确认")
        assertTrue(writer.created.size == 1, "second turn must write once")
        assertTrue(writer.created.first().name == "tea-note")
        assertTrue(h.events.all.any { it is AgentEvent.SkillsUpdated })
    }

    @Test
    fun commitInSameTurnIsRejected() = runTest {
        val writer = FakeSkillWriter()
        val h = Harness(skillWriter = writer)

        h.chat
            .tools(ToolCall("c1", AgentTools.CREATE_SKILL, createArgs()))
            .tools(ToolCall("c2", AgentTools.CREATE_SKILL, createArgs(draftId = "draft-1")))
            .reply("好了")
        h.turn("帮我建一个泡茶技能")

        assertTrue(writer.created.isEmpty())
        assertTrue(h.toolResult().contains("下一条消息"))
    }

    @Test
    fun expiredDraftIsRejected() = runTest {
        var now = 1_000L
        val writer = FakeSkillWriter()
        val h = Harness(skillWriter = writer, clock = { now })

        h.chat.tools(ToolCall("c1", AgentTools.CREATE_SKILL, createArgs())).reply("草稿如下")
        h.turn("帮我建一个泡茶技能")
        now += PendingSkillDrafts.TTL_MILLIS + 1

        h.chat.tools(ToolCall("c2", AgentTools.CREATE_SKILL, createArgs(draftId = "draft-1"))).reply("过期了")
        h.turn("确认")

        assertTrue(writer.created.isEmpty())
        assertTrue(h.toolResult().contains("过期"))
    }

    @Test
    fun sensitiveContentIsRejectedWithoutEchoingIt() = runTest {
        val writer = FakeSkillWriter()
        val h = Harness(skillWriter = writer)
        val secret = "sk-" + "a".repeat(24)
        val body = "---\nname: tea-note\ndescription: 测试技能\n---\n说明 $secret\n"

        h.chat.tools(ToolCall("c1", AgentTools.CREATE_SKILL, createArgs(body = body))).reply("不能写")
        h.turn("把这段写进技能")

        assertTrue(writer.created.isEmpty())
        val result = h.toolResult()
        assertTrue(result.contains("敏感信息"))
        assertFalse(result.contains(secret), "tool result must not echo the secret")
    }

    @Test
    fun builtInSkillCannotBeDeleted() = runTest {
        val writer = FakeSkillWriter(builtInSkills = setOf("taxi-home"))
        val h = Harness(skillWriter = writer)

        h.chat.tools(ToolCall("c1", AgentTools.DELETE_SKILL, "{\"name\":\"taxi-home\"}")).reply("不能删")
        h.turn("删掉打车技能")

        assertTrue(writer.deleted.isEmpty())
        assertTrue(h.toolResult().contains("内置技能不可删除"))
    }

    @Test
    fun deleteNeedsConfirmationAndCommitsOnNextTurn() = runTest {
        val writer = FakeSkillWriter(userSkills = mutableSetOf("tea-note"))
        val h = Harness(skillWriter = writer)

        h.chat.tools(ToolCall("c1", AgentTools.DELETE_SKILL, "{\"name\":\"tea-note\"}")).reply("要删掉 tea-note 吗")
        h.turn("删掉泡茶技能")
        assertTrue(writer.deleted.isEmpty())
        assertTrue(h.toolResult().contains("needsConfirmation"))

        h.chat.tools(ToolCall("c2", AgentTools.DELETE_SKILL, "{\"name\":\"tea-note\",\"draftId\":\"draft-1\"}")).reply("已删除")
        h.turn("确认")

        assertTrue(writer.deleted == listOf("tea-note"))
        assertTrue(h.events.all.any { it is AgentEvent.SkillsUpdated })
    }

    @Test
    fun confirmWithoutDraftIdCommitsPendingDraftOnce() = runTest {
        val writer = FakeSkillWriter()
        val h = Harness(skillWriter = writer)

        h.chat.tools(ToolCall("c1", AgentTools.CREATE_SKILL, createArgs())).reply("草稿如下，确认吗")
        h.turn("帮我建一个泡茶技能")
        assertTrue(writer.created.isEmpty())

        // The user says 确认; the model repeats the call without draftId (it cannot see last turn's tool result).
        h.chat.tools(ToolCall("c2", AgentTools.CREATE_SKILL, createArgs())).reply("已写入")
        h.turn("确认")

        assertTrue(writer.created.size == 1, "confirmation must commit exactly once")
        assertTrue(h.toolResult().contains("\"ok\":true"))
    }

    @Test
    fun fabricatedDraftIdFallsBackToPendingDraft() = runTest {
        val writer = FakeSkillWriter()
        val h = Harness(skillWriter = writer)

        h.chat.tools(ToolCall("c1", AgentTools.CREATE_SKILL, createArgs())).reply("草稿如下")
        h.turn("帮我建一个泡茶技能")

        h.chat.tools(ToolCall("c2", AgentTools.CREATE_SKILL, createArgs(draftId = "made-up-uuid"))).reply("已写入")
        h.turn("确认")

        assertTrue(writer.created.size == 1)
        assertTrue(h.toolResult().contains("\"ok\":true"))
    }

    @Test
    fun changedDraftOnConfirmAsksAgainInsteadOfWriting() = runTest {
        val writer = FakeSkillWriter()
        val h = Harness(skillWriter = writer)

        h.chat.tools(ToolCall("c1", AgentTools.CREATE_SKILL, createArgs())).reply("草稿如下")
        h.turn("帮我建一个泡茶技能")

        val changed = markdown + "\n额外一行\n"
        h.chat.tools(ToolCall("c2", AgentTools.CREATE_SKILL, createArgs(body = changed))).reply("内容变了")
        h.turn("确认")

        assertTrue(writer.created.isEmpty(), "content the user never saw must not be written")
        assertTrue(h.toolResult().contains("needsConfirmation"))
        assertTrue(h.toolResult().contains("draft-2"))
    }

    @Test
    fun deleteWithoutDraftIdCommitsNextTurn() = runTest {
        val writer = FakeSkillWriter(userSkills = mutableSetOf("tea-note"))
        val h = Harness(skillWriter = writer)

        h.chat.tools(ToolCall("c1", AgentTools.DELETE_SKILL, "{\"name\":\"tea-note\"}")).reply("要删掉 tea-note 吗")
        h.turn("删掉泡茶技能")
        assertTrue(writer.deleted.isEmpty())

        h.chat.tools(ToolCall("c2", AgentTools.DELETE_SKILL, "{\"name\":\"tea-note\"}")).reply("已删除")
        h.turn("确认")

        assertTrue(writer.deleted == listOf("tea-note"))
    }

    @Test
    fun listSkillsMarksBuiltIn() = runTest {
        val writer = FakeSkillWriter(builtInSkills = setOf("taxi-home"))
        val h = Harness(skills = FakeSkills(skill("taxi-home")), skillWriter = writer)

        h.chat.tools(ToolCall("c1", AgentTools.LIST_SKILLS, "{}")).reply("列好了")
        h.turn("有哪些技能")

        assertTrue(h.toolResult().contains("\"builtIn\":true"))
    }

    @Test
    fun skillToolsAreNotOfferedWithoutWriter() = runTest {
        val h = Harness(skillWriter = null)

        h.chat.reply("你好")
        h.turn("你好")

        val names = h.chat.requests.first().tools.map { it.name }
        assertFalse(AgentTools.CREATE_SKILL in names)
        assertFalse(AgentTools.UPDATE_SKILL in names)
        assertFalse(AgentTools.DELETE_SKILL in names)
        assertTrue(AgentTools.LIST_SKILLS in names)
    }
}
