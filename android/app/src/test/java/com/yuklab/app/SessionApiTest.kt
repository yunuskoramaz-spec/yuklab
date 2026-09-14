package com.yuklab.app

import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.Dispatcher
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class SessionApiTest {
    private val server=MockWebServer().apply { start() }
    private val base=server.url("/").toString().trimEnd('/')
    private class MemoryStore(var value:JSONObject?) : TokenStore {
        override fun read()=value
        override fun replace(expectedRefreshToken:String,value:JSONObject):Boolean {
            if(this.value?.optString("refreshToken")!=expectedRefreshToken) return false
            this.value=value; return true
        }
    }
    private val store=MemoryStore(JSONObject().put("server",base).put("accessToken","expired").put("refreshToken","old-refresh"))
    private val api=SessionApi(ApiTransport(),store)
    private fun response(status:Int,json:String)=MockResponse().setResponseCode(status).setBody(json)
    @After fun stop() { server.shutdown() }
    @Test fun refreshRotatesTokenBeforeRetryAndPreservesPostBody() {
        server.enqueue(response(401,"{\"error\":\"UNAUTHORIZED\"}"))
        server.enqueue(response(200,"{\"accessToken\":\"renewed\",\"refreshToken\":\"new-refresh\"}"))
        server.enqueue(response(201,"{\"id\":\"order-1\"}"))
        assertTrue(api.call(base,"/orders","POST",JSONObject().put("pickupAddress","Kayseri")).ok)
        val original=server.takeRequest(); val refresh=server.takeRequest(); val retry=server.takeRequest()
        assertEquals("Bearer expired",original.getHeader("Authorization")); assertEquals("Bearer renewed",retry.getHeader("Authorization"))
        assertEquals(original.body.readUtf8(),retry.body.readUtf8()); assertEquals("/orders",retry.path)
        assertEquals("/v1/auth/refresh",refresh.path); assertNull(refresh.getHeader("Authorization")); assertEquals("old-refresh",JSONObject(refresh.body.readUtf8()).getString("refreshToken"))
        assertEquals(3,server.requestCount); assertEquals("new-refresh",store.read()?.getString("refreshToken"))
    }
    @Test fun refreshFailureDoesNotRepeatMutationOrEraseStoredToken() {
        server.enqueue(response(401,"{}")); server.enqueue(response(503,"{\"error\":\"unavailable\"}"))
        assertEquals(503,api.call(base,"/orders","POST",JSONObject()).status); assertEquals(2,server.requestCount); assertEquals("old-refresh",store.read()?.optString("refreshToken"))
    }
    @Test fun malformedRefreshCannotMasqueradeAsSuccessfulOrder() {
        server.enqueue(response(401,"{}")); server.enqueue(response(200,"{}"))
        assertEquals(502,api.call(base,"/orders","POST",JSONObject()).status)
    }
    @Test fun logoutDuringRefreshDoesNotRestoreSessionOrRetry() {
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.path=="/v1/auth/refresh") { store.value=null; return response(200,"{\"accessToken\":\"renewed\",\"refreshToken\":\"new-refresh\"}") }
                return response(401,"{}")
            }
        }
        assertEquals(401,api.call(base,"/orders","POST",JSONObject()).status); assertNull(store.read()); assertEquals(2,server.requestCount)
    }
}
