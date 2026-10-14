package com.universalremote.hardware.ir

import com.universalremote.domain.model.RemoteError
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.universalremote.core.di.LanHttpClient

/**
 * Puente IoT para teléfonos sin emisor: reenvía la señal en crudo a un hub IR de la LAN
 * (ESP32/ESP8266 con IRremoteESP8266, Broadlink vía firmware abierto, etc.).
 *
 * Contrato HTTP: `POST {bridgeUrl}/ir/transmit` con
 * `{"frequency": 38000, "pattern": [9000, 4500, …]}` (µs, marca/espacio). Cualquier 2xx = enviado.
 */
class HttpIrBridgeTransmitter(
    private val client: OkHttpClient,
    private val endpoint: HttpUrl,
) : IrTransmitter {

    override val isAvailable: Boolean = true

    override suspend fun transmit(signal: IrSignal) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("frequency", signal.frequency)
            .put("pattern", JSONArray().apply { signal.pattern.forEach { put(it) } })
            .toString()
            .toRequestBody(JSON)
        val request = Request.Builder().url(endpoint).post(body).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RemoteError.Protocol("El hub IR respondió HTTP ${response.code}")
                }
            }
        } catch (e: IOException) {
            throw RemoteError.from(e, endpoint.host)
        }
    }

    class Factory @Inject constructor(@LanHttpClient private val client: OkHttpClient) {
        /** @throws RemoteError.InvalidCode si la URL no es http(s) válida. */
        fun create(bridgeUrl: String): HttpIrBridgeTransmitter {
            val base = bridgeUrl.trim().toHttpUrlOrNull()
                ?: throw RemoteError.InvalidCode("URL de hub IR inválida: $bridgeUrl")
            val endpoint = base.newBuilder().addPathSegments("ir/transmit").build()
            return HttpIrBridgeTransmitter(client, endpoint)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
