package dev.xiaoman.agent

import dev.xiaoman.api.AgentEvent
import dev.xiaoman.api.MemoryStore
import dev.xiaoman.api.Note
import dev.xiaoman.api.Observation
import dev.xiaoman.api.Relation
import dev.xiaoman.api.ReminderScheduler
import dev.xiaoman.api.Skill
import dev.xiaoman.api.SkillDeleteResult
import dev.xiaoman.api.SkillDraft
import dev.xiaoman.api.SkillProblem
import dev.xiaoman.api.SkillRepository
import dev.xiaoman.api.SkillWriteResult
import dev.xiaoman.api.SkillWriter
import dev.xiaoman.api.ToolCall
import dev.xiaoman.api.ToolProvider
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal class ToolArgumentException(message: String) : Exception(message)

/** Runs the chat model's tool calls. Argument and runtime errors become `{"error": ...}` tool results. */
internal class ChatTools(
    private val memory: MemoryStore,
    private val skills: SkillRepository,
    private val clock: () -> Long,
    private val reminders: ReminderScheduler,
    private val external: ToolProvider?,
    private val skillWriter: SkillWriter? = null,
    private val skillDrafts: PendingSkillDrafts = PendingSkillDrafts(),
    private val phoneTasks: Boolean = true,
    private val phoneTask: suspend (goal: String, skill: String?) -> AgentEvent.PhoneTaskFinished,
) {
    suspend fun execute(call: ToolCall, taskId: String, emit: (AgentEvent) -> Unit): String = try {
        val args = parseArgs(call.argumentsJson)
        when (call.name) {
            AgentTools.MEMORY_SEARCH -> search(args)
            AgentTools.MEMORY_READ -> read(args)
            AgentTools.MEMORY_WRITE -> write(args, taskId, emit)
            AgentTools.MEMORY_FORGET -> forget(args, taskId, emit)
            AgentTools.PERSONA_SET -> setPersona(args, taskId, emit)
            AgentTools.LIST_SKILLS -> listSkills()
            AgentTools.CREATE_SKILL -> createSkill(args, taskId, emit)
            AgentTools.UPDATE_SKILL -> updateSkill(args, taskId, emit)
            AgentTools.DELETE_SKILL -> deleteSkill(args, taskId, emit)
            AgentTools.REMINDER_SET -> reminder(args)
            AgentTools.PHONE_TASK -> if (phoneTasks) phone(args) else error("这里不能操作手机，请用其他工具或告诉用户自己操作")
            else -> external(call, taskId, emit)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: ToolArgumentException) {
        error("参数错误：${e.message}")
    } catch (e: Exception) {
        error("工具执行失败：${e.message ?: e::class.simpleName}")
    }

    private fun error(message: String) = buildJsonObject { put("error", message) }.toString()

    private fun parseArgs(raw: String): JsonObject {
        if (raw.isBlank()) return JsonObject(emptyMap())
        return try {
            JsonText.json.parseToJsonElement(raw) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: throw ToolArgumentException("arguments 不是合法的 JSON 对象")
    }

    private fun required(o: JsonObject, key: String): String =
        JsonText.string(o, key) ?: throw ToolArgumentException("缺少 $key")

    private fun optional(o: JsonObject, key: String): String? =
        JsonText.string(o, key)?.trim()?.takeIf { it.isNotEmpty() }

    private suspend fun search(args: JsonObject): String {
        val query = required(args, "query")
        val limit = ((args["limit"] as? JsonPrimitive)?.intOrNull ?: 5).coerceIn(1, 20)
        val hits = memory.search(query, limit)
        return buildJsonObject {
            putJsonArray("hits") {
                hits.forEach { h -> addJsonObject { put("permalink", h.permalink); put("title", h.title); put("snippet", h.snippet) } }
            }
        }.toString()
    }

    private suspend fun read(args: JsonObject): String {
        val note = memory.read(required(args, "identifier")) ?: return buildJsonObject { put("found", false) }.toString()
        return buildJsonObject {
            put("found", true)
            put("title", note.title)
            put("permalink", note.permalink)
            put("type", note.type)
            putJsonArray("tags") { note.tags.forEach { add(it) } }
            putJsonArray("observations") { note.observations.forEach { add(NoteText.observationLine(it)) } }
            putJsonArray("relations") { note.relations.forEach { add(NoteText.relationLine(it)) } }
            put("body", note.body)
        }.toString()
    }

    private suspend fun write(args: JsonObject, taskId: String, emit: (AgentEvent) -> Unit): String {
        val title = required(args, "title")
        val observations = objects(args, "observations").map {
            Observation(required(it, "category"), required(it, "content"), JsonText.stringList(it, "tags"), JsonText.string(it, "context"))
        }
        val relations = objects(args, "relations").map {
            Relation(required(it, "type"), required(it, "target"), JsonText.string(it, "context"))
        }
        if (observations.isEmpty() && relations.isEmpty()) throw ToolArgumentException("observations 不能为空")

        val existing = memory.read(JsonText.string(args, "permalink") ?: title)
        val permalink = JsonText.string(args, "permalink") ?: existing?.permalink ?: title
        val mergedObs = ((existing?.observations ?: emptyList()) + observations).distinctBy { it.category to it.content }
        val mergedRels = ((existing?.relations ?: emptyList()) + relations).distinctBy { it.type to it.target }
        val note = Note(
            title = title,
            permalink = permalink,
            type = JsonText.string(args, "type") ?: existing?.type ?: "note",
            tags = existing?.tags ?: emptyList(),
            extraFrontmatter = existing?.extraFrontmatter ?: emptyMap(),
            body = NoteText.body(title, mergedObs, mergedRels, existing?.body?.let { NoteText.preamble(it) } ?: ""),
            observations = mergedObs,
            relations = mergedRels,
            updatedAtMillis = clock(),
        )
        val stored = memory.write(note)
        emit(AgentEvent.MemoryUpdated(taskId, stored.permalink))
        return buildJsonObject {
            put("ok", true)
            put("permalink", stored.permalink)
            put("created", existing == null)
            put("observations", mergedObs.size)
        }.toString()
    }

    private fun objects(args: JsonObject, key: String): List<JsonObject> = when (val e = args[key]) {
        null -> emptyList()
        is JsonArray -> e.map { it as? JsonObject ?: throw ToolArgumentException("$key 的每一项必须是对象") }
        is JsonObject -> listOf(e)
        else -> if (e is JsonPrimitive && e.content == "null") emptyList() else throw ToolArgumentException("$key 必须是数组")
    }

    private suspend fun forget(args: JsonObject, taskId: String, emit: (AgentEvent) -> Unit): String {
        val identifier = required(args, "identifier")
        val permalink = memory.read(identifier)?.permalink ?: identifier
        val deleted = memory.delete(identifier)
        if (deleted) emit(AgentEvent.MemoryUpdated(taskId, permalink))
        return buildJsonObject { put("deleted", deleted) }.toString()
    }

    private suspend fun setPersona(args: JsonObject, taskId: String, emit: (AgentEvent) -> Unit): String {
        val tone = required(args, "tone").trim()
        val details = JsonText.stringList(args, "details").map { it.trim() }.filter { it.isNotEmpty() }
        Persona.problem(tone, details)?.let { throw ToolArgumentException(it) }
        guardPersonaText(tone + "\n" + details.joinToString("\n"))
        val stored = memory.write(Persona.note(tone, details, memory.read(Persona.PERMALINK)))
        emit(AgentEvent.MemoryUpdated(taskId, stored.permalink))
        return buildJsonObject {
            put("ok", true)
            put("permalink", stored.permalink)
            put("tone", Persona.tone(stored))
            putJsonArray("details") { Persona.details(stored).forEach { add(it) } }
        }.toString()
    }

    private suspend fun listSkills(): String {
        val list = skills.list()
        val builtIn = list.associate { it.name to (skillWriter?.isBuiltIn(it.name) ?: false) }
        return listSkillsJson(list, builtIn)
    }

    private fun listSkillsJson(list: List<Skill>, builtIn: Map<String, Boolean>): String = buildJsonObject {
        putJsonArray("skills") {
            list.forEach { s ->
                addJsonObject {
                    put("name", s.name)
                    put("description", s.description)
                    put("channel", s.channel.wire)
                    put("apps", buildJsonArray { s.apps.forEach { add(it) } })
                    put("hasFlow", s.flow != null)
                    put("builtIn", builtIn[s.name] ?: false)
                }
            }
        }
    }.toString()

    private suspend fun createSkill(args: JsonObject, taskId: String, emit: (AgentEvent) -> Unit): String {
        val writer = skillWriter ?: return error("当前环境不支持修改技能，只能在手机上操作")
        val name = skillName(args)
        val markdown = required(args, "markdown")
        guardSkillText(markdown)
        val draftId = optional(args, "draftId")
        if (draftId != null) {
            val lookup = skillDrafts.take(draftId, taskId, clock())
            if (lookup !is SkillDraftLookup.Missing) {
                val draft = committedDraft(lookup, SkillDraftOperation.CREATE) ?: return lookupError(lookup)
                return writeResult(writer.create(SkillDraft(draft.name, draft.markdown.orEmpty())), taskId, emit)
            }
            // A made-up id is common: fall through and reuse the real pending draft for this name.
        }
        when (val pending = skillDrafts.candidate(name, SkillDraftOperation.CREATE, taskId, clock())) {
            is SkillDraftLookup.Ok -> {
                if (pending.draft.markdown != markdown) {
                    return draftJson(skillDrafts.put(name, markdown, SkillDraftOperation.CREATE, taskId, clock()), markdown)
                }
                return writeResult(writer.create(SkillDraft(name, markdown)), taskId, emit)
            }
            SkillDraftLookup.SameTurn -> return error(SAME_TURN_HINT)
            SkillDraftLookup.Expired -> return error("上一轮草稿已过期（超过 10 分钟），请重新展示草稿并请用户再确认一次")
            SkillDraftLookup.Missing -> if (draftId != null) {
                return error("找不到这个 draftId 对应的草稿。不要编造 draftId：直接重新调用 create_skill（同 name、同内容）生成草稿，或省略 draftId 提交上一轮草稿")
            }
        }
        if (writer.userSkillExists(name)) return error("同名技能已存在：$name，请用 update_skill 修改")
        val problems = writer.validate(name, markdown)
        if (problems.isNotEmpty()) return invalid(problems)
        return draftJson(skillDrafts.put(name, markdown, SkillDraftOperation.CREATE, taskId, clock()), markdown)
    }

    private suspend fun updateSkill(args: JsonObject, taskId: String, emit: (AgentEvent) -> Unit): String {
        val writer = skillWriter ?: return error("当前环境不支持修改技能，只能在手机上操作")
        val name = skillName(args)
        val markdown = required(args, "markdown")
        guardSkillText(markdown)
        val draftId = optional(args, "draftId")
        if (draftId != null) {
            val lookup = skillDrafts.take(draftId, taskId, clock())
            if (lookup !is SkillDraftLookup.Missing) {
                val draft = committedDraft(lookup, SkillDraftOperation.UPDATE) ?: return lookupError(lookup)
                return writeResult(writer.update(draft.name, draft.markdown.orEmpty()), taskId, emit)
            }
        }
        when (val pending = skillDrafts.candidate(name, SkillDraftOperation.UPDATE, taskId, clock())) {
            is SkillDraftLookup.Ok -> {
                if (pending.draft.markdown != markdown) {
                    return draftJson(skillDrafts.put(name, markdown, SkillDraftOperation.UPDATE, taskId, clock()), markdown)
                }
                return writeResult(writer.update(name, markdown), taskId, emit)
            }
            SkillDraftLookup.SameTurn -> return error(SAME_TURN_HINT)
            SkillDraftLookup.Expired -> return error("上一轮草稿已过期（超过 10 分钟），请重新展示草稿并请用户再确认一次")
            SkillDraftLookup.Missing -> if (draftId != null) {
                return error("找不到这个 draftId 对应的草稿。不要编造 draftId：直接重新调用 update_skill（同 name、同内容），或省略 draftId 提交上一轮草稿")
            }
        }
        val exists = writer.userSkillExists(name)
        val builtIn = writer.isBuiltIn(name)
        if (!exists && !builtIn) return error("技能不存在：$name")
        val problems = writer.validate(name, markdown)
        if (problems.isNotEmpty()) return invalid(problems)
        return draftJson(
            skillDrafts.put(name, markdown, SkillDraftOperation.UPDATE, taskId, clock()),
            markdown,
            overridesBuiltIn = builtIn,
        )
    }

    private suspend fun deleteSkill(args: JsonObject, taskId: String, emit: (AgentEvent) -> Unit): String {
        val writer = skillWriter ?: return error("当前环境不支持修改技能，只能在手机上操作")
        val name = skillName(args)
        val draftId = optional(args, "draftId")
        if (draftId != null) {
            val lookup = skillDrafts.take(draftId, taskId, clock())
            if (lookup !is SkillDraftLookup.Missing) {
                val draft = committedDraft(lookup, SkillDraftOperation.DELETE) ?: return lookupError(lookup)
                if (draft.name != name) return error("draftId 属于技能 ${draft.name}，与 name=$name 不一致")
                return deleteResult(writer.delete(name), taskId, emit)
            }
        }
        when (val pending = skillDrafts.candidate(name, SkillDraftOperation.DELETE, taskId, clock())) {
            is SkillDraftLookup.Ok -> return deleteResult(writer.delete(pending.draft.name), taskId, emit)
            SkillDraftLookup.SameTurn -> return error(SAME_TURN_HINT)
            SkillDraftLookup.Expired -> return error("上一轮草稿已过期（超过 10 分钟），请重新告知用户并等确认")
            SkillDraftLookup.Missing -> if (draftId != null) {
                return error("找不到这个 draftId 对应的草稿。不要编造 draftId：省略 draftId 直接重新调用 delete_skill")
            }
        }
        if (!writer.userSkillExists(name)) {
            return error(if (writer.isBuiltIn(name)) "内置技能不可删除：$name" else "技能不存在：$name")
        }
        return deleteDraftJson(skillDrafts.put(name, null, SkillDraftOperation.DELETE, taskId, clock()))
    }

    private suspend fun deleteResult(result: SkillDeleteResult, taskId: String, emit: (AgentEvent) -> Unit): String = when (result) {
        is SkillDeleteResult.Ok -> {
            emit(AgentEvent.SkillsUpdated(taskId, result.name))
            buildJsonObject { put("ok", true); put("name", result.name); put("deleted", true) }.toString()
        }
        is SkillDeleteResult.Rejected -> error(result.reason)
    }

    private fun committedDraft(lookup: SkillDraftLookup, expected: SkillDraftOperation): PendingSkillDraft? =
        (lookup as? SkillDraftLookup.Ok)?.draft?.takeIf { it.operation == expected }

    private fun lookupError(lookup: SkillDraftLookup): String = when (lookup) {
        SkillDraftLookup.Missing -> error("draftId 不存在或已提交过。不要编造 draftId：省略 draftId 直接再调用一次即可复用上一轮草稿")
        SkillDraftLookup.Expired -> error("草稿已过期（超过 10 分钟），请重新生成")
        SkillDraftLookup.SameTurn -> error("必须先让用户看到草稿并在下一条消息里明确确认，之后才能提交")
        is SkillDraftLookup.Ok -> error("这个 draftId 不属于当前操作，请重新生成草稿")
    }

    private suspend fun writeResult(result: SkillWriteResult, taskId: String, emit: (AgentEvent) -> Unit): String = when (result) {
        is SkillWriteResult.Ok -> {
            emit(AgentEvent.SkillsUpdated(taskId, result.name))
            buildJsonObject {
                put("ok", true)
                put("name", result.name)
                put("created", result.created)
                put("overridesBuiltIn", result.overridesBuiltIn)
            }.toString()
        }
        is SkillWriteResult.Invalid -> invalid(result.problems)
        is SkillWriteResult.Rejected -> error(result.reason)
    }

    private fun invalid(problems: List<SkillProblem>): String = buildJsonObject {
        put("error", "技能格式不合格，未写入")
        putJsonArray("problems") {
            problems.forEach { p ->
                addJsonObject {
                    put("source", p.source)
                    put("message", p.message)
                    p.line?.let { put("line", it) }
                }
            }
        }
    }.toString()

    private fun draftJson(draft: PendingSkillDraft, markdown: String, overridesBuiltIn: Boolean = false): String = buildJsonObject {
        put("needsConfirmation", true)
        put("draftId", draft.id)
        put("name", draft.name)
        put("draft", markdown)
        put("overridesBuiltIn", overridesBuiltIn)
        put("note", "把 draft 原样贴给用户并询问是否确认；用户下一轮同意后再次调用本工具，draftId 可省略（会复用这份草稿），也可填 draftId=$($draft.id)；不要编造 draftId")
    }.toString()

    private fun deleteDraftJson(draft: PendingSkillDraft): String = buildJsonObject {
        put("needsConfirmation", true)
        put("draftId", draft.id)
        put("name", draft.name)
        put("operation", "delete")
        put("note", "先告诉用户将删除哪个技能并等确认；用户下一轮同意后再次调用本工具，draftId 可省略，也可填 draftId=$($draft.id)；不要编造 draftId")
    }.toString()

    private fun skillName(args: JsonObject): String {
        val name = required(args, "name").trim()
        if (!SKILL_NAME.matches(name)) throw ToolArgumentException("name 只能包含小写字母、数字和 -，长度 1–40，例如 taxi-home")
        return name
    }

    /** Rejects secrets and forbidden paths before anything is written; only the category is reported. */
    private fun guardSkillText(markdown: String) {
        for ((label, pattern) in SENSITIVE_PATTERNS) {
            if (pattern.containsMatchIn(markdown)) {
                throw ToolArgumentException("内容疑似包含敏感信息（$label），已拒绝写入，请删除后重试")
            }
        }
        if (TRAVERSAL_PATTERN.containsMatchIn(markdown)) {
            throw ToolArgumentException("内容包含不允许的路径（skills/examples、bundled-skills 或 ..），已拒绝写入")
        }
    }

    /** Rejects secrets in personality text; only the category is reported. */
    private fun guardPersonaText(text: String) {
        for ((label, pattern) in SENSITIVE_PATTERNS) {
            if (pattern.containsMatchIn(text)) {
                throw ToolArgumentException("性格设定疑似包含敏感信息（$label），已拒绝写入，请删除后重试")
            }
        }
    }

    private fun reminder(args: JsonObject): String {
        val text = required(args, "text")
        val raw = required(args, "at")
        val atMillis = ReminderTimes.parseAtMillis(raw)
            ?: throw ToolArgumentException("at 不是合法的 ISO-8601 本地时间，应为 2026-11-03T08:00 这样的形式")
        if (atMillis <= clock()) throw ToolArgumentException("提醒时间 $raw 已经过去，请向用户确认一个将来的时间")
        reminders.schedule(text, atMillis)
        return buildJsonObject {
            put("ok", true)
            put("text", text)
            put("at", raw)
        }.toString()
    }

    private suspend fun external(call: ToolCall, taskId: String, emit: (AgentEvent) -> Unit): String {
        val result = external?.call(taskId, call.name, call.argumentsJson) ?: return error("未知工具 ${call.name}")
        emit(AgentEvent.ToolCalled(taskId, call.name, result.ok, result.summary))
        return result.text
    }

    private suspend fun phone(args: JsonObject): String {
        val finished = phoneTask(required(args, "goal"), JsonText.string(args, "skill"))
        return buildJsonObject {
            put("success", finished.success)
            put("summary", finished.summary)
            put("steps", finished.steps)
        }.toString()
    }

    private companion object {
        const val SAME_TURN_HINT = "草稿刚生成，必须先让用户看到并在下一条消息确认；确认后再次调用本工具即可，draftId 可以省略"

        val SKILL_NAME = Regex("^[a-z0-9-]{1,40}$")
        val TRAVERSAL_PATTERN = Regex("(?i)(skills/examples|bundled-skills|\\.\\./)")

        /** Only the category is reported to the model, never the matched value. */
        val SENSITIVE_PATTERNS: List<Pair<String, Regex>> = listOf(
            "手机号" to Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)"),
            "sk- 开头的 Key" to Regex("sk-[A-Za-z0-9_-]{16,}"),
            "AWS Access Key" to Regex("AKIA[0-9A-Z]{16}"),
            "GitHub Token" to Regex("ghp_[A-Za-z0-9]{20,}"),
            "Bearer Token" to Regex("(?i)bearer\\s+[A-Za-z0-9._-]{10,}"),
            "密码" to Regex("(?i)(密码|passwd|password)\\s*[:：]\\s*\\S"),
            "api_key" to Regex("(?i)api[_-]?key\\s*[:：]\\s*\\S"),
            "secret" to Regex("(?i)secret\\s*[:：]\\s*\\S"),
            "token" to Regex("(?i)token\\s*[:：]\\s*\\S"),
        )
    }
}
