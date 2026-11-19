package dev.piko.agent

import dev.piko.api.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Tool definitions offered to the chat model. */
object AgentTools {
    const val MEMORY_SEARCH = "memory_search"
    const val MEMORY_READ = "memory_read"
    const val MEMORY_WRITE = "memory_write"
    const val MEMORY_FORGET = "memory_forget"
    const val PERSONA_SET = "persona_set"
    const val LIST_SKILLS = "list_skills"
    const val CREATE_SKILL = "create_skill"
    const val UPDATE_SKILL = "update_skill"
    const val DELETE_SKILL = "delete_skill"
    const val REMINDER_SET = "reminder_set"
    const val PHONE_TASK = "phone_task"

    val specs: List<ToolSpec> = listOf(
        ToolSpec(
            MEMORY_SEARCH, "在用户的记忆笔记中全文搜索（支持中文）。",
            schema(listOf("query"), "query" to str("搜索词"), "limit" to int("最多返回条数，默认 5")),
        ),
        ToolSpec(
            MEMORY_READ, "读取一条记忆笔记的全文。",
            schema(listOf("identifier"), "identifier" to str("笔记的 permalink（如 places/家）或标题")),
        ),
        ToolSpec(
            MEMORY_WRITE,
            "新建或更新一条记忆笔记；已有同名笔记时追加观察和关系（相同内容不会重复）。",
            schema(
                listOf("title", "observations"),
                "title" to str("笔记标题，如 家"),
                "permalink" to str("可选，路径形式的唯一键，如 places/家；默认用标题"),
                "type" to str("可选，笔记类型，如 place、person、preference；默认 note"),
                "observations" to arr(
                    "观察条目",
                    schema(
                        listOf("category", "content"),
                        "category" to str("类别，如 address、preference"),
                        "content" to str("内容"),
                        "tags" to arr("标签，不带 #", str("标签")),
                        "context" to str("可选，来源或补充说明"),
                    ),
                ),
                "relations" to arr(
                    "可选，与其他笔记的关系",
                    schema(listOf("type", "target"), "type" to str("关系类型，如 lives_in"), "target" to str("目标笔记标题")),
                ),
            ),
        ),
        ToolSpec(
            MEMORY_FORGET, "删除一条记忆笔记（真正删除文件）。仅在用户明确要求忘记时使用。",
            schema(listOf("identifier"), "identifier" to str("笔记的 permalink 或标题")),
        ),
        ToolSpec(
            PERSONA_SET,
            "设定或修改小满的性格（写入 persona 记忆笔记）。用户明确要求改变说话风格、人设或性格细节时使用；先复述将写入的基调和细节，用户明确同意后再调用。只影响语气、详略和引导方式，不改变工具调用和安全规则。用户同时说明职业或专业领域时，先用 memory_write 写入个人档案（permalink=profile，type=profile，类别 identity），再调用本工具。",
            schema(
                listOf("tone"),
                "tone" to str("性格基调，一句话，如 知无不言的亲密导师"),
                "details" to arr("性格细节，每条一个规则，如 设计类问题直接给专业结论", str("细节")),
            ),
        ),
        ToolSpec(LIST_SKILLS, "列出所有可用的手机操作技能。", schema(emptyList())),
        ToolSpec(
            CREATE_SKILL,
            "新建用户技能（写 SKILL.md）。第一次调用只生成草稿，把返回的 draft 原样给用户看并等确认；用户下一条消息明确同意后，再次调用本工具即可提交，draftId 可省略（会复用上一轮草稿），不要编造 draftId。",
            schema(
                listOf("name", "markdown"),
                "name" to str("技能名，只能用 a-z、0-9 和 -，如 taxi-home"),
                "markdown" to str("完整的 SKILL.md 内容，含 frontmatter 和正文；提交时必须与用户确认过的草稿完全一致"),
                "draftId" to str("可选。省略即复用上一轮同 name 的草稿；如填写必须是上一轮返回的原值，不要编造"),
            ),
        ),
        ToolSpec(
            UPDATE_SKILL,
            "修改用户技能（整份 SKILL.md 替换）。同样两段式：先出草稿等用户确认，用户同意后再次调用即可提交（draftId 可省略）。只存在于内置技能时会创建用户覆盖副本。",
            schema(
                listOf("name", "markdown"),
                "name" to str("要修改的技能名"),
                "markdown" to str("替换后的完整 SKILL.md 内容；提交时必须与用户确认过的草稿完全一致"),
                "draftId" to str("可选。省略即复用上一轮同 name 的草稿；如填写必须是上一轮返回的原值，不要编造"),
            ),
        ),
        ToolSpec(
            DELETE_SKILL,
            "删除用户技能。两段式：先返回将删除的技能名等用户确认，用户同意后再次调用即可删除（draftId 可省略）。内置技能不能删除。",
            schema(
                listOf("name"),
                "name" to str("要删除的技能名"),
                "draftId" to str("可选。省略即复用上一轮同 name 的删除确认；如填写必须是上一轮返回的原值，不要编造"),
            ),
        ),
        ToolSpec(
            REMINDER_SET,
            "设置一条定时提醒：到点后小满会在手机上弹出通知。",
            schema(
                listOf("text", "at"),
                "text" to str("提醒内容，如 喝水、带伞"),
                "at" to str("触发时间，ISO-8601 本地时间（设备时区，不带时区后缀），如 2026-11-03T08:00"),
            ),
        ),
        ToolSpec(
            PHONE_TASK, "在用户的手机上完成一个操作任务，返回结果摘要。",
            schema(
                listOf("goal"),
                "goal" to str("具体目标，包含关键参数，如 用高德打车回家（北京市海淀区某某路 1 号），选快车"),
                "skill" to str("可选，使用的技能名"),
            ),
        ),
    )

    private fun schema(required: List<String>, vararg props: Pair<String, JsonObject>): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
        putJsonArray("required") { required.forEach { add(it) } }
    }

    private fun str(description: String) = buildJsonObject { put("type", "string"); put("description", description) }
    private fun int(description: String) = buildJsonObject { put("type", "integer"); put("description", description) }
    private fun arr(description: String, items: JsonObject) = buildJsonObject {
        put("type", "array"); put("description", description); put("items", items)
    }
}
