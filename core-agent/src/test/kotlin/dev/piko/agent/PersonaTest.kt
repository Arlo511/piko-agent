package dev.piko.agent

import dev.piko.api.Note
import dev.piko.api.Observation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonaTest {
    @Test
    fun toneAndDetailsRoundTrip() {
        val note = Persona.note("知无不言的亲密导师", listOf("设计类问题直接给专业结论", "其他问题少用术语"))

        assertEquals(Persona.PERMALINK, note.permalink)
        assertEquals(Persona.TYPE, note.type)
        assertEquals("知无不言的亲密导师", Persona.tone(note))
        assertEquals(listOf("设计类问题直接给专业结论", "其他问题少用术语"), Persona.details(note))
    }

    @Test
    fun detailsAreCleanedAndCapped() {
        val long = "长".repeat(Persona.MAX_DETAIL_CHARS + 5)
        assertEquals(
            listOf("a", "b", long.take(Persona.MAX_DETAIL_CHARS)),
            Persona.cleanDetails("a\n\n a \nb\n$long"),
        )
        assertEquals(Persona.MAX_DETAILS, Persona.cleanDetails((1..20).joinToString("\n") { "d$it" }).size)
    }

    @Test
    fun rewritingNoteRemovesOldDetails() {
        val first = Persona.note("A", listOf("d1"))
        val second = Persona.note("B", listOf("d2"), first)

        assertEquals(listOf("d2"), Persona.details(second))
        assertFalse("d1" in second.body)
    }

    @Test
    fun profileIdentityReplacedKeepsOtherObservations() {
        val profile = Note(
            title = "个人档案",
            permalink = "profile",
            type = "profile",
            observations = listOf(
                Observation("identity", "设计师"),
                Observation("preference", "性价比优先"),
            ),
        )

        val updated = Persona.profileNote("产品经理", profile)

        assertEquals("产品经理", Persona.identity(updated))
        assertTrue(updated.observations.any { it.category == "preference" && it.content == "性价比优先" })
        assertFalse("设计师" in updated.body)
    }

    @Test
    fun emptyPersonaHasNoSection() {
        assertTrue(Persona.section(null).isEmpty())
        assertTrue(Persona.section(Note(title = "性格", permalink = "persona")).isEmpty())
        assertTrue(Persona.profileSection(null).isEmpty())
    }

    @Test
    fun sectionIsCapped() {
        val note = Persona.note("基调", List(Persona.MAX_DETAILS) { "细".repeat(Persona.MAX_DETAIL_CHARS) })
        val section = Persona.section(note)

        assertTrue(section.startsWith("性格基调：基调"))
        assertTrue(section.length <= Persona.MAX_SECTION_CHARS)
    }

    @Test
    fun problemRejectsBlankAndOversizedValues() {
        assertNull(Persona.problem("导师", listOf("细节")))
        assertTrue(Persona.problem("", emptyList()) != null)
        assertTrue(Persona.problem("基".repeat(Persona.MAX_TONE_CHARS + 1), emptyList()) != null)
        assertTrue(Persona.problem("导师", List(Persona.MAX_DETAILS + 1) { "d" }) != null)
        assertTrue(Persona.problem("导师", listOf("细".repeat(Persona.MAX_DETAIL_CHARS + 1))) != null)
    }
}
