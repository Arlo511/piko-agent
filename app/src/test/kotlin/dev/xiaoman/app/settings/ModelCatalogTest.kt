package dev.xiaoman.app.settings

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelCatalogTest {
    private val catalog = ModelCatalog.parse(
        """
        {
          "updatedAt": "2026-11-17",
          "models": [
            {"id": "vision-model", "displayName": "Vision Model", "vision": true},
            {"id": "text-model", "displayName": "Text Model", "vision": false},
            {"id": "", "displayName": "blank"},
            {"id": "vision-model", "displayName": "Duplicate", "vision": false}
          ]
        }
        """.trimIndent(),
    )

    @Test
    fun `parses entries and drops blank or duplicate ids`() {
        assertEquals("2026-11-17", catalog.updatedAt)
        assertEquals(listOf("vision-model", "text-model"), catalog.models.map { it.id })
        assertEquals("Vision Model", catalog.models.first().displayName)
        assertTrue(catalog.models.first().vision)
        assertFalse(catalog.models.last().vision)
        assertEquals(ModelSource.BUILT_IN, catalog.models.first().source)
    }

    @Test
    fun `merge keeps built in metadata and appends unique remote ids`() {
        val merged = catalog.merge(listOf("vision-model", "remote-a", "remote-a", " "))

        assertEquals(listOf("vision-model", "text-model", "remote-a"), merged.map { it.id })
        assertEquals("Vision Model", merged.first().displayName)
        assertEquals(ModelSource.REMOTE, merged.last().source)
        assertFalse(merged.last().vision)
    }

    @Test
    fun `operator options keep only vision models`() {
        val ids = catalog.options("vision-model", visionOnly = true).map { it.id }

        assertEquals(listOf("vision-model"), ids)
    }

    @Test
    fun `selected custom id is appended and never dropped`() {
        val options = catalog.options("ep-20260101", visionOnly = true)

        assertEquals(listOf("vision-model", "ep-20260101"), options.map { it.id })
        val custom = options.last()
        assertEquals(ModelSource.CUSTOM, custom.source)
        assertFalse(custom.vision)
        assertEquals("ep-20260101", custom.displayName)
    }

    @Test
    fun `selected non vision built in model is still shown for the operator`() {
        val options = catalog.options("text-model", visionOnly = true)

        assertEquals(listOf("vision-model", "text-model"), options.map { it.id })
        assertEquals(ModelSource.BUILT_IN, options.last().source)
        assertFalse(options.last().vision)
    }

    @Test
    fun `bundled asset parses into a usable catalog`() {
        val file = java.io.File("src/main/assets/models/ark_models.json")
        assertTrue(file.isFile, "missing ${file.absolutePath}")
        val bundled = ModelCatalog.parse(file.readText())

        assertTrue(bundled.models.size in 8..10, "expected 8-10 models, got ${bundled.models.size}")
        assertEquals("2026-11-17", bundled.updatedAt)
        assertTrue(bundled.models.any { it.vision })
        assertTrue(bundled.models.any { !it.vision })
    }

    @Test
    fun `invalid json falls back to an empty catalog`() {
        val empty = ModelCatalog.parse("not json")

        assertTrue(empty.models.isEmpty())
        assertEquals("", empty.updatedAt)
    }
}
