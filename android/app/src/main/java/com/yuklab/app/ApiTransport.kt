package com.yuklab.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

data class ApiResult(val status: Int, val data: JSONObject) {
    val ok: Boolean get() = status in 200..299
}

/** Blocking transport; callers must use a worker thread. Never follows credentialed redirects. */
class ApiTransport {
    fun call(server: String, path: String, method: String = "GET", body: JSONObject? = null, token: String? = null): ApiResult {
        val connection = URI(server + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            // Opening outputStream connects the socket: set EVERY header first.
            if (!token.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status == 204) return ApiResult(status, JSONObject())
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val data = try { JSONObject(raw) } catch (_: Exception) {
                return ApiResult(if (status in 200..299) 502 else status,
                    JSONObject().put("error", "Sunucudan geçerli bir yanıt alınamadı (HTTP $status)."))
            }
            return ApiResult(status, data)
        } finally { connection.disconnect() }
    }

    companion object {
        fun normalizeServer(value: String): String {
            val uri = URI(value.trim())
            require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
                "Sunucu adresi https:// ile başlamalı; kullanıcı bilgisi, sorgu veya # içermemeli."
            }
            return uri.toASCIIString().trimEnd('/')
        }
    }
}
