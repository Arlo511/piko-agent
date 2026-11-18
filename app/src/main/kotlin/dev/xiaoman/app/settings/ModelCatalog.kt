package dev.xiaoman.app.settings

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where a model id in the dropdown came from. */
enum class ModelSource { BUILT_IN, REMOTE, CUSTOM }

data class ModelOption(
    val id: String,
    val displayName: String,
    val vision: Boolean,
    val source: ModelSource,
)

/**
 * The model ids shipped with the app, merged with ids fetched from `{baseUrl}/models`.
 * Built-in metadata wins; remote-only ids are shown with their raw id and unknown vision.
 */
data class ModelCatalog(
    val models: List<ModelOption> = emptyList(),
    val updatedAt: String = "",
) {

    /** [selected] is always kept, even when it is a custom id or a non-vision model. */
    fun options(
        selected: String,
        remoteIds: List<String> = emptyList(),
        visionOnly: Boolean = false,
    ): List<ModelOption> {
        val merged = merge(remoteIds)
        val visible = if (visionOnly) merged.filter { it.vision } else merged
        val id = selected.trim()
        if (id.isEmpty() || visible.any { it.id == id }) return visible
        val current = merged.firstOrNull { it.id == id }
            ?: ModelOption(id, id, vision = false, source = ModelSource.CUSTOM)
        return visible + current
    }

    fun merge(remoteIds: List<String>): List<ModelOption> {
        val seen = models.mapTo(mutableSetOf()) { it.id }
        val extra = remoteIds.map { it.trim() }
            .filter { it.isNotEmpty() && seen.add(it) }
            .map { ModelOption(it, it, vision = false, source = ModelSource.REMOTE) }
        return models + extra
    }

    companion object {
        const val ASSET = "models/ark_models.json"
        private const val TAG = "Xiaoman"
        private val json = Json { ignoreUnknownKeys = true }

        fun load(context: Context): ModelCatalog = try {
            context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) }
        } catch (e: Exception) {
            Log.w(TAG, "model-catalog: cannot read $ASSET (${e.javaClass.simpleName})")
            ModelCatalog()
        }

        fun parse(text: String): ModelCatalog {
            val file = runCatching { json.decodeFromString<ModelFile>(text) }.getOrNull()
                ?: return ModelCatalog()
            val models = file.models.mapNotNull { entry ->
                val id = entry.id.trim()
                if (id.isEmpty()) null else ModelOption(
                    id = id,
                    displayName = entry.displayName.trim().ifEmpty { id },
                    vision = entry.vision,
                    source = ModelSource.BUILT_IN,
                )
            }.distinctBy { it.id }
            return ModelCatalog(models, file.updatedAt)
        }
    }
}

@Serializable
private data class ModelFile(
    val updatedAt: String = "",
    val models: List<ModelEntry> = emptyList(),
)

@Serializable
private data class ModelEntry(
    val id: String = "",
    val displayName: String = "",
    val vision: Boolean = false,
)
