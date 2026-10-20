package com.universalremote.adapters.common

import com.universalremote.domain.model.RemoteError
import com.universalremote.network.http.await
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject

/**
 * Helpers HTTP comunes para los adaptadores de marcas con API REST/SOAP (Sony, Vizio, Philips…).
 * Normalizan cualquier fallo a [RemoteError]: 401/403 → [RemoteError.PairingRejected],
 * otros códigos no-2xx → [RemoteError.Protocol], E/S → el tipo que asigna [RemoteError.from].
 */
suspend fun OkHttpClient.getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject {
    val body = execute(url, "GET", null, headers)
    return try {
        JSONObject(body)
    } catch (e: JSONException) {
        throw RemoteError.Protocol("Respuesta JSON inválida", e)
    }
}

suspend fun OkHttpClient.postBody(
    url: String,
    body: String,
    headers: Map<String, String> = emptyMap(),
    contentType: String = "text/xml; charset=utf-8",
): String = execute(url, "POST", body.toRequestBody(contentType.toMediaType()), headers)

suspend fun OkHttpClient.putJson(url: String, body: JSONObject, headers: Map<String, String> = emptyMap()): JSONObject {
    val text = execute(url, "PUT", body.toString().toRequestBody(JSON), headers)
    return try {
        JSONObject(text.ifBlank { "{}" })
    } catch (e: JSONException) {
        throw RemoteError.Protocol("Respuesta JSON inválida", e)
    }
}

private suspend fun OkHttpClient.execute(
    url: String,
    method: String,
    body: okhttp3.RequestBody?,
    headers: Map<String, String>,
): String {
    val httpUrl = url.toHttpUrl()
    val request = Request.Builder().url(httpUrl).method(method, body).apply {
        headers.forEach { (k, v) -> header(k, v) }
    }.build()
    try {
        newCall(request).await().use { response ->
            when {
                response.code == 401 || response.code == 403 -> throw RemoteError.PairingRejected()
                !response.isSuccessful ->
                    throw RemoteError.Protocol("${httpUrl.encodedPath} respondió HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    } catch (t: Throwable) {
        throw RemoteError.from(t, httpUrl.host)
    }
}

private val JSON = "application/json; charset=utf-8".toMediaType()
