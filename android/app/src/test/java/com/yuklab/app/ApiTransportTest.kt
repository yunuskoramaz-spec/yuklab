package com.yuklab.app

import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ApiTransportTest {
    private val server=MockWebServer().apply { start() }
    private val base=server.url("/").toString().trimEnd('/')
    private val transport=ApiTransport()
    @After fun stop() { server.shutdown() }
    @Test fun postIncludesAuthorizationBeforeBodyAndPreservesUtf8() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{\"order\":{\"id\":\"1\"}}"))
        val result=transport.call(base,"/orders","POST",JSONObject().put("address","Kayseri, Güneş Sokak"),"test-token")
        val request=server.takeRequest()
        assertTrue(result.ok); assertEquals("Bearer test-token",request.getHeader("Authorization")); assertEquals("Kayseri, Güneş Sokak",JSONObject(request.body.readUtf8()).getString("address"))
    }
    @Test fun acceptsNoContentForLocationAndLogout() {
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(transport.call(base,"/location","POST",JSONObject(),"token").ok)
    }
    @Test fun htmlResponseIsReportedAsFailure() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>Unavailable</html>"))
        val result=transport.call(base,"/html"); assertFalse(result.ok); assertEquals(502,result.status)
    }
    @Test fun redirectsAreNotFollowedWithCredentials() {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location","$base/other"))
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(302,transport.call(base,"/redirect",token="secret").status); assertEquals(1,server.requestCount)
    }
    @Test fun unauthorizedJsonPreservesStatusForRefresh() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"error\":\"UNAUTHORIZED\"}"))
        assertEquals(401,transport.call(base,"/private").status)
    }
    @Test fun serverUrlValidationRejectsUnsafeDestinations() {
        for(value in listOf("http://example.com","https://user:pass@example.com","https://example.com?x=1","https://example.com/#x","garbage")) {
            try { ApiTransport.normalizeServer(value); fail(value) } catch (_:IllegalArgumentException) {}
        }
        assertEquals("https://example.com/api",ApiTransport.normalizeServer(" https://example.com/api/ "))
    }
    @Test fun moneyUsesExactMinorUnitsAndTurkishDecimals() {
        assertEquals("1001",Amounts.minor("10,01",true)); assertEquals("29",Amounts.minor("0.29",true)); assertEquals("0",Amounts.minor("0",false)); assertEquals("10,01",Amounts.display("1001"))
        for(value in listOf("-1","0","1.001","NaN","9223372036854775808")) {
            try { Amounts.minor(value,true); fail(value) } catch (_:IllegalArgumentException) {}
        }
    }
}
