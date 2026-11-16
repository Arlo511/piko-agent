package dev.xiaoman.agent

import java.util.UUID

/** Which write a pending draft belongs to; a delete draft cannot be used to commit a write. */
internal enum class SkillDraftOperation { CREATE, UPDATE, DELETE }

internal data class PendingSkillDraft(
    val id: String,
    val name: String,
    /** Full `SKILL.md` text; null for DELETE. */
    val markdown: String?,
    val operation: SkillDraftOperation,
    /** The user turn that produced the draft; it can only be committed from a later turn. */
    val createdTaskId: String,
    val createdAt: Long,
)

internal sealed class SkillDraftLookup {
    data class Ok(val draft: PendingSkillDraft) : SkillDraftLookup()
    data object Missing : SkillDraftLookup()
    data object Expired : SkillDraftLookup()
    data object SameTurn : SkillDraftLookup()
}

/**
 * Drafts waiting for the user's confirmation. A draft is only committable from a later user turn
 * ([createdTaskId] differs), so the model cannot confirm its own proposal inside one exchange.
 * Expired drafts are dropped by TTL; the oldest is dropped when [capacity] is reached.
 */
internal class PendingSkillDrafts(
    private val capacity: Int = 8,
    private val ttlMillis: Long = TTL_MILLIS,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val drafts = LinkedHashMap<String, PendingSkillDraft>()

    @Synchronized
    fun put(name: String, markdown: String?, operation: SkillDraftOperation, taskId: String, now: Long): PendingSkillDraft {
        expire(now)
        drafts.values.firstOrNull { it.name == name && it.operation == operation }?.let { drafts.remove(it.id) }
        while (drafts.size >= capacity) drafts.remove(drafts.keys.first())
        val draft = PendingSkillDraft(idFactory(), name, markdown, operation, taskId, now)
        drafts[draft.id] = draft
        return draft
    }

    @Synchronized
    fun take(id: String, taskId: String, now: Long): SkillDraftLookup {
        val draft = drafts[id] ?: run {
            expire(now)
            return SkillDraftLookup.Missing
        }
        if (now - draft.createdAt > ttlMillis) {
            drafts.remove(id)
            return SkillDraftLookup.Expired
        }
        if (draft.createdTaskId == taskId) return SkillDraftLookup.SameTurn
        drafts.remove(id)
        return SkillDraftLookup.Ok(draft)
    }

    /**
     * Finds the pending draft for [name] + [operation] when the model omits the id.
     * Only a draft from an earlier turn can be committed, so the user-confirmation gate stays.
     */
    @Synchronized
    fun candidate(name: String, operation: SkillDraftOperation, taskId: String, now: Long): SkillDraftLookup {
        val draft = drafts.values.lastOrNull { it.name == name && it.operation == operation }
        if (draft == null) {
            expire(now)
            return SkillDraftLookup.Missing
        }
        if (now - draft.createdAt > ttlMillis) {
            drafts.remove(draft.id)
            return SkillDraftLookup.Expired
        }
        if (draft.createdTaskId == taskId) return SkillDraftLookup.SameTurn
        drafts.remove(draft.id)
        return SkillDraftLookup.Ok(draft)
    }

    private fun expire(now: Long) {
        drafts.entries.removeAll { now - it.value.createdAt > ttlMillis }
    }

    companion object {
        const val TTL_MILLIS: Long = 10 * 60 * 1000
    }
}
