package com.yuklab.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("yuklab", Context.MODE_PRIVATE) }
    private var token: String? = null
    private var server: String = ""
    private val pad = 16

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); token=prefs.getString("token",null); server=prefs.getString("server","https://yuklab-api.onrender.com")?:"https://yuklab-api.onrender.com"; showHome() }

    private fun base(title:String):LinearLayout{
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(248,249,250))}
        val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(12));setBackgroundColor(Color.WHITE)}
        val t=TextView(this).apply{text=title;textSize=21f;setTextColor(Color.rgb(20,20,20));setTypeface(null,android.graphics.Typeface.BOLD)}
        bar.addView(t,LinearLayout.LayoutParams(0,dp(56),1f));root.addView(bar)
        val scroll=ScrollView(this);val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(pad),dp(pad),dp(pad),dp(90))};scroll.addView(body);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));setContentView(root);return body
    }

    private fun showHome(){val b=base("YÜKLAB");text(b,"Akıllı lojistik ağı",28,true);text(b,"Yük taşıma, kurye ve acil yardım işlemlerini tek Android uygulamasından yönet.",16,false);button(b,"Taşıma talebi oluştur"){showCreateOrder()};button(b,"Siparişlerim"){showOrders()};button(b,"Provider Hub"){showProvider()};button(b,"Ayarlar"){showSettings()};if(token==null)button(b,"Giriş / Kayıt"){showLogin()}else{ text(b,"Oturum açık",14,true);button(b,"Çıkış yap"){prefs.edit().remove("token").apply();token=null;showHome()} }}

    private fun showLogin(){val b=base("Giriş");val identifier=input(b,"E-posta veya telefon");val password=input(b,"Şifre",true);button(b,"Giriş yap"){if(server.isBlank()){toast("Önce Ayarlar > Sunucu adresinden API adresini girin.");return@button};request("/v1/auth/login","POST",JSONObject().put("identifier",identifier.text.toString()).put("password",password.text.toString()),null){ok,data->if(ok){token=data.optString("accessToken");prefs.edit().putString("token",token).apply();showHome()}else toast(data.optString("error","Giriş başarısız"))}};button(b,"Hesap oluştur"){showRegister()};button(b,"Geri"){showHome()}}

    private fun showRegister(){val b=base("Yeni hesap");val first=input(b,"Ad");val last=input(b,"Soyad");val email=input(b,"E-posta");val pass=input(b,"Şifre",true);button(b,"Kayıt ol"){val body=JSONObject().put("firstName",first.text.toString()).put("lastName",last.text.toString()).put("email",email.text.toString()).put("password",pass.text.toString());request("/v1/auth/register","POST",body,null){ok,data->if(ok){toast("Hesap oluşturuldu. Giriş yapabilirsiniz.");showLogin()}else toast(data.optString("error","Kayıt başarısız"))}};button(b,"Geri"){showLogin()}}

    private fun showCreateOrder(){if(token==null){showLogin();return};val b=base("Taşıma talebi");val service=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("LOAD","COURIER","EMERGENCY"))};b.addView(label("Hizmet"));b.addView(service,lp());val pickup=input(b,"Nereden? Pickup adresi");val delivery=input(b,"Nereye? Teslimat adresi");val weight=input(b,"Ağırlık (kg)");val volume=input(b,"Hacim (m³)");val vehicle=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("ANY","MOTORCYCLE","VAN","TRUCK","TRACTOR_TRAILER"))};b.addView(label("Araç tipi"));b.addView(vehicle,lp());val budget=input(b,"Bütçe (TL)");val refrigerated=CheckBox(this).apply{text="Soğutuculu araç gerekli"};b.addView(refrigerated);button(b,"Mevcut konumumu kullan"){location{lat,lng->pickup.setText("$lat,$lng")}};button(b,"Talep oluştur"){if(pickup.text.isBlank()){toast("Pickup adresi gerekli");return@button};val payload=JSONObject().apply{if(weight.text.isNotBlank())put("weightKg",weight.text.toString().toDoubleOrNull());if(volume.text.isNotBlank())put("volumeM3",volume.text.toString().toDoubleOrNull());put("vehicleType",vehicle.selectedItem.toString());put("refrigerated",refrigerated.isChecked)};val body=JSONObject().put("serviceType",service.selectedItem.toString()).put("pickupAddress",pickup.text.toString()).put("deliveryAddress",delivery.text.toString()).put("currency","TRY").put("payload",payload);if(budget.text.isNotBlank())body.put("budgetMinor",((budget.text.toString().toDoubleOrNull()?:0.0)*100).toLong());request("/v1/orders","POST",body,token){ok,data->if(ok){toast("Talep oluşturuldu");showOrders()}else toast(data.optString("error","Talep oluşturulamadı"))}};button(b,"Ana sayfa"){showHome()}}

    private fun showOrders(){if(token==null){showLogin();return};val b=base("Siparişlerim");text(b,"Veriler sunucudan alınır.",14,false);request("/v1/orders","GET",null,token){ok,data->runOnUiThread{if(!ok){toast(data.optString("error","Siparişler alınamadı"));return@runOnUiThread};val arr=data.optJSONArray("orders")?:return@runOnUiThread;for(i in 0 until arr.length()){val o=arr.getJSONObject(i);val card=TextView(this).apply{text="${o.optString("serviceType")}  •  ${o.optString("status")}\n${o.optString("pickupAddress")} → ${o.optString("deliveryAddress")}\n${o.optString("createdAt")}";textSize=16f;setTextColor(Color.DKGRAY);setPadding(dp(14),dp(14),dp(14),dp(14));setBackgroundColor(Color.WHITE)};b.addView(card,lp(dp(0),dp(10)));button(b,"Eşleşmeleri gör"){showMatches(o.optString("id"))}}}};button(b,"Yenile"){showOrders()};button(b,"Ana sayfa"){showHome()}}

    private fun showMatches(orderId:String){val b=base("Akıllı eşleşme");request("/v1/orders/$orderId/matches","GET",null,token){ok,data->runOnUiThread{if(!ok){toast(data.optString("error","Eşleşme alınamadı"));return@runOnUiThread};val arr=data.optJSONArray("matches")?:return@runOnUiThread;for(i in 0 until arr.length()){val m=arr.getJSONObject(i);text(b,"Skor: ${m.optDouble("score")}  •  ${m.optDouble("distanceKm")} km  •  Puan: ${m.optDouble("rating")}\nAraç: ${m.optString("vehicleType","-")} ${m.optString("vehicleSubtype","")}",16,false)}}};button(b,"Geri"){showOrders()}}

    private fun showProvider(){if(token==null){showLogin();return};val b=base("Provider Hub");text(b,"Sağlayıcı işlemleri",24,true);button(b,"Provider siparişleri"){providerOrders()};button(b,"Tekliflerim"){providerOffers()};button(b,"Araçlarım"){vehicles()};button(b,"Sağlayıcı hesabını etkinleştir"){request("/v1/auth/become-provider","POST",JSONObject().put("category","GENERAL"),token){ok,data->toast(if(ok)"Provider hesabı etkinleştirildi" else data.optString("error","İşlem başarısız"))}};button(b,"Ana sayfa"){showHome()}}
    private fun providerOrders(){val b=base("Provider siparişleri");request("/v1/provider/orders","GET",null,token){ok,data->runOnUiThread{if(!ok){toast(data.optString("error"));return@runOnUiThread};val a=data.optJSONArray("orders")?:return@runOnUiThread;for(i in 0 until a.length()){val o=a.getJSONObject(i);text(b,"${o.optString("serviceType")} • ${o.optString("status")}\n${o.optString("pickupAddress")} → ${o.optString("deliveryAddress")}",16,false)}}};button(b,"Geri"){showProvider()}}
    private fun providerOffers(){val b=base("Tekliflerim");request("/v1/provider/offers","GET",null,token){ok,data->runOnUiThread{if(!ok){toast(data.optString("error"));return@runOnUiThread};val a=data.optJSONArray("offers")?:return@runOnUiThread;for(i in 0 until a.length()){val o=a.getJSONObject(i);text(b,"${o.optString("status")} • ${(o.optLong("amountMinor")/100.0)} ${o.optString("currency")}\n${o.optString("note","")}",16,false)}}};button(b,"Geri"){showProvider()}}
    private fun vehicles(){val b=base("Araçlarım");request("/v1/vehicles","GET",null,token){ok,data->runOnUiThread{if(!ok){toast(data.optString("error"));return@runOnUiThread};val a=data.optJSONArray("vehicles")?:return@runOnUiThread;for(i in 0 until a.length()){val v=a.getJSONObject(i);text(b,"${v.optString("type")} ${v.optString("subtype","")}\nPlaka: ${v.optString("plateNumber","-")}\nKapasite: ${v.optString("capacityKg","-")} kg • ${v.optString("volumeM3","-")} m³",16,false)}}};button(b,"Geri"){showProvider()}}

    private fun showSettings(){val b=base("Ayarlar");text(b,"Android uygulama ayarları",24,true);val s=input(b,"API sunucu adresi (örn. https://api.example.com)");s.setText(server);button(b,"Sunucu adresini kaydet"){server=s.text.toString().trim().removeSuffix("/");prefs.edit().putString("server",server).apply();toast("Kaydedildi")};text(b,"Bu APK web arayüzü kullanmaz. Sunucu adresi yalnızca API bağlantısı için kullanılır.",14,false);button(b,"Konum izni"){if(android.os.Build.VERSION.SDK_INT>=23)requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),42)};button(b,"Ana sayfa"){showHome()}}

    private fun request(path:String,method:String,body:JSONObject?,bearer:String?,done:(Boolean,JSONObject)->Unit){if(server.isBlank()){runOnUiThread{toast("API sunucu adresi ayarlanmadı")};return};executor.execute{try{val c=URL(server+path).openConnection() as HttpURLConnection;c.requestMethod=method;c.connectTimeout=15000;c.readTimeout=20000;c.setRequestProperty("Accept","application/json");if(body!=null){c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toString().toByteArray())}};if(!bearer.isNullOrBlank())c.setRequestProperty("Authorization","Bearer $bearer");val stream=if(c.responseCode in 200..299)c.inputStream else c.errorStream;val txt=stream?.bufferedReader()?.readText()?:"{}";done(c.responseCode in 200..299,JSONObject(txt));c.disconnect()}catch(e:Exception){done(false,JSONObject().put("error",e.message?:"Bağlantı hatası"))}}}
    private fun location(done:(Double,Double)->Unit){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),42);toast("Konum izni verin");return};val lm=getSystemService(LOCATION_SERVICE) as LocationManager;val p=lm.getProviders(true).firstOrNull()?:return toast("Konum servisi kapalı");val l:Location?=try{lm.getLastKnownLocation(p)}catch(_:Exception){null};if(l!=null)done(l.latitude,l.longitude)else toast("Konum henüz hazır değil")}
    private fun input(parent:LinearLayout,hint:String,password:Boolean=false):EditText{val e=EditText(this).apply{this.hint=hint;textSize=16f;setPadding(dp(12),dp(8),dp(12),dp(8));if(password)inputType=0x81};parent.addView(e,lp());return e}
    private fun button(parent:LinearLayout,label:String,onClick:()->Unit){val v=Button(this).apply{text=label;textSize=15f;setOnClickListener{onClick()}};parent.addView(v,lp())}
    private fun text(parent:LinearLayout,value:String,size:Number,bold:Boolean){val v=TextView(this).apply{text=value;textSize=size.toFloat();setTextColor(Color.rgb(25,25,25));if(bold)setTypeface(null,android.graphics.Typeface.BOLD);setPadding(0,dp(8),0,dp(8))};parent.addView(v,lp())}
    private fun label(value:String)=TextView(this).apply{text=value;textSize=14f;setTextColor(Color.DKGRAY);setPadding(0,dp(6),0,0)}
    private fun lp(w:Int=-1,h:Int=-2)=LinearLayout.LayoutParams(w,h).apply{setMargins(0,0,0,dp(4))}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun toast(s:String){runOnUiThread{Toast.makeText(this,s,Toast.LENGTH_LONG).show()}}
}
