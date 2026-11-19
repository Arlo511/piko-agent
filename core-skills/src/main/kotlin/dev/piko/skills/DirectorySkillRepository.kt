package dev.piko.skills

import dev.piko.api.Skill
import dev.piko.api.SkillDeleteResult
import dev.piko.api.SkillDraft
import dev.piko.api.SkillLoadResult
import dev.piko.api.SkillProblem
import dev.piko.api.SkillRepository
import dev.piko.api.SkillWriteResult
import dev.piko.api.SkillWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Loads skills from folders `<root>/<name>/SKILL.md` (+ optional `flow.yaml`).
 *
 * Later [roots] override earlier ones by skill name, so pass bundled skills first and user skills
 * last. Missing roots are skipped silently (the user folder may not exist yet). A broken skill is
 * reported in [SkillLoadResult.problems] and never stops the others from loading; when a user skill
 * is broken, a bundled skill of the same name stays in place.
 *
 * [list] and [get] load lazily on first use; [reload] rescans. Safe to call from any thread.
 */
class DirectorySkillRepository(
    private val roots: List<File>,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : SkillRepository, SkillWriter {
    private val mutex = Mutex()

    /** Serializes writes and reloads so a reader never sees a half-written skill. */
    private val writeMutex = Mutex()

    /** The last root is the user folder; earlier roots are bundled and read-only. */
    private val userRoot: File? = roots.lastOrNull()
    private val builtInRoots: List<File> = roots.dropLast(1)

    @Volatile
    private var state: State? = null

    override suspend fun reload(): SkillLoadResult = mutex.withLock { load() }.result

    override suspend fun list(): List<Skill> = loaded().result.skills

    override suspend fun get(name: String): Skill? = loaded().byName[name]

    private suspend fun loaded(): State = state ?: mutex.withLock { state ?: load() }

    /** Must hold [mutex]. */
    private suspend fun load(): State = withContext(io) {
        val merged = LinkedHashMap<String, Skill>()
        val problems = mutableListOf<SkillProblem>()
        for (root in roots) {
            val (skills, rootProblems) = loadRoot(root)
            problems += rootProblems
            skills.forEach { merged[it.name] = it }
        }
        val skills = merged.values.sortedBy { it.name }
        State(SkillLoadResult(skills, problems), skills.associateBy { it.name })
    }.also { state = it }

    override suspend fun validate(name: String, markdown: String): List<SkillProblem> {
        if (!isValidName(name)) return listOf(SkillProblem("draft", "name '$name' must match [a-z0-9-]+", null))
        val (skill, problems) = SkillParser.parseFolder(name, markdown, flowYaml = null, source = "draft/$name")
        return if (skill == null && problems.isEmpty()) {
            listOf(SkillProblem("draft/$name/SKILL.md", "SKILL.md 无法解析", null))
        } else {
            problems
        }
    }

    override suspend fun create(draft: SkillDraft): SkillWriteResult = writeMutex.withLock {
        val dir = userDirFor(draft.name) ?: return@withLock rejectedName(draft.name)
        if (File(dir, SKILL_FILE).isFile) {
            return@withLock SkillWriteResult.Rejected("同名技能已存在：${draft.name}，请用 update_skill 修改")
        }
        if (dir.exists()) return@withLock SkillWriteResult.Rejected("用户技能目录里已有同名文件夹：${draft.name}")
        writeLocked(dir, draft.name, draft.markdown, created = true)
    }

    override suspend fun update(name: String, markdown: String): SkillWriteResult = writeMutex.withLock {
        val dir = userDirFor(name) ?: return@withLock rejectedName(name)
        val exists = File(dir, SKILL_FILE).isFile
        val builtIn = isBuiltIn(name)
        if (!exists && !builtIn) return@withLock SkillWriteResult.Rejected("技能不存在：$name")
        if (!exists && dir.exists()) return@withLock SkillWriteResult.Rejected("用户技能目录里已有同名文件夹：$name")
        writeLocked(dir, name, markdown, created = !exists)
    }

    override suspend fun delete(name: String): SkillDeleteResult = writeMutex.withLock {
        val dir = userDirFor(name) ?: return@withLock SkillDeleteResult.Rejected("技能名不合法：$name")
        withContext(io) {
            if (!File(dir, SKILL_FILE).isFile) {
                return@withContext if (isBuiltIn(name)) {
                    SkillDeleteResult.Rejected("内置技能不可删除：$name")
                } else {
                    SkillDeleteResult.Rejected("技能不存在：$name")
                }
            }
            if (!dir.deleteRecursively()) return@withContext SkillDeleteResult.Rejected("删除失败：$name")
            reload()
            SkillDeleteResult.Ok(name)
        }
    }

    override suspend fun userSkillExists(name: String): Boolean =
        userDirFor(name)?.let { File(it, SKILL_FILE).isFile } ?: false

    override suspend fun isBuiltIn(name: String): Boolean =
        isValidName(name) && builtInRoots.any { File(File(it, name), SKILL_FILE).isFile }

    private fun isValidName(name: String): Boolean = SkillParser.NAME.matches(name) && name.length <= MAX_NAME_LENGTH

    /** Resolves `<userRoot>/<name>`; null when the name is invalid or the path escapes the user root. */
    private fun userDirFor(name: String): File? {
        if (!isValidName(name)) return null
        val root = userRoot ?: return null
        val dir = File(root, name).canonicalFile
        return if (dir.parentFile == root.canonicalFile) dir else null
    }

    private fun rejectedName(name: String) = SkillWriteResult.Rejected(
        if (userRoot == null) "没有可写的用户技能目录" else "技能名不合法：'$name'，只能用 a-z、0-9 和 -",
    )

    /** Writes `SKILL.md.tmp`, validates it, then renames atomically; a failed draft leaves nothing behind. */
    private suspend fun writeLocked(dir: File, name: String, markdown: String, created: Boolean): SkillWriteResult = withContext(io) {
        val madeDir = !dir.exists()
        if (madeDir && !dir.mkdirs()) return@withContext SkillWriteResult.Rejected("无法创建目录：${dir.path}")
        val tmp = File(dir, "$SKILL_FILE.tmp")
        val file = File(dir, SKILL_FILE)
        try {
            tmp.writeText(markdown, Charsets.UTF_8)
            val problems = validate(name, tmp.readText(Charsets.UTF_8))
            if (problems.isNotEmpty()) {
                tmp.delete()
                if (created) dir.deleteRecursively()
                return@withContext SkillWriteResult.Invalid(problems)
            }
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            tmp.delete()
            if (created) dir.deleteRecursively()
            return@withContext SkillWriteResult.Rejected("写入失败：${e.message ?: e::class.simpleName}")
        }
        val overridesBuiltIn = isBuiltIn(name)
        reload()
        SkillWriteResult.Ok(name, created, overridesBuiltIn)
    }

    private class State(val result: SkillLoadResult, val byName: Map<String, Skill>)

    companion object {
        private const val SKILL_FILE = "SKILL.md"
        private const val MAX_NAME_LENGTH = 40

        /** Loads one root without overrides; duplicate skill names inside it are problems. */
        fun loadRoot(root: File): SkillLoadResult {
            if (!root.exists()) return SkillLoadResult(emptyList(), emptyList())
            val folders = root.listFiles()
                ?: return SkillLoadResult(emptyList(), listOf(SkillProblem(root.path, "not a readable directory")))
            val parsed = mutableListOf<Pair<File, Skill>>()
            val problems = mutableListOf<SkillProblem>()
            for (folder in folders.filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name }) {
                val skillFile = File(folder, "SKILL.md")
                if (!skillFile.isFile) {
                    problems += SkillProblem(folder.path, "missing SKILL.md")
                    continue
                }
                val flowFile = File(folder, "flow.yaml")
                val (skill, skillProblems) = try {
                    SkillParser.parseFolder(
                        name = folder.name,
                        skillMd = skillFile.readText(),
                        flowYaml = if (flowFile.isFile) flowFile.readText() else null,
                        source = folder.path,
                    )
                } catch (e: IOException) {
                    null to listOf(SkillProblem(folder.path, "cannot read: ${e.message}"))
                }
                problems += skillProblems
                if (skill != null) parsed += folder to skill
            }
            val skills = parsed.groupBy({ it.second.name }) { it }.map { (name, candidates) ->
                // Prefer the folder whose name matches; report the rest.
                val keep = candidates.firstOrNull { it.first.name == name } ?: candidates.first()
                for ((folder, _) in candidates) if (folder != keep.first) {
                    problems += SkillProblem(folder.path, "duplicate skill name '$name' (also in ${keep.first.path}); ignored")
                }
                keep.second
            }
            return SkillLoadResult(skills, problems)
        }
    }
}
