package com.universalremote.adapters.common

import com.universalremote.domain.model.RemoteError
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class HttpAdapterSupportTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder()
        .readTimeout(300, TimeUnit.MILLISECONDS)
        .callTimeout(1, TimeUnit.SECONDS)
        .build()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getJson devuelve el objeto`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"active","n":3}"""))

        val json = client.getJson(server.url("/sony/system").toString())

        assertEquals("active", json.getString("status"))
        assertEquals(3, json.getInt("n"))
    }

    @Test
    fun `postBody envia cuerpo y cabeceras y devuelve la respuesta`() = runTest {
        server.enqueue(MockResponse().setBody("ok"))

        val body = client.postBody(server.url("/x").toString(), "<a/>", mapOf("X-Auth-PSK" to "1234"))

        assertEquals("ok", body)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("1234", request.getHeader("X-Auth-PSK"))
        assertEquals("<a/>", request.body.readUtf8())
    }

    @Test
    fun `un 500 lanza RemoteError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        try {
            client.getJson(server.url("/").toString())
            fail("debía lanzar")
        } catch (e: RemoteError.Protocol) {
            // esperado
        }
    }

    @Test
    fun `un 403 lanza PairingRejected`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        try {
            client.postBody(server.url("/").toString(), "", emptyMap())
            fail("debía lanzar")
        } catch (e: RemoteError.PairingRejected) {
            // esperado
        }
    }

    @Test
    fun `un timeout lanza RemoteError tipado`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        try {
            client.getJson(server.url("/").toString())
            fail("debía lanzar")
        } catch (e: RemoteError.Timeout) {
            // esperado
        }
    }

    @Test
    fun `conexion rechazada lanza Unreachable`() = runTest {
        val url = server.url("/").toString()
        server.shutdown()
        try {
            client.getJson(url)
            fail("debía lanzar")
        } catch (e: RemoteError.Unreachable) {
            // esperado
        }
    }
}
