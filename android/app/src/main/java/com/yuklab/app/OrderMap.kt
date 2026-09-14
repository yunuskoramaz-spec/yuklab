package com.yuklab.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.*

@SuppressLint("SetJavaScriptEnabled")
class OrderMap(context:Context,lat:Double,lng:Double):WebView(context) {
    init {
        require(lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0)
        settings.javaScriptEnabled=true
        settings.allowFileAccess=false
        settings.allowContentAccess=false
        settings.domStorageEnabled=false
        settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.userAgentString="YukLab/2.2 (Android; https://github.com/yunuskoramaz-spec/yuklab)"
        webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                if(request.isForMainFrame && request.url.scheme=="https") {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW,request.url)) } catch(_:Exception) {}
                }
                return true
            }
        }
        val css=context.assets.open("leaflet.css").bufferedReader().use { it.readText() }
        val js=context.assets.open("leaflet.js").bufferedReader().use { it.readText() }
        val html="""<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>$css html,body,#map{margin:0;width:100%;height:100%;background:#eaf0f3}.leaflet-control-attribution{font-size:10px}</style></head><body><div id="map"></div><script>$js</script><script>
        const map=L.map('map',{scrollWheelZoom:false}).setView([$lat,$lng],12);
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'}).addTo(map);
        L.circleMarker([$lat,$lng],{radius:9,color:'#ffffff',weight:3,fillColor:'#0ea28b',fillOpacity:1}).addTo(map).bindPopup('Alım noktası');
        </script></body></html>"""
        loadDataWithBaseURL("https://github.com/yunuskoramaz-spec/yuklab/",html,"text/html","UTF-8",null)
        contentDescription="Alım noktası haritası"
    }
}
