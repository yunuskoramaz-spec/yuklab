package com.yuklab.app

import org.json.JSONObject

interface TokenStore {
    fun read(): JSONObject?
    fun replace(expectedRefreshToken: String, value: JSONObject): Boolean
}

/** Refresh only after a rejected authentication; never replay ambiguous network failures. */
class SessionApi(private val transport: ApiTransport, private val store: TokenStore) {
    @Synchronized fun call(server: String, path: String, method: String, body: JSONObject?): ApiResult {
        val session = store.read()?.takeIf { it.optString("server") == server }
            ?: return ApiResult(401, JSONObject().put("error", "UNAUTHORIZED"))
        val response = transport.call(server, path, method, body, session.optString("accessToken"))
        if (response.status != 401) return response
        val refreshToken = session.optString("refreshToken")
        if (refreshToken.isBlank()) return response
        val refresh = transport.call(server, "/v1/auth/refresh", "POST", JSONObject().put("refreshToken", refreshToken))
        if (!refresh.ok) return refresh
        if (refresh.data.optString("accessToken").isBlank() || refresh.data.optString("refreshToken").isBlank()) {
            return ApiResult(502, JSONObject().put("error", "Sunucunun oturum yenileme yanıtı geçersiz."))
        }
        val next = JSONObject(session.toString())
            .put("accessToken", refresh.data.getString("accessToken"))
            .put("refreshToken", refresh.data.getString("refreshToken"))
        // Do not overwrite a logout, server switch or new login that happened while refreshing.
        if (!store.replace(refreshToken, next)) return ApiResult(401, JSONObject().put("error", "UNAUTHORIZED"))
        return transport.call(server, path, method, body, next.getString("accessToken"))
    }
}
