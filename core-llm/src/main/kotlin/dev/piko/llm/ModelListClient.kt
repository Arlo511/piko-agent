package dev.piko.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Outcome of asking an OpenAI-compatible endpoint for its model list. */
sealed class ModelListResult {
    data class Success(val modelIds: List<String>) : ModelListResult()

    data class Failure(val reason: Reason, val httpCode: Int? = null) : ModelListResult() {
        enum class Reason { UNAUTHORIZED, UNSUPPORTED, NETWORK, MALFORMED, OTHER }
    }
}

/**
 * Reads `GET {baseUrl}/models` with the configured API key.
 *
 * Only the status code and model ids leave this class: the key and response body are never logged.
 * The caller decides how to fall back when the endpoint does not implement the route.
 */
class ModelListClient internal constructor(private val http: OkHttpClient = defaultHttp()) {

    companion object {
        /** App-facing factory; the OkHttp dependency stays internal to this module. */
        fun create(): ModelListClient = ModelListClient()

        private fun defaultHttp(): OkHttpClient = LlmClients.sharedHttp.newBuilder()
            .callTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    suspend fun list(baseUrl: String, apiKey: String): ModelListResult = withContext(Dispatchers.IO) {
        val url = baseUrl.trim().trimEnd('/') + "/models"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .get()
            .build()
        try {
            http.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 ->
                        ModelListResult.Failure(ModelListResult.Failure.Reason.UNAUTHORIZED, response.code)
                    response.code == 404 || response.code == 405 ->
                        ModelListResult.Failure(ModelListResult.Failure.Reason.UNSUPPORTED, response.code)
                    !response.isSuccessful ->
                        ModelListResult.Failure(ModelListResult.Failure.Reason.OTHER, response.code)
                    else -> parse(response.body?.string().orEmpty())
                }
            }
        } catch (e: IOException) {
            ModelListResult.Failure(ModelListResult.Failure.Reason.NETWORK)
        }
    }

    private fun parse(body: String): ModelListResult {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return ModelListResult.Failure(ModelListResult.Failure.Reason.MALFORMED)
        val data: JsonArray = root["data"] as? JsonArray
            ?: return ModelListResult.Failure(ModelListResult.Failure.Reason.MALFORMED)
        val ids = data.mapNotNull { element ->
            (element as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        }.distinct()
        return ModelListResult.Success(ids)
    }
}
