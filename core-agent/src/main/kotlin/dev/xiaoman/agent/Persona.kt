package dev.xiaoman.agent

import dev.xiaoman.api.Note
import dev.xiaoman.api.Observation

/**
 * The user-configured personality, stored as a Basic Memory note (`persona`) plus an optional
 * user profile note (`profile`). Both are read on every chat turn by [XiaomanAgent.systemPrompt].
 *
 * A persona note holds `[tone]` (one line) and `[detail]` observations; the profile note holds
 * `[identity]` next to the user's other observations.
 */
object Persona {
    const val PERMALINK = "persona"
    const val TYPE = "persona"
    const val PROFILE_PERMALINK = "profile"
    const val PROFILE_TYPE = "profile"

    const val TONE = "tone"
    const val DETAIL = "detail"
    const val IDENTITY = "identity"

    const val MAX_TONE_CHARS = 60
    const val MAX_DETAILS = 10
    const val MAX_DETAIL_CHARS = 160
    const val MAX_SECTION_CHARS = 1500
    const val MAX_PROFILE_CHARS = 1500

    fun tone(note: Note?): String =
        note?.observations?.firstOrNull { it.category == TONE }?.content?.trim().orEmpty()

    fun details(note: Note?): List<String> = note?.observations
        ?.filter { it.category == DETAIL }
        ?.map { it.content.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

    fun identity(note: Note?): String =
        note?.observations?.firstOrNull { it.category == IDENTITY }?.content?.trim().orEmpty()

    /** One detail per line, for the settings form. */
    fun detailsText(note: Note?): String = details(note).joinToString("\n")

    /** Trims, drops blanks and duplicates, caps each line and the number of lines. */
    fun cleanDetails(text: String): List<String> = text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { it.take(MAX_DETAIL_CHARS) }
        .distinct()
        .take(MAX_DETAILS)

    /** Null when the values fit; otherwise a short reason for the model or the settings form. */
    fun problem(tone: String, details: List<String>): String? = when {
        tone.isBlank() -> "性格基调不能为空"
        tone.trim().length > MAX_TONE_CHARS -> "性格基调最多 $MAX_TONE_CHARS 个字"
        details.size > MAX_DETAILS -> "性格细节最多 $MAX_DETAILS 条"
        details.any { it.trim().length > MAX_DETAIL_CHARS } -> "每条性格细节最多 $MAX_DETAIL_CHARS 个字"
        else -> null
    }

    /**
     * Rewrites the persona note. Observations of other categories, relations, tags and the free
     * text before the sections are kept. The body is rebuilt so removed tone/details do not linger.
     */
    fun note(tone: String, details: List<String>, existing: Note? = null): Note {
        val title = existing?.title?.takeIf { it.isNotBlank() } ?: "性格"
        val observations = buildList {
            addAll(existing?.observations?.filter { it.category != TONE && it.category != DETAIL }.orEmpty())
            tone.trim().takeIf { it.isNotEmpty() }?.let { add(Observation(TONE, it)) }
            details.map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { it.take(MAX_DETAIL_CHARS) }
                .distinct()
                .take(MAX_DETAILS)
                .forEach { add(Observation(DETAIL, it)) }
        }
        val relations = existing?.relations.orEmpty()
        return Note(
            title = title,
            permalink = PERMALINK,
            type = TYPE,
            tags = existing?.tags ?: listOf("性格"),
            extraFrontmatter = existing?.extraFrontmatter ?: emptyMap(),
            body = NoteText.body(title, observations, relations, existing?.body?.let { NoteText.preamble(it) }.orEmpty()),
            observations = observations,
            relations = relations,
        )
    }

    /** Rewrites the profile's `[identity]` while keeping the other observations, relations and tags. */
    fun profileNote(identity: String, existing: Note? = null): Note {
        val title = existing?.title?.takeIf { it.isNotBlank() } ?: "个人档案"
        val observations = buildList {
            addAll(existing?.observations?.filter { it.category != IDENTITY }.orEmpty())
            identity.trim().takeIf { it.isNotEmpty() }?.let { add(Observation(IDENTITY, it)) }
        }
        val relations = existing?.relations.orEmpty()
        return Note(
            title = title,
            permalink = PROFILE_PERMALINK,
            type = PROFILE_TYPE,
            tags = existing?.tags ?: listOf("个人档案"),
            extraFrontmatter = existing?.extraFrontmatter ?: emptyMap(),
            body = NoteText.body(title, observations, relations, existing?.body?.let { NoteText.preamble(it) }.orEmpty()),
            observations = observations,
            relations = relations,
        )
    }

    /** Text under `## 性格设定`, cut to [MAX_SECTION_CHARS]; empty when nothing is configured. */
    fun section(note: Note?): String {
        val tone = tone(note)
        val details = details(note)
        if (tone.isEmpty() && details.isEmpty()) return ""
        val text = buildString {
            if (tone.isNotEmpty()) append("性格基调：").append(tone).append('\n')
            details.forEach { append("- [").append(DETAIL).append("] ").append(it).append('\n') }
        }.trimEnd()
        return text.take(MAX_SECTION_CHARS)
    }

    /** Text under `## 用户画像`, cut to [MAX_PROFILE_CHARS]; empty when the note has nothing to say. */
    fun profileSection(note: Note?): String {
        if (note == null) return ""
        val lines = note.observations.map { NoteText.observationLine(it) }
            .ifEmpty { listOf(note.body.trim()) }
            .filter { it.isNotBlank() }
        return lines.joinToString("\n").take(MAX_PROFILE_CHARS)
    }
}
