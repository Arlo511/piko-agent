package dev.xiaoman.llm

import dev.xiaoman.llm.ModelListResult.Failure.Reason
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ModelListClientTest {
    private lateinit var server: MockWebServer

    @BeforeTest
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun client(timeoutMillis: Long = 5_000) = ModelListClient(
        OkHttpClient.Builder().callTimeout(timeoutMillis, TimeUnit.MILLISECONDS).build(),
    )

    private fun baseUrl() = server.url("/api/v3").toString()

    @Test
    fun `lists unique model ids and sends the bearer token`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"object":"list","data":[{"id":"model-a"},{"id":"model-b"},{"id":"model-a"},{"id":""}]}""",
            ),
        )
        val result = client().list(baseUrl(), "sk-secret")
        assertEquals(ModelListResult.Success(listOf("model-a", "model-b")), result)
        val request = server.takeRequest()
        assertEquals("/api/v3/models", request.path)
        assertEquals("Bearer sk-secret", request.getHeader("Authorization"))
        assertFalse("sk-secret" in result.toString())
    }

    @Test
    fun `401 and 403 mean the key is invalid or unauthorized`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(
            ModelListResult.Failure(Reason.UNAUTHORIZED, 401),
            client().list(baseUrl(), "key"),
        )
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(
            ModelListResult.Failure(Reason.UNAUTHORIZED, 403),
            client().list(baseUrl(), "key"),
        )
    }

    @Test
    fun `404 and 405 mean the endpoint does not support a model list`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(
            ModelListResult.Failure(Reason.UNSUPPORTED, 404),
            client().list(baseUrl(), "key"),
        )
        server.enqueue(MockResponse().setResponseCode(405))
        assertEquals(
            ModelListResult.Failure(Reason.UNSUPPORTED, 405),
            client().list(baseUrl(), "key"),
        )
    }

    @Test
    fun `other http errors keep the status code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(
            ModelListResult.Failure(Reason.OTHER, 500),
            client().list(baseUrl(), "key"),
        )
    }

    @Test
    fun `a timeout is a network failure`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertEquals(
            ModelListResult.Failure(Reason.NETWORK),
            client(timeoutMillis = 300).list(baseUrl(), "key"),
        )
    }

    @Test
    fun `a body without data is rejected`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        assertEquals(
            ModelListResult.Failure(Reason.MALFORMED),
            client().list(baseUrl(), "key"),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"object":"list"}"""))
        assertEquals(
            ModelListResult.Failure(Reason.MALFORMED),
            client().list(baseUrl(), "key"),
        )
    }

    @Test
    fun `an empty list is a success`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"object":"list","data":[]}"""))
        assertEquals(ModelListResult.Success(emptyList()), client().list(baseUrl(), "key"))
    }
}
