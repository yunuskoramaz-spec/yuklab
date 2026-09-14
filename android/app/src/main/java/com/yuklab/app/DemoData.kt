package com.yuklab.app

import org.json.JSONObject
import org.json.JSONArray

/** Only used with an explicit, persistent 'Örnek veriler' banner. Never sent to the API. */
object DemoData {
    private fun person()=JSONObject("""{"id":"demo-driver","firstName":"Örnek","lastName":"Taşıyıcı","role":"DRIVER","companyName":"Örnek Nakliyat","city":"Kayseri","district":"Melikgazi","services":"Şehirler arası · Parsiyel · Yük taşıma","bio":"Bu profil arayüz tanıtımı için örnektir.","rating":0,"completedJobs":0,"isOnline":true,"isAvailable":true,"createdAt":"2026-01-01T00:00:00Z"}""")
    private fun orders()=JSONArray().apply {
        val routes=listOf("Kayseri" to "İzmir","Bursa" to "Gaziantep","Mersin" to "Konya")
        routes.forEachIndexed { i,r -> put(JSONObject().put("id","demo-$i").put("status",if(i==1) "DRIVER_ASSIGNED" else "PUBLISHED").put("serviceType","LOAD").put("pickupAddress",r.first).put("deliveryAddress",r.second).put("budgetMinor",listOf("3000000","1400000","1520000")[i]).put("currency","TRY").put("createdAt","2026-09-14T09:00:00Z").put("payload",JSONObject().put("pickupCity",r.first).put("deliveryCity",r.second).put("weightKg",15000-i*1000).put("loadType",if(i==2) "Parsiyel" else "Komple").put("cargoDescription","Örnek yük ilanı").put("vehicleTypes",JSONArray().put("TIR")))) }
    }
    fun response(path:String):JSONObject=when {
        path=="/v1/orders" || path=="/v1/provider/orders" -> JSONObject().put("orders",orders())
        path=="/v1/contacts" -> JSONObject().put("contacts",JSONArray().put(JSONObject().put("contactId","demo-driver").put("note","Örnek portföy notu").put("person",person())))
        path=="/v1/directory/providers" -> JSONObject().put("providers",JSONArray().put(person()))
        path.startsWith("/v1/directory/providers/") -> JSONObject().put("person",person())
        path.endsWith("/vehicles") -> JSONObject().put("vehicles",JSONArray().put(JSONObject("""{"id":"demo-vehicle","type":"TIR","subtype":"TENTELI","capacityKg":20000,"volumeM3":80,"active":true,"refrigerated":false,"details":{"brandModel":"Örnek çekici","year":2022,"ownership":"Kendime ait","city":"Kayseri","destination":"İzmir","bodyType":"Tenteli"}}""").put("owner",person())))
        path.endsWith("/matches") -> JSONObject().put("matches",JSONArray().put(JSONObject("""{"providerId":"demo-driver","score":92,"distanceKm":12,"vehicleType":"TIR","capacityKg":20000,"rating":0}""")))
        path.endsWith("/offers") -> JSONObject().put("offers",JSONArray())
        path=="/v1/providers/me" -> JSONObject("""{"provider":{"isOnline":true,"isAvailable":true,"rating":0,"completedJobs":0}}""")
        path.endsWith("/history") -> JSONObject().put("events",JSONArray())
        else -> JSONObject()
    }
}
