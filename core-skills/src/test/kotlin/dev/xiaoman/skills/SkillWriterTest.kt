package dev.xiaoman.skills

import dev.xiaoman.api.SkillDeleteResult
import dev.xiaoman.api.SkillDraft
import dev.xiaoman.api.SkillWriteResult
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkillWriterTest {
    private val tmp: File = Files.createTempDirectory("skill-writer-test").toFile()
    private val bundled = File(tmp, "bundled").apply { mkdirs() }
    private val user = File(tmp, "user").apply { mkdirs() }
    private val repo = DirectorySkillRepository(listOf(bundled, user))

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    private fun markdown(name: String, description: String = "测试技能") =
        "---\nname: $name\ndescription: $description\n---\n按步骤操作\n"

    private fun installBuiltIn(name: String) {
        val dir = File(bundled, name).apply { mkdirs() }
        File(dir, "SKILL.md").writeText(markdown(name))
    }

    @Test
    fun createWritesUserSkillAndReloads() = runTest {
        val result = repo.create(SkillDraft("tea-note", markdown("tea-note")))

        assertTrue(result is SkillWriteResult.Ok, "expected ok, got $result")
        assertEquals("tea-note", File(user, "tea-note/SKILL.md").readText().let { markdown ->
            Regex("^name: (.+)$", RegexOption.MULTILINE).find(markdown)?.groupValues?.get(1)
        })
        assertFalse(File(user, "tea-note/SKILL.md.tmp").exists())
        assertEquals(listOf("tea-note"), repo.list().map { it.name })
        assertTrue(repo.userSkillExists("tea-note"))
    }

    @Test
    fun invalidNameIsRejected() = runTest {
        val result = repo.create(SkillDraft("../evil", markdown("evil")))

        assertTrue(result is SkillWriteResult.Rejected)
        assertFalse(File(tmp, "evil/SKILL.md").exists())
        assertFalse(File(user, "evil").exists())
    }

    @Test
    fun duplicateCreateIsRejected() = runTest {
        repo.create(SkillDraft("tea-note", markdown("tea-note")))

        val again = repo.create(SkillDraft("tea-note", markdown("tea-note")))

        assertTrue(again is SkillWriteResult.Rejected)
        assertTrue((again as SkillWriteResult.Rejected).reason.contains("已存在"))
    }

    @Test
    fun builtInSkillCannotBeDeleted() = runTest {
        installBuiltIn("taxi-home")

        val result = repo.delete("taxi-home")

        assertTrue(result is SkillDeleteResult.Rejected)
        assertTrue((result as SkillDeleteResult.Rejected).reason.contains("内置"))
        assertTrue(File(bundled, "taxi-home/SKILL.md").isFile)
    }

    @Test
    fun updatingBuiltInCreatesUserOverrideAndDeleteRestoresIt() = runTest {
        installBuiltIn("taxi-home")

        val updated = repo.update("taxi-home", markdown("taxi-home", "我的版本"))

        assertTrue(updated is SkillWriteResult.Ok, "expected ok, got $updated")
        assertTrue((updated as SkillWriteResult.Ok).created)
        assertTrue(updated.overridesBuiltIn)
        assertTrue(File(user, "taxi-home/SKILL.md").isFile)
        assertEquals("我的版本", repo.get("taxi-home")?.description)

        val deleted = repo.delete("taxi-home")

        assertTrue(deleted is SkillDeleteResult.Ok, "expected ok, got $deleted")
        assertFalse(File(user, "taxi-home").exists())
        assertTrue(File(bundled, "taxi-home/SKILL.md").isFile)
        assertEquals("测试技能", repo.get("taxi-home")?.description)
    }

    @Test
    fun invalidMarkdownLeavesNoFile() = runTest {
        val result = repo.create(SkillDraft("tea-note", "---\ndescription: 缺少 name\n---\n正文\n"))

        assertTrue(result is SkillWriteResult.Invalid, "expected invalid, got $result")
        assertFalse(File(user, "tea-note").exists())
    }

    @Test
    fun deleteRemovesOnlyTheUserFolder() = runTest {
        repo.create(SkillDraft("tea-note", markdown("tea-note")))

        val result = repo.delete("tea-note")

        assertTrue(result is SkillDeleteResult.Ok)
        assertFalse(File(user, "tea-note").exists())
        assertFalse(repo.userSkillExists("tea-note"))
        assertEquals(emptyList(), repo.list())
    }

    @Test
    fun unknownNameIsRejectedOnUpdateAndDelete() = runTest {
        assertTrue(repo.update("missing-one", markdown("missing-one")) is SkillWriteResult.Rejected)
        assertTrue(repo.delete("missing-one") is SkillDeleteResult.Rejected)
    }
}
