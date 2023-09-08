// SPDX-FileCopyrightText: 2017-2023 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

package org.equeim.tremotesf.torrentfile.rpc

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import okhttp3.Credentials
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.equeim.tremotesf.torrentfile.rpc.requests.getSessionStats
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import timber.log.Timber
import java.net.HttpURLConnection
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalSerializationApi::class)
class RpcClientTest {
    private val server = MockWebServer()
    private val client = RpcClient()

    private class TestTree : Timber.DebugTree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            val time = LocalTime.now()
            System.err.println(
                if (tag != null) {
                    "$time $tag: $message"
                } else {
                    "$time: $message"
                }
            )
        }
    }

    @BeforeEach
    fun before() {
        Timber.plant(TestTree())
        server.start()
        client.setConnectionConfiguration(createTestServer())
        client.shouldConnectToServer.value = true
    }

    @AfterEach
    fun after() {
        server.close()
        Timber.uprootAll()
    }

    @Test
    fun `Check that validation is performed on first request`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(HttpURLConnection.HTTP_CONFLICT)
                .addHeader(SESSION_ID_HEADER, TEST_SESSION_ID_1)
        )
        enqueueSuccessfulValidation()
        server.enqueue(
            MockResponse().setBody(TEST_SESSION_STATS_RESPONSE_BODY).addHeader(SESSION_ID_HEADER, TEST_SESSION_ID_1)
        )
        client.getSessionStats()
        server.takeRequest()
        assertEquals(
            EXPECTED_SERVER_VERSION_REQUEST_BODY,
            client.json.decodeFromBufferedSource<JsonElement>(server.takeRequest().body)
        )
        assertEquals(
            EXPECTED_UNIX_ROOT_FREE_SPACE_REQUEST_BODY,
            client.json.decodeFromBufferedSource<JsonElement>(server.takeRequest().body)
        )
    }

    @Test
    fun `Check that validation is performed when session id changes`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(HttpURLConnection.HTTP_CONFLICT)
                .addHeader(SESSION_ID_HEADER, TEST_SESSION_ID_1)
        )
        enqueueSuccessfulValidation()
        server.enqueue(
            MockResponse().setBody(TEST_SESSION_STATS_RESPONSE_BODY).addHeader(SESSION_ID_HEADER, TEST_SESSION_ID_1)
        )
        client.getSessionStats()
        repeat(4) { server.takeRequest() }
        server.enqueue(
            MockResponse().setResponseCode(HttpURLConnection.HTTP_CONFLICT)
                .addHeader(SESSION_ID_HEADER, TEST_SESSION_ID_2)
        )
        enqueueSuccessfulValidation()
        server.enqueue(
            MockResponse().setBody(TEST_SESSION_STATS_RESPONSE_BODY).addHeader(SESSION_ID_HEADER, TEST_SESSION_ID_2)
        )
        client.getSessionStats()
        server.takeRequest()
        assertEquals(
            EXPECTED_SERVER_VERSION_REQUEST_BODY,
            client.json.decodeFromBufferedSource<JsonElement>(server.takeRequest().body)
        )
        assertEquals(
            EXPECTED_UNIX_ROOT_FREE_SPACE_REQUEST_BODY,
            client.json.decodeFromBufferedSource<JsonElement>(server.takeRequest().body)
        )
    }

    @Test
    fun `Check session stats request`() = runTest {
        enqueueSuccessfulValidation()
        server.enqueue(
            MockResponse().setBody(TEST_SESSION_STATS_RESPONSE_BODY).addHeader(SESSION_ID_HEADER, TEST_SESSION_ID_1)
        )
        client.getSessionStats()
        repeat(2) { server.takeRequest() }
        val body = client.json.decodeFromBufferedSource<JsonElement>(server.takeRequest().body)
        assertEquals(EXPECTED_SESSION_STATS_REQUEST_BODY, body)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Check timeout error`(errorDuringValidation: Boolean) = runTest {
        if (errorDuringValidation) {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        } else {
            enqueueSuccessfulValidation()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        }
        assertThrows<RpcRequestError.Timeout> { client.getSessionStats() }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Check connection error`(errorDuringValidation: Boolean) = runTest {
        if (errorDuringValidation) {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        } else {
            enqueueSuccessfulValidation()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        }
        assertThrows<RpcRequestError.NetworkError> { client.getSessionStats() }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Check http status code error`(errorDuringValidation: Boolean) = runTest {
        if (errorDuringValidation) {
            server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR))
        } else {
            enqueueSuccessfulValidation()
            server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR))
        }
        val error = assertThrows<RpcRequestError.UnsuccessfulHttpStatusCode> { client.getSessionStats() }
        assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, error.response.code)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Check deserialization error`(errorDuringValidation: Boolean) = runTest {
        if (errorDuringValidation) {
            server.enqueue(MockResponse().setBody("lol"))
        } else {
            enqueueSuccessfulValidation()
            server.enqueue(MockResponse().setBody("lol"))
        }
        assertThrows<RpcRequestError.DeserializationError> { client.getSessionStats() }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Check deserialization error due to empty body`(errorDuringValidation: Boolean) = runTest {
        if (errorDuringValidation) {
            server.enqueue(MockResponse())
        } else {
            enqueueSuccessfulValidation()
            server.enqueue(MockResponse())
        }
        assertThrows<RpcRequestError.DeserializationError> { client.getSessionStats() }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Check authentication error due to no credentials`(errorDuringValidation: Boolean) = runTest {
        if (errorDuringValidation) {
            server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAUTHORIZED))
        } else {
            enqueueSuccessfulValidation()
            server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAUTHORIZED))
        }
        assertThrows<RpcRequestError.AuthenticationError> { client.getSessionStats() }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Check authentication error due to invalid credentials`(errorDuringValidation: Boolean) = runTest {
        client.setConnectionConfiguration(
            createTestServer().copy(
                authentication = true,
                username = "foo",
                password = "bar"
            )
        )
        if (errorDuringValidation) {
            server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAUTHORIZED))
        } else {
            enqueueSuccessfulValidation()
            server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAUTHORIZED))
        }
        assertThrows<RpcRequestError.AuthenticationError> { client.getSessionStats() }
    }

    @Test
    fun `Check authentication`() = runTest {
        val username = "foo"
        val password = "bar"
        client.setConnectionConfiguration(
            createTestServer().copy(
                authentication = true,
                username = username,
                password = password
            )
        )
        enqueueSuccessfulValidation()
        server.enqueue(MockResponse().setBody(TEST_SESSION_STATS_RESPONSE_BODY))
        client.getSessionStats()
        assertEquals(Credentials.basic(username, password), server.takeRequest().getHeader(AUTHORIZATION_HEADER))
    }

    @Test
    fun `Check error when server is too old`() = runTest {
        val serverVersionResponse =
            """{"arguments":{"rpc-version":13,"rpc-version-minimum":13,"version":"2.30"},"result":"success"}"""
        server.enqueue(MockResponse().setBody(serverVersionResponse).addHeader(SESSION_ID_HEADER, TEST_SESSION_ID_1))
        val error = assertThrows<RpcRequestError.UnsupportedServerVersion> { client.getSessionStats() }
        assertEquals("2.30", error.version)
    }

    @Test
    fun `Check error when server is too new`() = runTest {
        val serverVersionResponse =
            """{"arguments":{"rpc-version":42,"rpc-version-minimum":21,"version":"666.666"},"result":"success"}"""
        server.enqueue(MockResponse().setBody(serverVersionResponse).addHeader(SESSION_ID_HEADER, TEST_SESSION_ID_1))
        val error = assertThrows<RpcRequestError.UnsupportedServerVersion> { client.getSessionStats() }
        assertEquals("666.666", error.version)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Check RPC response error`(errorDuringValidation: Boolean) = runTest {
        if (errorDuringValidation) {
            server.enqueue(
                MockResponse().setBody("""{"result":"wtf", "arguments": {}}""")
                    .addHeader(SESSION_ID_HEADER, TEST_SESSION_ID_1)
            )
        } else {
            enqueueSuccessfulValidation()
            server.enqueue(
                MockResponse().setBody("""{"result":"wtf", "arguments": {}}""")
                    .addHeader(SESSION_ID_HEADER, TEST_SESSION_ID_1)
            )
        }
        val error = assertThrows<RpcRequestError.UnsuccessfulResultField> { client.getSessionStats() }
        assertEquals("wtf", error.result)
    }

    private fun createTestServer(): Server {
        val url = server.url("/")
        return Server(
            address = url.host,
            port = url.port,
            apiPath = url.encodedPath,
            timeout = 1.seconds,
        )
    }

    private fun enqueueSuccessfulValidation() {
        server.enqueue(
            MockResponse().setBody(TEST_SERVER_VERSION_RESPONSE_BODY).addHeader(SESSION_ID_HEADER, TEST_SESSION_ID_1)
        )
        server.enqueue(
            MockResponse().setBody(TEST_UNIX_ROOT_FREE_SPACE_RESPONSE_BODY_SUCCESS)
                .addHeader(SESSION_ID_HEADER, TEST_SESSION_ID_1)
        )
    }

    private companion object {
        val TEST_SESSION_ID_1 = "hmm"
        val TEST_SESSION_ID_2 = "huh"

        val EXPECTED_SERVER_VERSION_REQUEST_BODY = JsonObject(
            mapOf(
                "method" to JsonPrimitive("session-get"),
                "arguments" to JsonObject(
                    mapOf(
                        "fields" to JsonArray(
                            listOf(
                                "rpc-version",
                                "rpc-version-minimum",
                                "version"
                            ).map(::JsonPrimitive)
                        )
                    )
                )
            )
        )

        val EXPECTED_UNIX_ROOT_FREE_SPACE_REQUEST_BODY = JsonObject(
            mapOf(
                "method" to JsonPrimitive("free-space"),
                "arguments" to JsonObject(mapOf("path" to JsonPrimitive("/")))
            )
        )

        val TEST_SERVER_VERSION_RESPONSE_BODY =
            """{"arguments":{"rpc-version":17,"rpc-version-minimum":14,"version":"4.0.3 (6b0e49bbb2)"},"result":"success"}"""
        val TEST_UNIX_ROOT_FREE_SPACE_RESPONSE_BODY_SUCCESS = """{"arguments":{"size-bytes":666},"result":"success"}"""
        val TEST_UNIX_ROOT_FREE_SPACE_RESPONSE_BODY_FAILURE = """{"arguments":{},"result":"nope"}"""

        val EXPECTED_SESSION_STATS_REQUEST_BODY = JsonObject(mapOf("method" to JsonPrimitive("session-stats")))
        val TEST_SESSION_STATS_RESPONSE_BODY = """
            {
              "arguments": {
                "activeTorrentCount": 65,
                "cumulative-stats": {
                  "downloadedBytes": 3185828524323,
                  "filesAdded": 196458,
                  "secondsActive": 15499651,
                  "sessionCount": 812,
                  "uploadedBytes": 3287616408173
                },
                "current-stats": {
                  "downloadedBytes": 7372800,
                  "filesAdded": 1,
                  "secondsActive": 4735,
                  "sessionCount": 1,
                  "uploadedBytes": 20919542
                },
                "downloadSpeed": 0,
                "pausedTorrentCount": 0,
                "torrentCount": 65,
                "uploadSpeed": 0
              },
              "result": "success"
            }
        """.trimIndent()
    }
}
