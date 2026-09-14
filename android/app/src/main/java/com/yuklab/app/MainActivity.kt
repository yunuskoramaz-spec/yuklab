package com.yuklab.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("yuklab", Context.MODE_PRIVATE) }
    private val sessions by lazy { SessionStore(this) }
    private val transport = ApiTransport()
    private val sessionApi by lazy { SessionApi(transport,sessions) }
    private var session: JSONObject? = null
    private var activeMap:OrderMap?=null
    private var demoMode=false
    private var demoProvider=false
    private var saveForm:(()->Unit)?=null
    private var server = ""
    @Volatile private var screen = 0
    private var fieldId = 100
    private var inFlight = 0
    private val authEpoch = java.util.concurrent.atomic.AtomicInteger()
    private var back: (() -> Unit)? = null
    private lateinit var root: LinearLayout
    private var pendingLocation: ((Location) -> Unit)? = null
    private var listener: LocationListener? = null
    private val services = linkedMapOf("LOAD" to "Yük taşıma", "COURIER" to "Kurye", "EMERGENCY" to "Yol yardım")
    private val vehicles = linkedMapOf("MOTORCYCLE" to "Motosiklet", "VAN" to "Kamyonet", "TRUCK" to "Kamyon", "KIRKAYAK" to "Kırkayak", "TIR" to "Tır", "TOW_TRUCK" to "Çekici", "REFRIGERATED" to "Soğutuculu", "DUMP_TRUCK" to "Damperli", "FLATBED" to "Açık kasa", "LOWBED" to "Lowbed")
    private val states = mapOf("PUBLISHED" to "Yayında", "OFFERING" to "Teklif alıyor", "DRIVER_ASSIGNED" to "Sürücü atandı", "EN_ROUTE_PICKUP" to "Alım adresine gidiyor", "ARRIVED_PICKUP" to "Alım adresinde", "LOADED" to "Yük alındı", "IN_TRANSIT" to "Taşınıyor", "ARRIVED_DELIVERY" to "Teslimat adresinde", "DELIVERED" to "Teslim edildi", "COMPLETED" to "Tamamlandı", "CANCELLED" to "İptal edildi", "PENDING" to "Bekliyor", "ACCEPTED" to "Kabul edildi", "REJECTED" to "Reddedildi", "EXPIRED" to "Süresi doldu", "WITHDRAWN" to "Geri çekildi")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        server = prefs.getString("server", "https://yuklab-api.onrender.com").orEmpty()
        // Drop legacy plaintext credentials; require a new login after this upgrade.
        prefs.edit().remove("token").apply()
        session = sessions.read()?.takeIf { it.optString("server") == server }
        showHome()
    }

    private fun base(title:String,tab:String?=null,onBack:(()->Unit)?={showHome()}):LinearLayout {
        saveForm?.invoke(); saveForm=null; activeMap?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }; activeMap=null; stopLocation(); screen++; inFlight=0; fieldId=100; back=onBack
        root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setBackgroundColor(Palette.background)
            setOnApplyWindowInsetsListener { view,insets ->
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
                insets
            }
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        @Suppress("DEPRECATION")
        window.statusBarColor=Color.WHITE
        @Suppress("DEPRECATION")
        window.navigationBarColor=Color.WHITE
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setBackgroundColor(Color.WHITE); setPadding(dp(20),dp(10),dp(20),dp(12)) }
        if(onBack!=null && tab==null) header.addView(Glyph(this,"back").apply { contentDescription="Geri"; setPadding(dp(10),dp(10),dp(10),dp(10)); setOnClickListener { onBack() } },LinearLayout.LayoutParams(dp(44),dp(44)))
        val titleBox=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        header.addView(titleBox,LinearLayout.LayoutParams(0,-2,1f))
        text(titleBox,if(tab=="home") "YÜKLAB" else title,if(tab=="home") 26 else 23,true)
        if(tab=="home") text(titleBox,if(isProvider()) "TAŞIYICI MERKEZİ" else "YÜK VE TAŞIMA PLATFORMU",10)
        header.addView(Glyph(this,if(tab=="profile") "user" else "truck",Palette.accent),LinearLayout.LayoutParams(dp(28),dp(28)))
        root.addView(header)
        if(demoMode) root.addView(TextView(this).apply { text="TANITIM MODU · TÜM KAYITLAR ÖRNEKTİR"; gravity=Gravity.CENTER; textSize=10f; setTextColor(Palette.ink); setBackgroundColor(Palette.soft); setPadding(0,dp(8),0,dp(8)) })
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(18),dp(20),dp(24)) }
        root.addView(ScrollView(this).apply { isFillViewport=true; addView(body) },LinearLayout.LayoutParams(-1,0,1f))
        if(tab!=null) {
            val nav=LinearLayout(this).apply { setBackgroundColor(Color.WHITE); gravity=Gravity.CENTER; setPadding(dp(8),dp(8),dp(8),dp(8)) }
            val labels=listOf(Triple("home","home","Panel"),Triple("loads","list","İlanlar"),Triple("new","plus","Oluştur"),Triple("contacts","contacts","Portföy"),Triple("profile","user","Profil"))
            for((id,icon,label) in labels) {
                val active=tab==id
                val cell=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; minimumHeight=dp(56); contentDescription=label; isClickable=true; isFocusable=true }
                val glyph=Glyph(this,icon,if(active || id=="new") Palette.accent else Palette.muted)
                cell.addView(glyph,LinearLayout.LayoutParams(dp(24),dp(24)))
                cell.addView(TextView(this).apply { text=label; textSize=10f; gravity=Gravity.CENTER; setPadding(0,dp(5),0,0); setTextColor(if(active) Palette.ink else Palette.muted); if(active) setTypeface(null,android.graphics.Typeface.BOLD) })
                cell.setOnClickListener { if(id!=tab) when(id) { "home"->showHome(); "loads"->showOrders(isProvider()); "new"->showCreateMenu(); "contacts"->showPortfolio(); else->showProfile() } }
                nav.addView(cell,LinearLayout.LayoutParams(0,-2,1f))
            }
            root.addView(nav)
        }
        setContentView(root); root.requestApplyInsets(); return body
    }
    @Deprecated("Legacy back callback") override fun onBackPressed() { back?.invoke() ?: super.onBackPressed() }

    private fun showHome() {
        val b=base("YÜKLAB",tab="home",onBack=null)
        val name=session?.optJSONObject("user")?.optString("firstName").orEmpty()
        text(b,if(name.isBlank()) "Hoş geldin." else "Merhaba, $name",14)
        val hero=card(b).apply { background=Palette.shape(Palette.ink,dp(24).toFloat()); setPadding(dp(22),dp(20),dp(22),dp(22)) }
        hero.addView(TextView(this).apply { text=if(isProvider()) "DAHA AZ BOŞ YOL" else "HER YÜKE BİR YOL"; textSize=10f; letterSpacing=.15f; setTextColor(Color.rgb(119,221,201)); setPadding(0,0,0,dp(14)) })
        hero.addView(TextView(this).apply { text=if(isProvider()) "Bir sonraki yükün\nburada." else "Yükünü kolayca\nyola çıkar."; textSize=30f; setTextColor(Color.WHITE); setTypeface(null,android.graphics.Typeface.BOLD) })
        hero.addView(TextView(this).apply { text=if(isProvider()) "Aracına uygun işleri bul, teklif ver,\nsevkiyatlarını tek yerden yönet." else "İlanını oluştur, taşıyıcılarla eşleş,\ntekliflerini tek yerden yönet."; textSize=14f; setTextColor(Color.rgb(198,219,228)); setLineSpacing(dp(3).toFloat(),1f); setPadding(0,dp(12),0,dp(14)) })
        button(hero,if(isProvider()) "Uygun yükleri keşfet  →" else "Yeni ilan oluştur  +") { if(isProvider()) showOrders(true) else showCreateMenu() }
        if(session==null && !demoMode) {
            actionCard(b,"user","Giriş yap veya hesap oluştur","İlanlarını ve tekliflerini hesabında sakla") { showLogin() }
            secondary(b,"Örnek ekranları incele") { demoMode=true; showHome() }
        }
        text(b,"Hızlı işlemler",19,true)
        val grid=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }; b.addView(grid,lp())
        val left=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; val right=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        grid.addView(left,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(6) }); grid.addView(right,LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(6) })
        miniCard(left,"list",if(isProvider()) "Araçlarım" else "İlanlarım",if(isProvider()) "Araç ve kapasite" else "Yayındaki yüklerin") { if(isProvider()) showVehicles() else showOrders() }
        miniCard(right,"truck","Boş araçlar","Müsait taşıyıcılar") { showDirectory(true) }
        miniCard(left,"contacts","Nakliyeciler","Firma ve sürücüler") { showDirectory(false) }
        miniCard(right,"calculator","Sefer maliyeti","Yakıt ve gider hesabı") { showCost() }
        actionCard(b,"list","Metinden hızlı ilan","Bir yük listesini taslaklara dönüştür") { showBulk() }
        if(isProvider()) actionCard(b,"check","Müsaitlik ve konum","Çevrimiçi durumunu ve iş alanını yönet") { showProvider() }
        if(demoMode) secondary(b,"Tanıtımdan çık") { demoMode=false; showHome() }
    }
    private fun isProvider()=if(demoMode) demoProvider else session?.optJSONObject("user")?.optString("role") in listOf("DRIVER","SERVICE_PROVIDER")

    private fun authenticated(): Boolean { if(demoMode || session!=null) return true; showLogin(); return false }
    private fun showLogin() {
        demoMode=false
        val b=base("Giriş")
        val identifier=input(b,"E-posta veya telefon"); val password=input(b,"Şifre",password=true)
        button(b,"Giriş yap") {
            if(identifier.text.isBlank() || password.text.isBlank()) { toast("E-posta/telefon ve şifre gerekli."); return@button }
            request("/v1/auth/login","POST",JSONObject().put("identifier",identifier.text.toString().trim()).put("password",password.text.toString()),false) { data ->
                if(data.optString("accessToken").isBlank() || data.optString("refreshToken").isBlank()) { toast("Sunucunun oturum yanıtı geçersiz."); return@request }
                try { authEpoch.incrementAndGet(); session=data.put("server",server); sessions.save(data); showHome() }
                catch (_: Exception) { session=null; toast("Güvenli oturum kaydedilemedi. Yeniden deneyin.") }
            }
        }
        button(b,"Hesap oluştur") { showRegister() }
    }
    private fun showRegister() {
        val b=base("Yeni hesap") { showLogin() }
        val first=input(b,"Ad"); val last=input(b,"Soyad"); val email=input(b,"E-posta"); val password=input(b,"Şifre (en az 8 karakter)",password=true)
        button(b,"Kayıt ol") {
            if(first.text.isBlank() || last.text.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email.text.toString().trim()).matches() || password.text.length !in 8..128) { toast("Ad, soyad, geçerli e-posta ve 8–128 karakterlik şifre gerekli."); return@button }
            request("/v1/auth/register","POST",JSONObject().put("firstName",first.text.toString().trim()).put("lastName",last.text.toString().trim()).put("email",email.text.toString().trim()).put("password",password.text.toString()),false) { toast("Hesap oluşturuldu. Giriş yapabilirsiniz."); showLogin() }
        }
    }
    private fun showCreateOrder() {
        if(!authenticated()) return
        val draft=try { JSONObject(prefs.getString(draftKey(),"{}").orEmpty()) } catch(_:Exception) { JSONObject() }
        showOrderForm(draft,0)
    }
    private fun draftKey()="draft.${server.hashCode()}.${session?.optJSONObject("user")?.optString("id") ?: "demo"}"
    private fun showCreateMenu() {
        val b=base("Yeni oluştur")
        text(b,"Nasıl başlamak istersin?",26,true)
        text(b,"Yükünü veya aracını birkaç adımda ekle.",14)
        actionCard(b,"box","Yük ilanı oluştur","Güzergâh, yük ve fiyat bilgilerini gir") { showCreateOrder() }
        actionCard(b,"list","Metinden ilan hazırla","Yapıştırdığın listeden düzenlenebilir taslaklar") { showBulk() }
        if(isProvider()) actionCard(b,"truck","Araç ekle","Araç bilgileri ve çalıştığın güzergâh") { editVehicle(null) }
        else actionCard(b,"truck","Taşıyıcı olmak istiyorum","Aracınla iş alabilmek için hesabını hazırla") { showProvider() }
    }
    private fun showOrderForm(draft:JSONObject,step:Int) {
        if(!authenticated()) return
        val b=base("Yeni yük ilanı") { if(step>0) showOrderForm(draft,step-1) else showCreateMenu() }
        chip(b,"ADIM ${step+1} / 3")
        text(b,listOf("Nereden, nereye?","Yükünü tanıyalım","Son kontrol ve fiyat")[step],25,true)
        if(step==0) {
            text(b,"İl ve adresi yaz, alım konumunu doğrula.",14)
            val pickupCity=input(b,"Alım ili").apply { setText(draft.optString("pickupCity")) }
            val pickup=input(b,"Alım ilçesi / açık adres").apply { setText(draft.optString("pickupAddress")) }
            val deliveryCity=input(b,"Teslimat ili").apply { setText(draft.optString("deliveryCity")) }
            val delivery=input(b,"Teslimat ilçesi / açık adres").apply { setText(draft.optString("deliveryAddress")) }
            val loc=TextView(this).apply { textSize=12f; setTextColor(Palette.accent); text=if(draft.has("pickupLat")) "Alım konumu hazır" else "Eşleştirme için alım konumu gerekli" }; b.addView(loc,lp())
            val sync={ draft.put("pickupCity",pickupCity.text.toString().trim()).put("pickupAddress",pickup.text.toString().trim()).put("deliveryCity",deliveryCity.text.toString().trim()).put("deliveryAddress",delivery.text.toString().trim()); prefs.edit().putString(draftKey(),draft.toString()).apply(); Unit }
            saveForm=sync
            watch(pickupCity) { draft.remove("pickupLat"); draft.remove("pickupLng"); loc.text="Adres değişti; alım konumunu yeniden doğrula" }
            watch(pickup) { draft.remove("pickupLat"); draft.remove("pickupLng"); loc.text="Adres değişti; alım konumunu yeniden doğrula" }
            secondary(b,"Alım adresini konumlandır") {
                sync(); if(pickupCity.text.isBlank()) toast("Önce alım ilini girin.") else geocode("${pickup.text}, ${pickupCity.text}, Türkiye") { lat,lng -> draft.put("pickupLat",lat).put("pickupLng",lng); sync(); loc.text="Alım konumu: %.4f, %.4f".format(java.util.Locale.US,lat,lng) }
            }
            secondary(b,"Alım için mevcut konumumu kullan") { location { l -> draft.put("pickupLat",l.latitude).put("pickupLng",l.longitude); sync(); loc.text="Mevcut konum alım noktası olarak seçildi" } }
            secondary(b,"Koordinatları elle gir") {
                val c=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(12),dp(20),0) }; val lat=input(c,"Enlem",number=true); val lng=input(c,"Boylam",number=true)
                val dialog=AlertDialog.Builder(this).setTitle("Alım konumu").setView(c).setNegativeButton("Vazgeç",null).setPositiveButton("Kaydet",null).create()
                dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { try { val a=number(lat,true)!!; val o=number(lng,true)!!; require(a in -90.0..90.0 && o in -180.0..180.0) { "Koordinatlar geçersiz." }; draft.put("pickupLat",a).put("pickupLng",o); sync(); loc.text="Alım konumu hazır"; dialog.dismiss() } catch(e:Exception) { toast(e.message.orEmpty()) } } }; dialog.show()
            }
            button(b,"Yük bilgilerine devam  →") {
                sync(); if(pickupCity.text.isBlank() || pickup.text.isBlank() || deliveryCity.text.isBlank()) { toast("Alım ili, alım adresi ve teslimat ilini girin."); return@button }
                if(!draft.has("pickupLat")) { toast("Alım konumunu doğrulayın."); return@button }; showOrderForm(draft,1)
            }
        } else if(step==1) {
            val service=select(b,"Hizmet",services).apply { setSelection(services.keys.indexOf(draft.optString("serviceType","LOAD")).coerceAtLeast(0)) }
            val loads=linkedMapOf("Komple" to "Komple yük","Parsiyel" to "Parsiyel / parça yük")
            val load=select(b,"Taşıma şekli",loads).apply { setSelection(loads.keys.indexOf(draft.optString("loadType")).coerceAtLeast(0)) }
            val desc=input(b,"Yükün cinsi").apply { setText(draft.optString("cargoDescription")) }
            val weight=input(b,"Ağırlık (kg)",number=true).apply { if(draft.has("weightText")) setText(draft.optString("weightText")) else if(draft.has("weightKg")) setText(draft.optDouble("weightKg").toString()) }
            val volume=input(b,"Hacim (m³, isteğe bağlı)",number=true).apply { if(draft.has("volumeText")) setText(draft.optString("volumeText")) else if(draft.has("volumeM3")) setText(draft.optDouble("volumeM3").toString()) }
            val choices=linkedMapOf("ANY" to "Fark etmez").apply { putAll(vehicles) }; val vehicle=select(b,"Gerekli araç",choices).apply { setSelection(choices.keys.indexOf(draft.optString("vehicleType","ANY")).coerceAtLeast(0)) }
            val cold=CheckBox(this).apply { text="Soğutuculu araç gerekli"; isChecked=draft.optBoolean("refrigerated"); setTextColor(Palette.ink) }; b.addView(cold,lp())
            val date=TextView(this).apply { text=if(draft.has("scheduledAt")) dateLabel(draft.getString("scheduledAt")) else "En kısa sürede"; setTextColor(Palette.ink) }; b.addView(date,lp())
            secondary(b,"Yükleme tarihi seç") { val now=java.util.Calendar.getInstance(); android.app.DatePickerDialog(this,{ _,year,month,day -> val cal=java.util.Calendar.getInstance().apply { set(year,month,day,9,0,0); set(java.util.Calendar.MILLISECOND,0) }; val fmt=java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",java.util.Locale.US).apply { timeZone=java.util.TimeZone.getTimeZone("UTC") }; draft.put("scheduledAt",fmt.format(cal.time)); date.text=dateLabel(draft.getString("scheduledAt")) },now.get(java.util.Calendar.YEAR),now.get(java.util.Calendar.MONTH),now.get(java.util.Calendar.DAY_OF_MONTH)).apply { datePicker.minDate=System.currentTimeMillis()-1000 }.show() }
            val sync={ draft.put("serviceType",key(service,services)).put("loadType",key(load,loads)).put("cargoDescription",desc.text.toString().trim()).put("vehicleType",key(vehicle,choices)).put("refrigerated",cold.isChecked).put("weightText",weight.text.toString()).put("volumeText",volume.text.toString()); prefs.edit().putString(draftKey(),draft.toString()).apply(); Unit }; saveForm=sync
            button(b,"Fiyat ve özete devam  →") { try { sync(); val w=number(weight,true)!!; require(w>0 && w<=1000000) { "Ağırlık 0–1.000.000 kg arasında olmalı." }; draft.put("weightKg",w); number(volume)?.let { require(it in 0.0..100000.0); draft.put("volumeM3",it) } ?: draft.remove("volumeM3"); showOrderForm(draft,2) } catch(e:Exception) { toast(e.message ?: "Yük bilgilerini kontrol edin.") } }
        } else {
            val summary=card(b); chip(summary,draft.optString("loadType","Komple")); text(summary,"${draft.optString("pickupCity")} → ${draft.optString("deliveryCity")}",22,true)
            pair(summary,"Yük",draft.optString("cargoDescription","Belirtilmedi")); pair(summary,"Ağırlık","${draft.optDouble("weightKg")} kg"); pair(summary,"Araç",vehicles[draft.optString("vehicleType")] ?: "Fark etmez"); pair(summary,"Yükleme",if(draft.has("scheduledAt")) dateLabel(draft.getString("scheduledAt")) else "En kısa sürede")
            val budget=input(b,"Bütçe (TL, isteğe bağlı)",number=true).apply { setText(draft.optString("budget")) }
            val note=input(b,"Taşıyıcıya not").apply { setSingleLine(false); minLines=3; maxLines=5; setText(draft.optString("note")) }
            text(b,"Yayımladığın yük bilgileri uygun taşıyıcılara gösterilir. Teklifleri İlanlar bölümünden yönetebilirsin.",13)
            saveForm={ draft.put("budget",budget.text.toString()).put("note",note.text.toString()); prefs.edit().putString(draftKey(),draft.toString()).apply() }
            button(b,"İlanı yayımla") {
                try {
                    saveForm?.invoke()
                    val payload=JSONObject().put("pickupCity",draft.getString("pickupCity")).put("deliveryCity",draft.getString("deliveryCity")).put("loadType",draft.optString("loadType","Komple")).put("cargoDescription",draft.optString("cargoDescription")).put("note",note.text.toString()).put("weightKg",draft.getDouble("weightKg")).put("refrigerated",draft.optBoolean("refrigerated")).put("vehicleTypes",JSONArray().apply { val v=draft.optString("vehicleType","ANY"); if(v!="ANY") put(v) })
                    if(draft.has("volumeM3")) payload.put("volumeM3",draft.getDouble("volumeM3"))
                    val body=JSONObject().put("serviceType",draft.optString("serviceType","LOAD")).put("pickupAddress","${draft.getString("pickupAddress")}, ${draft.getString("pickupCity")}").put("deliveryAddress","${draft.optString("deliveryAddress")}, ${draft.getString("deliveryCity")}").put("pickupLat",draft.getDouble("pickupLat")).put("pickupLng",draft.getDouble("pickupLng")).put("currency","TRY").put("payload",payload)
                    if(draft.has("scheduledAt")) body.put("scheduledAt",draft.getString("scheduledAt"))
                    if(budget.text.isNotBlank()) body.put("budgetMinor",Amounts.minor(budget.text.toString(),false))
                    request("/v1/orders","POST",body) { saveForm=null; if(draft.has("importId")) { val ids=prefs.getStringSet(draftKey()+".published",emptySet()).orEmpty().toMutableSet(); ids.add(draft.getString("importId")); prefs.edit().putStringSet(draftKey()+".published",ids).apply() }; prefs.edit().remove(draftKey()).apply(); toast("İlanın yayımlandı."); showOrders() }
                } catch(e:Exception) { toast(e.message ?: "Bilgileri kontrol edin.") }
            }
            secondary(b,"Güzergâhı düzenle") { showOrderForm(draft,0) }
        }
    }
    private fun geocode(address:String,done:(Double,Double)->Unit) {
        if(!android.location.Geocoder.isPresent()) { toast("Bu telefonda adres arama yok. Konumunu kullan veya koordinat gir."); return }
        val generation=screen; toast("Adres aranıyor…")
        executor.execute {
            @Suppress("DEPRECATION")
            val results=try { android.location.Geocoder(this,java.util.Locale.forLanguageTag("tr-TR")).getFromLocationName(address,3) } catch(_:Exception) { null }
            handler.post { if(isDestroyed || screen!=generation) return@post; if(results.isNullOrEmpty()) toast("Adres bulunamadı. Daha açık yaz veya mevcut konumunu kullan.") else AlertDialog.Builder(this).setTitle("Alım konumunu seç").setItems(results.map { it.getAddressLine(0) ?: "${it.latitude}, ${it.longitude}" }.toTypedArray()) { _,index -> done(results[index].latitude,results[index].longitude) }.setNegativeButton("Vazgeç",null).show() }
        }
    }
    private fun showBulk() {
        val b=base("Metinden ilan hazırla"); text(b,"Yük listesini yapıştır",25,true)
        text(b,"Her satıra bir yük yaz. Şehirler arasında → veya -> kullan. Oluşan taslakları tek tek kontrol edip yayımlayacaksın.",14)
        val raw=input(b,"Yük listesi").apply { setSingleLine(false); minLines=6; maxLines=10; setText(prefs.getString(draftKey()+".bulk","")); hint="Kayseri → İzmir | 15 ton | 30.000 TL\nBursa → Gaziantep | 12000 kg | 14000 TL" }
        val list=column(b)
        button(b,"Taslakları hazırla") {
            prefs.edit().putString(draftKey()+".bulk",raw.text.toString()).apply(); val parsed=LogisticsTools.parse(raw.text.toString()); list.removeAllViews()
            if(parsed.drafts.isEmpty()) { empty(list,"Taslak bulunamadı","Örnekteki satır düzenini kullanabilirsin."); return@button }
            text(list,"${parsed.drafts.size} taslak hazır",19,true)
            if(parsed.rejected.isNotEmpty()) text(list,"${parsed.rejected.size} satır anlaşılamadı. Bu satırları yukarıda düzenleyebilirsin.",13)
            val published=prefs.getStringSet(draftKey()+".published",emptySet()).orEmpty()
            parsed.drafts.forEach { draft -> val importId=java.security.MessageDigest.getInstance("SHA-256").digest(draft.optString("note").toByteArray()).joinToString("") { "%02x".format(it) }; draft.put("importId",importId); if(importId in published) { text(list,"${draft.getString("pickupCity")} → ${draft.getString("deliveryCity")} · Yayımlandı",13); return@forEach }; actionCard(list,"box","${draft.getString("pickupCity")} → ${draft.getString("deliveryCity")}","Bilgileri kontrol et ve tamamla") { if(authenticated()) showOrderForm(draft,0) } }
        }
        text(b,"Bu araç biçimlendirilmiş metni ayrıştırır. En fazla 50 satır işlenir; otomatik yayımlama yapılmaz.",12)
    }

    private fun showOrders(provider:Boolean=false) {
        if(!authenticated()) return
        val b=base(if(provider) "Yükler ve sevkiyatlar" else "İlanlarım",tab="loads")
        text(b,if(provider) "Sana uygun işler" else "Yüklerin kontrolün altında",23,true)
        text(b,if(provider) "Araç, konum ve müsaitliğine göre eşleşen yükler." else "Son 50 ilanını burada yönetebilirsin.",13)
        val filter=select(b,"Görünüm",if(provider) linkedMapOf("all" to "Tümü","open" to "Uygun yükler","active" to "Aktif sevkiyatlar") else linkedMapOf("all" to "Tümü","active" to "Aktif ilanlar","archive" to "Arşiv"))
        val query=input(b,"İl veya adres ara")
        val list=column(b); var items=JSONArray()
        val render={
            list.removeAllViews(); var count=0
            for(i in 0 until items.length()) {
                val o=items.getJSONObject(i); val status=o.optString("status"); val terminal=status in listOf("COMPLETED","CANCELLED","EXPIRED","FAILED","DISPUTED")
                val index=filter.selectedItemPosition
                val matches=if(provider) index==0 || (index==1 && status in listOf("PUBLISHED","OFFERING")) || (index==2 && status !in listOf("PUBLISHED","OFFERING") && !terminal) else index==0 || (index==1 && !terminal) || (index==2 && terminal)
                if(matches && LogisticsTools.contains("${o.optString("pickupAddress")} ${o.optString("deliveryAddress")}",query.text.toString())) { orderCard(list,o,provider); count++ }
            }
            if(count==0) empty(list,"Bu görünümde ilan yok",if(provider) "Aracını, müsaitliğini ve konumunu kontrol edebilirsin." else "Yeni ilan oluşturabilir veya aramanı değiştirebilirsin.")
        }
        text(list,"İlanlar yükleniyor…",14)
        request(if(provider) "/v1/provider/orders" else "/v1/orders",failed={ list.removeAllViews(); empty(list,"İlanlar alınamadı","Bağlantını kontrol edip tekrar dene."); secondary(list,"Tekrar dene") { showOrders(provider) } }) { data -> items=data.optJSONArray("orders") ?: JSONArray(); render() }
        watch(query) { render() }; filter.onItemSelectedListener=object:AdapterView.OnItemSelectedListener { override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) { if(items.length()>0) render() }; override fun onNothingSelected(parent:AdapterView<*>?) {} }
        button(b,if(provider) "Tekliflerim" else "+ Yeni ilan") { if(provider) providerOffers() else showCreateMenu() }
        secondary(b,"Yenile") { showOrders(provider) }
    }
    private fun orderCard(parent:LinearLayout,o:JSONObject,provider:Boolean) {
        val c=card(parent); val payload=o.optJSONObject("payload") ?: JSONObject()
        chip(c,state(o.optString("status")))
        text(c,if(o.isNull("budgetMinor")) "Teklif bekleniyor" else "${Amounts.display(o.getString("budgetMinor"))} ${o.optString("currency","TRY")}",23,true)
        routePoint(c,"YÜKLEME",payload.optString("pickupCity").ifBlank { safe(o,"pickupAddress") },false)
        routePoint(c,"TESLİMAT",payload.optString("deliveryCity").ifBlank { safe(o,"deliveryAddress") },true)
        val weight=if(payload.has("weightKg")) "${formatNumber(payload.optDouble("weightKg")/1000)} ton" else services[o.optString("serviceType")] ?: "Yük"
        text(c,"${dateLabel(o.optString("scheduledAt").ifBlank { o.optString("createdAt") })}  ·  $weight  ·  ${payload.optString("loadType","Komple")}",12)
        secondary(c,"İlanı incele  →") { showOrder(o,provider) }
    }
    private fun routePoint(parent:LinearLayout,label:String,place:String,end:Boolean) {
        val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(0,dp(5),0,dp(8)) }
        row.addView(Glyph(this,"pin",if(end) Palette.accent else Palette.ink),LinearLayout.LayoutParams(dp(18),dp(22)).apply { marginEnd=dp(12) })
        val words=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; row.addView(words,LinearLayout.LayoutParams(0,-2,1f)); text(words,label,10); text(words,place,18,true); parent.addView(row)
    }
    private fun formatNumber(value:Double)=java.text.DecimalFormat("#,##0.##",java.text.DecimalFormatSymbols(java.util.Locale.forLanguageTag("tr-TR"))).format(value)

    private fun showOrder(order:JSONObject,provider:Boolean) {
        val id=order.getString("id"); val status=order.getString("status")
        val b=base("Sipariş detayı") { showOrders(provider) }
        val header=card(b); chip(header,state(status)); text(header,if(order.isNull("budgetMinor")) "Teklif bekleniyor" else "${Amounts.display(order.getString("budgetMinor"))} ${order.optString("currency","TRY")}",28,true)
        routePoint(header,"YÜKLEME",safe(order,"pickupAddress"),false); routePoint(header,"TESLİMAT",safe(order,"deliveryAddress"),true)
        if(!order.isNull("pickupLat") && !order.isNull("pickupLng")) {
            secondary(b,"Alım noktasını haritada göster") {
                if(activeMap==null) { try { val map=OrderMap(this,order.getDouble("pickupLat"),order.getDouble("pickupLng")); activeMap=map; b.addView(map,1,lp(dp(230))) } catch(_:Exception) { toast("Harita bu cihazda açılamadı.") } }
            }
        }
        val payload=order.optJSONObject("payload") ?: JSONObject(); val details=card(b); text(details,"Yük bilgileri",18,true)
        pair(details,"Taşıma şekli",payload.optString("loadType","Komple")); pair(details,"Yük cinsi",safe(payload,"cargoDescription")); pair(details,"Ağırlık",if(payload.has("weightKg")) "${formatNumber(payload.optDouble("weightKg"))} kg" else "Belirtilmedi"); pair(details,"Hacim",if(payload.has("volumeM3")) "${formatNumber(payload.optDouble("volumeM3"))} m³" else "Belirtilmedi")
        val vehicleType=payload.optJSONArray("vehicleTypes")?.optString(0).orEmpty(); pair(details,"Araç",vehicles[vehicleType] ?: "Fark etmez"); if(payload.optString("note").isNotBlank()) text(details,payload.getString("note"),14)
        secondary(b,"Sefer maliyetini hesapla") { showCost() }
        if(!provider) {
            button(b,"Gelen teklifler") { showOffers(id) }
            if(status in listOf("PUBLISHED","OFFERING")) button(b,"Eşleşen taşıyıcılar") { showMatches(id) }
        } else if(status in listOf("PUBLISHED","OFFERING")) button(b,"Teklif ver") { makeOffer(order) }
        else {
            val next=mapOf("DRIVER_ASSIGNED" to "EN_ROUTE_PICKUP","EN_ROUTE_PICKUP" to "ARRIVED_PICKUP","ARRIVED_PICKUP" to "LOADED","LOADED" to "IN_TRANSIT","IN_TRANSIT" to "ARRIVED_DELIVERY","ARRIVED_DELIVERY" to "DELIVERED","DELIVERED" to "COMPLETED")[status]
            if(next!=null) button(b,"Durumu güncelle: ${state(next)}") { confirm("${state(next)} olarak işaretlensin mi?") { changeStatus(id,next,provider) } }
            if(status in listOf("ARRIVED_DELIVERY","DELIVERED")) button(b,"Teslimat onayı ekle") { proof(order) }
            if(next!=null) button(b,"Bu iş için konumumu paylaş") { shareLocation(id) }
        }
        if(status in listOf("PUBLISHED","OFFERING","ACCEPTED","DRIVER_ASSIGNED","EN_ROUTE_PICKUP","ARRIVED_PICKUP")) button(b,"Siparişi iptal et") { confirm("Sipariş iptal edilsin mi?") { changeStatus(id,"CANCELLED",provider) } }
        button(b,"Son taşıyıcı konumu") { request("/v1/tracking/orders/$id/location") { data ->
            val l=data.optJSONObject("location") ?: return@request
            val map=Intent(Intent.ACTION_VIEW,Uri.parse("geo:${l.optDouble("lat")},${l.optDouble("lng")}?q=${l.optDouble("lat")},${l.optDouble("lng")}"))
            try { startActivity(map) } catch (_: android.content.ActivityNotFoundException) { toast("Konum: ${l.optDouble("lat")}, ${l.optDouble("lng")}") }
        } }
        button(b,"İşlem geçmişi") { val history=base("İşlem geçmişi") { showOrder(order,provider) }; loadList(history,"/v1/tracking/orders/$id/history","events") { e -> text(history,"${state(e.optString("eventType").removePrefix("ORDER_STATUS_"))}\n${e.optString("createdAt")}") } }
        button(b,"Güncel sipariş listesi") { showOrders(provider) }
    }
    private fun changeStatus(id:String,status:String,provider:Boolean) { request("/v1/orders/$id/status","POST",JSONObject().put("status",status)) { toast("Durum güncellendi."); showOrders(provider) } }
    private fun showOffers(id:String) {
        val b=base("Gelen teklifler") { showOrders() }
        loadList(b,"/v1/orders/$id/offers","offers") { o ->
            val c=card(b); text(c,"${Amounts.display(o.optString("amountMinor"))} ${o.optString("currency")} • ${state(o.optString("status"))}",18,true)
            text(c,"${o.optJSONObject("provider")?.optString("firstName").orEmpty()} • ${o.optString("note")}")
            if(o.optString("status")=="PENDING") button(c,"Teklifi kabul et") { confirm("Bu teklif kabul edilsin mi?") { request("/v1/orders/$id/offers/${o.getString("id")}/accept","POST",JSONObject()) { showOrders() } } }
        }
    }
    private fun makeOffer(order:JSONObject) {
        val b=base("Teklif ver") { showOrder(order,true) }; val amount=input(b,"Tutar (${order.optString("currency")})",number=true); val eta=input(b,"Tahmini varış (dakika)",number=true); val note=input(b,"Not (isteğe bağlı)")
        button(b,"Teklifi gönder") {
            try {
                val body=JSONObject().put("amountMinor",Amounts.minor(amount.text.toString(),true)).put("currency",order.getString("currency")).put("note",note.text.toString())
                if(eta.text.isNotBlank()) { val minutes=eta.text.toString().toIntOrNull(); require(minutes!=null && minutes in 1..10080) { "Varış süresi 1–10080 dakika olmalı." }; body.put("etaMinutes",minutes) }
                request("/v1/orders/${order.getString("id")}/offers","POST",body) { toast("Teklif gönderildi."); providerOffers() }
            } catch(e:IllegalArgumentException) { toast(e.message.orEmpty()) }
        }
    }
    private fun providerOffers() {
        val b=base("Tekliflerim") { showProvider() }
        loadList(b,"/v1/provider/offers","offers") { o ->
            val c=card(b); text(c,"${Amounts.display(o.optString("amountMinor"))} ${o.optString("currency")} • ${state(o.optString("status"))}",18,true)
            text(c,o.optJSONObject("order")?.optString("pickupAddress").orEmpty())
            if(o.optString("status")=="PENDING") button(c,"Geri çek") { confirm("Teklif geri çekilsin mi?") { request("/v1/provider/offers/${o.getString("id")}/withdraw","POST",JSONObject()) { providerOffers() } } }
        }
    }
    private fun showMatches(id:String) {
        val b=base("Eşleşen taşıyıcılar") { showOrders() }
        loadList(b,"/v1/orders/$id/matches","matches") { m -> val c=card(b); text(c,"Skor: ${m.optDouble("score")} • ${m.optDouble("distanceKm")} km\n${vehicles[m.optString("vehicleType")] ?: m.optString("vehicleType")}"); secondary(c,"Taşıyıcı profili") { request("/v1/directory/providers/${m.getString("providerId")}") { data -> data.optJSONObject("person")?.let { showPerson(it) } } } }
    }
    private fun showProvider() {
        if(!authenticated()) return
        val b=base("Taşıyıcı ayarları")
        text(b,"İş almaya hazır mısın?",25,true)
        if(!isProvider()) {
            text(b,"Taşıyıcı hesabını etkinleştir, aracını ekle ve müsait olduğunda konumunu paylaş.",14)
            button(b,"Taşıyıcı hesabını etkinleştir") { confirm("Hesabın sürücü hesabına dönüştürülsün mü?") { request("/v1/auth/become-provider","POST",JSONObject().put("providerType","DRIVER")) { data -> data.optJSONObject("user")?.let { user -> session?.put("user",user); session?.let { sessions.save(it) } }; showHome() } } }
            return
        }
        val status=card(b)
        fun render(p:JSONObject) {
            status.removeAllViews(); chip(status,if(p.optBoolean("isOnline")) "ÇEVRİMİÇİ" else "ÇEVRİMDIŞI")
            text(status,if(p.optBoolean("isAvailable") && p.optBoolean("isOnline")) "Yeni yükler için müsaitsin" else "Şu anda iş almıyorsun",20,true)
            button(status,if(p.optBoolean("isOnline")) "Çevrimdışı ol" else "Çevrimiçi ve müsait ol") { val online=!p.optBoolean("isOnline"); request("/v1/providers/me","PATCH",JSONObject().put("isOnline",online).put("isAvailable",online)) { showProvider() } }
            if(p.optBoolean("isOnline")) secondary(status,if(p.optBoolean("isAvailable")) "Meşgul olarak işaretle" else "Yeniden müsait ol") { request("/v1/providers/me","PATCH",JSONObject().put("isAvailable",!p.optBoolean("isAvailable"))) { showProvider() } }
        }
        text(status,"Durumun yükleniyor…",14)
        request("/v1/providers/me",failed={ status.removeAllViews(); text(status,"Durum alınamadı. Bağlantını kontrol et.",14); secondary(status,"Tekrar dene") { showProvider() } }) { render(it.optJSONObject("provider") ?: JSONObject()) }
        actionCard(b,"truck","Araçlarım","Araç özelliklerini ve kapasitesini güncelle") { showVehicles() }
        button(b,"Güncel konumumu paylaş") { shareLocation(null) }
        text(b,"Konum, düğmeye bastığında bir kez gönderilir. Arka planda konum takibi yapılmaz.",13)
        val radius=input(b,"İş arama mesafesi (km)",number=true)
        secondary(b,"İş alanını güncelle") { try { val r=number(radius,true)!!; require(r in 1.0..500.0) { "1–500 km arasında bir değer girin." }; request("/v1/providers/me","PATCH",JSONObject().put("serviceRadiusKm",r)) { toast("İş alanı güncellendi.") } } catch(e:Exception) { toast(e.message.orEmpty()) } }
        actionCard(b,"list","Tekliflerim","Gönderdiğin teklifleri yönet") { providerOffers() }
    }

    private fun showVehicles() {
        if(!authenticated()) return
        val b=base("Araçlarım"); text(b,"Filon hazır olsun",25,true); text(b,"Eşleşmelerde aracın kapasitesi ve özellikleri dikkate alınır.",14)
        button(b,"+ Araç ekle") { editVehicle(null) }
        loadList(b,"/v1/vehicles","vehicles") { v ->
            val d=v.optJSONObject("details") ?: JSONObject(); val c=card(b); chip(c,if(v.optBoolean("active")) "AKTİF" else "PASİF")
            text(c,d.optString("brandModel").ifBlank { vehicles[v.optString("type")] ?: v.optString("type") },22,true)
            pair(c,"Plaka",safe(v,"plateNumber")); pair(c,"Kapasite","${safe(v,"capacityKg")} kg"); text(c,"${d.optString("city")} → ${d.optString("destination").ifBlank { "Güzergâh esnek" }}",14)
            secondary(c,"Aracı incele  →") { showVehicle(v,true) }
        }
    }

    private fun proof(order:JSONObject) {
        val b=base("Teslimat onayı") { showOrder(order,true) }; val recipient=input(b,"Teslim alan kişinin adı"); val note=input(b,"Teslimat notu")
        button(b,"Onayı kaydet") {
            if(recipient.text.isBlank()) { toast("Teslim alan kişinin adını girin."); return@button }
            request("/v1/tracking/orders/${order.getString("id")}/delivery-proof","POST",JSONObject().put("type","RECIPIENT_CONFIRMATION").put("recipientName",recipient.text.toString().trim()).put("note",note.text.toString())) { toast("Teslimat onayı kaydedildi."); showOrders(true) }
        }
    }
    private fun showSettings() {
        val b=base("Ayarlar ve bağlantı"); val address=input(b,"API sunucu adresi"); address.setText(server)
        button(b,"Sunucu adresini kaydet") {
            try {
                val next=ApiTransport.normalizeServer(address.text.toString())
                if(next!=server) { synchronized(sessions) { authEpoch.incrementAndGet(); sessions.clear() }; session=null; server=next; prefs.edit().putString("server",next).apply() }
                toast("Kaydedildi. Sunucu değiştiyse tekrar giriş yapın.")
            } catch(e:Exception) { toast(e.message ?: "Geçersiz adres.") }
        }
        button(b,"Sunucu ve veritabanı bağlantısını kontrol et") { request("/ready",auth=false) { data ->
            toast(if(data.optString("status")=="ready" && data.optString("service")=="yuklab-api") "Sunucu ve veritabanı hazır." else "Bu adres geçerli bir YükLab API yanıtı vermiyor.")
        } }
        text(b,"Sipariş, kayıt ve giriş için çalışan bir YükLab API sunucusu gerekir. Varsayılan adresin kullanılabilirliği sunucuya bağlıdır.",14)
        text(b,"YükLab 2.2 • Android deneme sürümü",13)
    }
    private fun logout() {
        saveForm?.invoke(); saveForm=null
        val old=session; val endpoint=server; session=null; synchronized(sessions) { authEpoch.incrementAndGet(); sessions.clear() }; showHome()
        if(old!=null) executor.execute { try { transport.call(endpoint,"/v1/auth/logout","POST",JSONObject().put("refreshToken",old.optString("refreshToken")),old.optString("accessToken")) } catch (_: Exception) {} }
    }
    private fun request(path:String,method:String="GET",body:JSONObject?=null,auth:Boolean=true,failed:(()->Unit)?=null,done:(JSONObject)->Unit) {
        if(demoMode) {
            val generation=screen
            handler.post { if(screen==generation && !isDestroyed) { if(method=="GET") done(DemoData.response(path)) else toast("Tanıtım modunda işlem kaydedilmez. Gerçek işlemler için giriş yapın.") } }
            return
        }
        val currentScreen=screen; val endpoint=server; val currentSession=session; val epoch=authEpoch.get()
        if(auth && currentSession==null) { showLogin(); return }
        try { ApiTransport.normalizeServer(endpoint) } catch (_:Exception) { toast("Ayarlar bölümünden geçerli bir HTTPS sunucu adresi girin."); return }
        inFlight++; enableButtons(root,false)
        val progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate=true }; root.addView(progress,0)
        executor.execute {
            if((method=="GET" && screen!=currentScreen) || (auth && authEpoch.get()!=epoch)) return@execute
            val result=try {
                if(auth) sessionApi.call(endpoint,path,method,body) else transport.call(endpoint,path,method,body)
            } catch (_:java.net.SocketTimeoutException) { ApiResult(0,JSONObject().put("error","Sunucu zamanında yanıt vermedi. İşlem yapıyorsanız tekrar göndermeden önce listeyi kontrol edin.")) }
              catch (_:Exception) { ApiResult(0,JSONObject().put("error","Sunucuya bağlanılamadı. İnternet bağlantısını ve API adresini kontrol edin.")) }
            handler.post {
                if(screen==currentScreen) inFlight=(inFlight-1).coerceAtLeast(0)
                if(isDestroyed) return@post
                if(authEpoch.get()==epoch && session!=null && server==endpoint) session=sessions.read()
                if(auth && result.status==401 && session!=null && authEpoch.get()==epoch) { saveForm?.invoke(); saveForm=null; session=null; sessions.clear() }
                enableButtons(root,inFlight==0)
                if(screen!=currentScreen) return@post
                (progress.parent as? ViewGroup)?.removeView(progress); enableButtons(root,inFlight==0)
                if(result.ok) done(result.data) else {
                    failed?.invoke(); toast(errorText(result))
                    if(auth && result.status==401) showLogin()
                }
            }
        }
    }
    private fun errorText(result:ApiResult):String {
        val code=result.data.optString("error")
        return mapOf("INVALID_PROFILE" to "Profil bilgilerini kontrol edin.", "INVALID_NOTE" to "Not en fazla 500 karakter olmalı.", "USER_NOT_FOUND" to "Bu kullanıcı bulunamadı.", "INVALID_VEHICLE_YEAR" to "Araç model yılı geçersiz.", "INVALID_VEHICLE_DETAILS" to "Araç detaylarını kontrol edin.", "CANNOT_SAVE_SELF" to "Kendi hesabını portföyüne ekleyemezsin.", "INVALID_CREDENTIALS" to "E-posta/telefon veya şifre yanlış.","ACCOUNT_EXISTS" to "Bu hesap zaten kayıtlı.","INVALID_INPUT" to "Bilgileri kontrol edin.","INVALID_REFRESH_TOKEN" to "Oturumunuz sona erdi. Yeniden giriş yapın.","PROVIDER_NOT_MATCHED" to "Bu iş için uygun değilsiniz. Konum, araç ve müsaitlik durumunu kontrol edin.","FORBIDDEN" to "Bu işlem için hesabınızın yetkisi yok.","PENDING_OFFER_EXISTS" to "Bu iş için bekleyen bir teklifiniz var.","DELIVERY_PROOF_REQUIRED" to "Önce teslimat onayı ekleyin.","LOCATION_NOT_AVAILABLE" to "Taşıyıcının güncel konumu bulunmuyor.","DRIVER_NOT_ASSIGNED" to "Henüz taşıyıcı atanmadı.","TRACKING_NOT_ACTIVE" to "Bu siparişte konum takibi aktif değil.")[code]
            ?: if(result.status==401) "Oturumunuz sona erdi. Yeniden giriş yapın." else if(result.status==503) "Sunucu veya veritabanı hazır değil. Daha sonra tekrar deneyin." else code.ifBlank { "İşlem başarısız (HTTP ${result.status})." }
    }
    private fun loadList(parent:LinearLayout,path:String,array:String,render:(JSONObject)->Unit) {
        val loading=TextView(this).apply { text="Yükleniyor…" }; parent.addView(loading)
        request(path,failed={ loading.text="Veriler alınamadı. Geri dönüp yeniden deneyin." }) { data -> parent.removeView(loading); val items=data.optJSONArray(array); if(items==null || items.length()==0) text(parent,"Henüz kayıt bulunmuyor."); else for(i in 0 until items.length()) render(items.getJSONObject(i)) }
    }
    private fun shareLocation(orderId:String?) { location { l ->
        val body=JSONObject().put("lat",l.latitude).put("lng",l.longitude).put("accuracyM",l.accuracy.toDouble())
        if(orderId!=null) body.put("orderId",orderId)
        request("/v1/tracking/location","POST",body) { toast("Konum paylaşıldı.") }
    } }
    private fun location(done:(Location)->Unit) {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            pendingLocation=done; requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),42); return
        }
        stopLocation()
        val manager=getSystemService(LOCATION_SERVICE) as LocationManager
        val providers=manager.getProviders(true).filter { it==LocationManager.GPS_PROVIDER || it==LocationManager.NETWORK_PROVIDER }
        if(providers.isEmpty()) { toast("Telefonun konum servisini açın."); return }
        try {
            val recent=providers.mapNotNull { manager.getLastKnownLocation(it) }.filter { SystemClock.elapsedRealtimeNanos()-it.elapsedRealtimeNanos in 0..120_000_000_000L }.maxByOrNull { it.elapsedRealtimeNanos }
            if(recent!=null) { done(recent); return }
            val generation=screen
            val active=object:LocationListener {
                override fun onLocationChanged(location:Location) { stopLocation(); if(screen==generation && !isDestroyed) done(location) }
                @Deprecated("Deprecated by Android") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?) {}
                override fun onProviderEnabled(provider:String) {}
                override fun onProviderDisabled(provider:String) {}
            }
            listener=active
            providers.forEach { manager.requestLocationUpdates(it,0L,0f,active,Looper.getMainLooper()) }
            toast("Güncel konum alınıyor…")
            handler.postDelayed({ if(listener===active) { stopLocation(); if(screen==generation && !isDestroyed) toast("Konum alınamadı. Açık alanda tekrar deneyin veya koordinatları girin.") } },20000)
        } catch (_:SecurityException) { stopLocation(); toast("Konum izni verilemedi.") }
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==42) { val done=pendingLocation; pendingLocation=null; if(grantResults.any { it==PackageManager.PERMISSION_GRANTED } && done!=null) location(done) else toast("Konum izni verilmedi.") }
    }
    private fun stopLocation() { listener?.let { (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(it) }; listener=null; pendingLocation=null }
    override fun onPause() { saveForm?.invoke(); super.onPause() }
    override fun onDestroy() { activeMap?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }; activeMap=null; stopLocation(); handler.removeCallbacksAndMessages(null); executor.shutdown(); super.onDestroy() }
    private fun showProfile() {
        val b=base("Profilim",tab="profile")
        if(demoMode) {
            text(b,"Tanıtım profili",26,true); text(b,"Müşteri ve taşıyıcı ekranlarını keşfedebilirsin.",14)
            button(b,if(demoProvider) "Yük veren görünümüne geç" else "Taşıyıcı görünümüne geç") { demoProvider=!demoProvider; showHome() }
            secondary(b,"Tanıtımdan çık ve giriş yap") { demoMode=false; showLogin() }; return
        }
        if(session==null) { empty(b,"Hesabına giriş yap","İlanların, araçların ve portföyün burada."); button(b,"Giriş / Kayıt") { showLogin() }; secondary(b,"Bağlantı ayarları") { showSettings() }; return }
        val content=column(b)
        fun render(user:JSONObject) {
            content.removeAllViews(); val profile=user.optJSONObject("profile") ?: JSONObject(); val c=card(content)
            c.addView(Glyph(this,"user",Palette.accent),LinearLayout.LayoutParams(dp(48),dp(48)))
            text(c,"${user.optString("firstName")} ${user.optString("lastName")}",25,true)
            chip(c,if(user.optString("role") in listOf("DRIVER","SERVICE_PROVIDER")) "TAŞIYICI" else "YÜK VEREN")
            if(profile.optString("companyName").isNotBlank()) text(c,profile.getString("companyName"),16,true)
            text(c,listOf(profile.optString("city"),profile.optString("district")).filter { it.isNotBlank() }.joinToString(" / "),14)
            pair(c,"E-posta",safe(user,"email")); if(user.has("createdAt")) pair(c,"Üyelik",dateLabel(user.getString("createdAt")))
            button(content,"Profili düzenle") { editProfile(user) }
            if(isProvider()) { actionCard(content,"truck","Araçlarım","Araç bilgilerini ve müsaitliğini yönet") { showVehicles() }; actionCard(content,"check","Taşıyıcı ayarları","Konum, çevrimiçi durum ve iş alanı") { showProvider() } }
            else actionCard(content,"truck","Taşıyıcı hesabı aç","Aracınla yük taşıma hizmeti ver") { showProvider() }
            secondary(content,"Bağlantı ayarları") { showSettings() }
            secondary(content,"Çıkış yap") { logout() }
        }
        render(session!!.optJSONObject("user") ?: JSONObject())
        request("/v1/users/me",failed={ toast("Profil güncellenemedi; kayıtlı bilgiler gösteriliyor.") }) { data -> data.optJSONObject("user")?.let { user -> session?.put("user",user); session?.let { sessions.save(it) }; render(user) } }
    }
    private fun editProfile(user:JSONObject) {
        val b=base("Profili düzenle") { showProfile() }; val profile=user.optJSONObject("profile") ?: JSONObject()
        val first=input(b,"Ad").apply { setText(user.optString("firstName")) }; val last=input(b,"Soyad").apply { setText(user.optString("lastName")) }
        val company=input(b,"Firma adı (isteğe bağlı)").apply { setText(profile.optString("companyName")) }; val city=input(b,"İl").apply { setText(profile.optString("city")) }; val district=input(b,"İlçe").apply { setText(profile.optString("district")) }
        val services=input(b,"Verdiğin hizmetler").apply { setText(profile.optString("services")) }; val bio=input(b,"Hakkında").apply { setSingleLine(false); minLines=3; setText(profile.optString("bio")) }
        val phone=input(b,"İletişim telefonu").apply { inputType=InputType.TYPE_CLASS_PHONE; setText(profile.optString("contactPhone")) }
        val share=CheckBox(this).apply { text="İletişim telefonumu profilimde göster"; isChecked=profile.optBoolean("publicPhone"); setTextColor(Palette.ink) }; b.addView(share,lp())
        button(b,"Değişiklikleri kaydet") {
            if(first.text.isBlank() || last.text.isBlank()) { toast("Ad ve soyad gerekli."); return@button }
            val p=JSONObject().put("companyName",company.text.toString().trim()).put("city",city.text.toString().trim()).put("district",district.text.toString().trim()).put("services",services.text.toString().trim()).put("bio",bio.text.toString().trim()).put("contactPhone",phone.text.toString().trim()).put("publicPhone",share.isChecked)
            request("/v1/users/me","PATCH",JSONObject().put("firstName",first.text.toString().trim()).put("lastName",last.text.toString().trim()).put("profile",p)) { data -> data.optJSONObject("user")?.let { session?.put("user",it); session?.let { value -> sessions.save(value) } }; toast("Profil güncellendi."); showProfile() }
        }
    }
    private fun showDirectory(freeVehicles:Boolean) {
        if(!authenticated()) return
        val b=base(if(freeVehicles) "Boş araçlar" else "Nakliyeciler")
        text(b,if(freeVehicles) "Yoluna uygun aracı bul" else "Taşıyıcılarla tanış",24,true)
        text(b,if(freeVehicles) "Çevrimiçi ve müsait taşıyıcıların son 100 aracı." else "Son 100 taşıyıcı profili. İsim veya şehirle ara.",13)
        val query=input(b,"İsim, il veya güzergâh ara"); val list=column(b); var items=JSONArray()
        val render={
            list.removeAllViews(); var count=0
            for(i in 0 until items.length()) {
                val item=items.getJSONObject(i); val person=if(freeVehicles) item.optJSONObject("owner") ?: JSONObject() else item
                val details=item.optJSONObject("details") ?: JSONObject()
                if(LogisticsTools.contains("${person.optString("firstName")} ${person.optString("lastName")} ${person.optString("companyName")} ${person.optString("city")} ${details.optString("city")} ${details.optString("destination")}",query.text.toString())) {
                    val c=card(list)
                    if(freeVehicles) { chip(c,"MÜSAİT ARAÇ"); text(c,details.optString("brandModel").ifBlank { vehicles[item.optString("type")] ?: "Araç" },21,true); text(c,"${details.optString("city").ifBlank { person.optString("city") }} → ${details.optString("destination").ifBlank { "Güzergâh esnek" }}",16); pair(c,"Kapasite",if(item.isNull("capacityKg")) "Belirtilmedi" else "${safe(item,"capacityKg")} kg"); secondary(c,"Araç ve taşıyıcı") { showVehicle(item,false) } }
                    else { text(c,personName(person),21,true); text(c,"${person.optString("city")} ${person.optString("district")}",14); text(c,person.optString("services"),13); if(person.optInt("completedJobs")>0) pair(c,"Tamamlanan işler",person.optInt("completedJobs").toString()); secondary(c,"Profili incele") { showPerson(person) } }
                    count++
                }
            }
            if(count==0) empty(list,"Sonuç bulunamadı","Arama metnini değiştirebilir veya daha sonra tekrar bakabilirsin.")
        }
        text(list,"Yükleniyor…",14)
        request(if(freeVehicles) "/v1/directory/vehicles" else "/v1/directory/providers",failed={ list.removeAllViews(); empty(list,"Liste alınamadı","Sunucu bağlantısını kontrol edip tekrar dene."); secondary(list,"Tekrar dene") { showDirectory(freeVehicles) } }) { items=it.optJSONArray(if(freeVehicles) "vehicles" else "providers") ?: JSONArray(); render() }
        watch(query) { render() }
    }
    private fun personName(person:JSONObject)=person.optString("companyName").ifBlank { "${person.optString("firstName")} ${person.optString("lastName")}".trim().ifBlank { "Taşıyıcı" } }
    private fun showPerson(person:JSONObject) {
        val b=base("Taşıyıcı profili"); val c=card(b)
        c.addView(Glyph(this,"user",Palette.accent),LinearLayout.LayoutParams(dp(48),dp(48))); text(c,personName(person),25,true)
        text(c,"${person.optString("city")} ${person.optString("district")}",14)
        chip(c,if(person.optBoolean("isAvailable") && person.optBoolean("isOnline")) "MÜSAİT" else "PROFİL")
        if(person.optInt("completedJobs")>0) { pair(c,"Tamamlanan işler",person.optInt("completedJobs").toString()); pair(c,"Puan",formatNumber(person.optDouble("rating"))) }
        text(c,person.optString("services"),16); text(c,person.optString("bio"),14)
        button(b,"Portföyüme ekle") { request("/v1/contacts/${person.getString("id")}","PUT",JSONObject()) { toast("Portföyüne eklendi.") } }
        if(!person.isNull("contactPhone") && person.optString("contactPhone").isNotBlank()) button(b,"Telefonla iletişim") { dial(person.getString("contactPhone")) }
        else text(b,"Bu kişi iletişim telefonunu paylaşmamış.",13)
    }
    private fun dial(phone:String) { val cleaned=phone.filter { it.isDigit() || it=='+' }; if(cleaned.isBlank()) return; try { startActivity(Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",cleaned,null))) } catch(_:android.content.ActivityNotFoundException) { toast(cleaned) } }
    private fun showPortfolio() {
        if(!authenticated()) return
        val b=base("Portföyüm",tab="contacts"); text(b,"Yeniden çalışmak istediklerin",23,true); text(b,"Kaydettiğin kişileri ve yalnızca sana ait notları yönet.",14)
        val query=input(b,"Portföyde ara"); val list=column(b); var entries=JSONArray()
        val render={
            list.removeAllViews(); var count=0
            for(i in 0 until entries.length()) {
                val entry=entries.getJSONObject(i); val person=entry.optJSONObject("person") ?: JSONObject()
                if(LogisticsTools.contains("${personName(person)} ${entry.optString("note")}",query.text.toString())) {
                    val c=card(list); text(c,personName(person),20,true); text(c,person.optString("city"),13); if(entry.optString("note").isNotBlank()) text(c,entry.optString("note"),14)
                    secondary(c,"Profili gör") { showPerson(person) }; secondary(c,"Kişisel not ekle / düzenle") { editNote(entry) }
                    secondary(c,"Portföyden kaldır") { confirm("Bu kişi portföyünden kaldırılsın mı?") { request("/v1/contacts/${entry.getString("contactId")}","DELETE") { showPortfolio() } } }; count++
                }
            }
            if(count==0) empty(list,"Portföyün burada büyüyecek","Bir taşıyıcı profiline girerek portföyüne ekleyebilirsin.")
        }
        request("/v1/contacts",failed={ empty(list,"Portföy alınamadı","Bağlantını kontrol edip tekrar dene."); secondary(list,"Tekrar dene") { showPortfolio() } }) { entries=it.optJSONArray("contacts") ?: JSONArray(); render() }
        watch(query) { render() }; button(b,"Taşıyıcıları keşfet") { showDirectory(false) }
    }
    private fun editNote(entry:JSONObject) {
        val b=base("Kişisel not") { showPortfolio() }; text(b,personName(entry.optJSONObject("person") ?: JSONObject()),23,true)
        val note=input(b,"Notun (en fazla 500 karakter)").apply { setSingleLine(false); minLines=5; filters=arrayOf(android.text.InputFilter.LengthFilter(500)); setText(entry.optString("note")) }
        text(b,"Bu not yalnızca kendi hesabında görünür.",13)
        button(b,"Notu kaydet") { request("/v1/contacts/${entry.getString("contactId")}","PUT",JSONObject().put("note",note.text.toString())) { toast("Not kaydedildi."); showPortfolio() } }
    }
    private fun showCost() {
        val b=base("Sefer maliyeti"); text(b,"Yola çıkmadan hesapla",25,true); text(b,"Kendi yakıt fiyatını ve giderlerini gir. Sonuçlar girdiğin tutarlara göre hesaplanır.",14)
        val km=input(b,"Mesafe (km)",number=true); val fuel=input(b,"Tüketim (litre / 100 km)",number=true); val price=input(b,"Yakıt litre fiyatı (TL)",number=true)
        val days=input(b,"Sefer süresi (gün)",number=true); val daily=input(b,"Günlük sürücü / konaklama gideri (TL)",number=true); val tolls=input(b,"Köprü, otoyol, feribot toplamı (TL)",number=true); val other=input(b,"Diğer giderler (TL)",number=true)
        val result=column(b)
        button(b,"Maliyeti hesapla") {
            try {
                fun decimal(e:EditText,required:Boolean=false):java.math.BigDecimal { val raw=e.text.toString().trim().replace(',','.'); require(!required || raw.isNotBlank()) { "${e.hint}: bir değer girin." }; require(raw.isBlank() || raw.matches(Regex("[0-9]{1,9}(\\.[0-9]{1,4})?"))) { "En fazla 9 basamak ve 4 ondalık basamak kullanın." }; return if(raw.isBlank()) java.math.BigDecimal.ZERO else java.math.BigDecimal(raw) }
                val cost=LogisticsTools.cost(decimal(km,true),decimal(fuel,true),decimal(price,true),decimal(days),decimal(daily),decimal(tolls),decimal(other))
                result.removeAllViews(); val c=card(result); chip(c,"TAHMİNİ TOPLAM GİDER"); text(c,"${Amounts.display(cost.total.movePointRight(2).toPlainString())} TL",30,true); pair(c,"Yakıt","${Amounts.display(cost.fuel.movePointRight(2).toPlainString())} TL"); pair(c,"Diğer giderler","${Amounts.display(cost.operations.movePointRight(2).toPlainString())} TL"); text(c,"Vergi ve KDV hesaplaması içermez.",12)
            } catch(e:Exception) { toast(e.message ?: "Geçerli, negatif olmayan sayılar girin.") }
        }
    }
    private fun showVehicle(vehicle:JSONObject,own:Boolean) {
        val b=base("Araç detayı") { if(own) showVehicles() else showDirectory(true) }; val d=vehicle.optJSONObject("details") ?: JSONObject(); val c=card(b)
        c.addView(Glyph(this,"truck",Palette.accent),LinearLayout.LayoutParams(dp(66),dp(58))); text(c,d.optString("brandModel").ifBlank { vehicles[vehicle.optString("type")] ?: "Araç" },25,true)
        chip(c,if(vehicle.optBoolean("active",true)) "AKTİF" else "PASİF")
        pair(c,"Araç tipi",vehicles[vehicle.optString("type")] ?: vehicle.optString("type")); if(own) pair(c,"Plaka",safe(vehicle,"plateNumber")); pair(c,"Model yılı",safe(d,"year")); pair(c,"Mülkiyet",safe(d,"ownership")); pair(c,"Kasa / dorse",safe(d,"bodyType")); pair(c,"Kapasite","${safe(vehicle,"capacityKg")} kg"); pair(c,"Hacim","${safe(vehicle,"volumeM3")} m³"); pair(c,"Soğutucu",if(vehicle.optBoolean("refrigerated")) "Var" else "Yok"); pair(c,"Bulunduğu il",safe(d,"city")); pair(c,"Tercih edilen varış",safe(d,"destination"))
        if(own) {
            button(b,"Araç bilgilerini düzenle") { editVehicle(vehicle) }
            secondary(b,if(vehicle.optBoolean("active")) "Aracı pasife al" else "Aracı aktifleştir") { request("/v1/vehicles/${vehicle.getString("id")}","PATCH",JSONObject().put("active",!vehicle.optBoolean("active"))) { showVehicles() } }
        } else vehicle.optJSONObject("owner")?.let { person -> button(b,"Taşıyıcı profilini gör") { showPerson(person) } }
    }
    private fun editVehicle(vehicle:JSONObject?) {
        if(!authenticated()) return
        val b=base(if(vehicle==null) "Araç ekle" else "Aracı düzenle") { showVehicles() }; val d=vehicle?.optJSONObject("details") ?: JSONObject()
        val type=select(b,"Araç tipi",vehicles).apply { setSelection(vehicles.keys.indexOf(vehicle?.optString("type")).coerceAtLeast(0)) }
        val brand=input(b,"Marka / model").apply { setText(d.optString("brandModel")) }; val plate=input(b,"Plaka").apply { setText(vehicle?.let { safe(it,"plateNumber","") }.orEmpty()) }; val year=input(b,"Model yılı (isteğe bağlı)",number=true).apply { setText(safe(d,"year","")) }
        val owners=linkedMapOf("Kendime ait" to "Kendime ait","Kiralık" to "Kiralık","Şirket aracı" to "Şirket aracı"); val owner=select(b,"Mülkiyet",owners).apply { setSelection(owners.keys.indexOf(d.optString("ownership")).coerceAtLeast(0)) }
        val bodyType=input(b,"Kasa / dorse tipi").apply { setText(d.optString("bodyType")) }; val capacity=input(b,"Kapasite (kg)",number=true).apply { setText(vehicle?.let { safe(it,"capacityKg","") }.orEmpty()) }; val volume=input(b,"Hacim (m³)",number=true).apply { setText(vehicle?.let { safe(it,"volumeM3","") }.orEmpty()) }
        val city=input(b,"Bulunduğu il").apply { setText(d.optString("city")) }; val destination=input(b,"Tercih edilen varış ili").apply { setText(d.optString("destination")) }; val cold=CheckBox(this).apply { text="Soğutuculu araç"; isChecked=vehicle?.optBoolean("refrigerated") ?: false }; b.addView(cold,lp())
        button(b,"Aracı kaydet") {
            try {
                require(plate.text.toString().trim().length in 2..20) { "Geçerli bir plaka girin." }
                val details=JSONObject().put("brandModel",brand.text.toString().trim()).put("ownership",key(owner,owners)).put("bodyType",bodyType.text.toString().trim()).put("city",city.text.toString().trim()).put("destination",destination.text.toString().trim())
                if(year.text.isNotBlank()) { val value=year.text.toString().toIntOrNull(); require(value!=null && value in 1950..java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)+1) { "Model yılı geçersiz." }; details.put("year",value) }
                val body=JSONObject().put("type",key(type,vehicles)).put("plateNumber",plate.text.toString().trim()).put("details",details).put("refrigerated",cold.isChecked)
                number(capacity)?.let { require(it>0 && it<=1000000) { "Kapasite geçersiz." }; body.put("capacityKg",it) }; number(volume)?.let { require(it>=0 && it<=100000) { "Hacim geçersiz." }; body.put("volumeM3",it) }
                request(if(vehicle==null) "/v1/vehicles" else "/v1/vehicles/${vehicle.getString("id")}",if(vehicle==null) "POST" else "PATCH",body) { toast("Araç kaydedildi."); showVehicles() }
            } catch(e:Exception) { toast(e.message ?: "Araç bilgilerini kontrol edin.") }
        }
    }

    private fun number(field:EditText,required:Boolean=false):Double? { val value=field.text.toString().trim(); if(value.isBlank() && !required) return null; val n=value.replace(',','.').toDoubleOrNull(); require(n!=null && n.isFinite()) { "${field.hint}: geçerli bir sayı girin." }; return n }
    private fun state(value:String)=states[value] ?: value
    private fun safe(o:JSONObject,key:String,fallback:String="—")=if(o.isNull(key)) fallback else o.optString(key).ifBlank { fallback }
    private fun column(parent:LinearLayout)=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; parent.addView(this,lp()) }
    private fun card(parent:LinearLayout)=column(parent).apply { setPadding(dp(18),dp(14),dp(18),dp(14)); background=Palette.shape(Color.WHITE,dp(20).toFloat(),Palette.line); elevation=dp(1).toFloat() }
    private fun text(parent:LinearLayout,value:String,size:Int=16,bold:Boolean=false) { parent.addView(TextView(this).apply { text=value; textSize=size.toFloat(); setTextColor(if(size<=14) Palette.muted else Palette.ink); if(bold) setTypeface(null,android.graphics.Typeface.BOLD); setLineSpacing(dp(2).toFloat(),1f); setPadding(0,dp(4),0,dp(7)) },LinearLayout.LayoutParams(-1,-2)) }
    private fun input(parent:LinearLayout,hint:String,password:Boolean=false,number:Boolean=false):EditText {
        text(parent,hint,12,true)
        return EditText(this).apply { id=fieldId++; this.hint=hint; filters=arrayOf(android.text.InputFilter.LengthFilter(if(number) 24 else if(password) 128 else 1000)); textSize=15f; setSingleLine(); setTextColor(Palette.ink); setHintTextColor(Palette.muted); setPadding(dp(14),dp(12),dp(14),dp(12)); background=Palette.shape(Color.WHITE,dp(12).toFloat(),Palette.line); minimumHeight=dp(50); inputType=if(password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else if(number) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED else InputType.TYPE_CLASS_TEXT; parent.addView(this,lp()) }
    }
    private fun button(parent:LinearLayout,label:String,action:()->Unit) { styledButton(parent,label,false,action) }
    private fun secondary(parent:LinearLayout,label:String,action:()->Unit) { styledButton(parent,label,true,action) }
    private fun styledButton(parent:LinearLayout,label:String,outline:Boolean,action:()->Unit) {
        parent.addView(Button(this).apply { text=label; isEnabled=inFlight==0; isAllCaps=false; textSize=14f; setTypeface(null,android.graphics.Typeface.BOLD); minHeight=dp(50); setPadding(dp(12),dp(10),dp(12),dp(10)); setTextColor(if(outline) Palette.ink else Color.WHITE); backgroundTintList=null; background=Palette.shape(if(outline) Color.WHITE else Palette.accent,dp(14).toFloat(),if(outline) Palette.line else null); elevation=0f; stateListAnimator=null; setOnClickListener { action() } },lp())
    }
    private fun select(parent:LinearLayout,label:String,choices:Map<String,String>):Spinner { text(parent,label,12,true); return Spinner(this).apply { id=fieldId++; adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,choices.values.toList()); background=Palette.shape(Color.WHITE,dp(12).toFloat(),Palette.line); setPadding(dp(8),0,dp(8),0); parent.addView(this,lp(dp(50))) } }
    private fun key(spinner:Spinner,choices:Map<String,String>)=choices.keys.elementAt(spinner.selectedItemPosition)
    private fun enableButtons(view:View,enabled:Boolean) { if(view is Button) view.isEnabled=enabled; if(view is ViewGroup) for(i in 0 until view.childCount) enableButtons(view.getChildAt(i),enabled) }
    private fun confirm(message:String,action:()->Unit) { AlertDialog.Builder(this).setMessage(message).setNegativeButton("Vazgeç",null).setPositiveButton("Onayla") { _,_ -> action() }.show() }
    private fun lp(height:Int=-2)=LinearLayout.LayoutParams(-1,height).apply { setMargins(0,0,0,dp(12)) }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun toast(value:String) { Toast.makeText(this,value,Toast.LENGTH_LONG).show() }
    private fun miniCard(parent:LinearLayout,icon:String,title:String,subtitle:String,action:()->Unit) {
        val c=card(parent); c.minimumHeight=dp(134); c.addView(Glyph(this,icon,Palette.accent),LinearLayout.LayoutParams(dp(27),dp(27)).apply { bottomMargin=dp(10) }); text(c,title,16,true); text(c,subtitle,11); c.isFocusable=true; c.contentDescription="$title. $subtitle"; c.setOnClickListener { action() }
    }
    private fun actionCard(parent:LinearLayout,icon:String,title:String,subtitle:String,action:()->Unit) {
        val c=card(parent); c.orientation=LinearLayout.HORIZONTAL; c.gravity=Gravity.CENTER_VERTICAL
        c.addView(Glyph(this,icon,Palette.accent),LinearLayout.LayoutParams(dp(26),dp(26)).apply { marginEnd=dp(14) })
        val words=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; c.addView(words,LinearLayout.LayoutParams(0,-2,1f)); text(words,title,16,true); text(words,subtitle,12)
        c.addView(Glyph(this,"arrow",Palette.muted),LinearLayout.LayoutParams(dp(20),dp(20))); c.isFocusable=true; c.setOnClickListener { action() }
    }
    private fun chip(parent:LinearLayout,label:String) { parent.addView(TextView(this).apply { text=label; textSize=11f; setTextColor(Palette.accent); setTypeface(null,android.graphics.Typeface.BOLD); background=Palette.shape(Palette.soft,dp(8).toFloat()); setPadding(dp(9),dp(5),dp(9),dp(5)) },LinearLayout.LayoutParams(-2,-2).apply { bottomMargin=dp(10) }) }
    private fun pair(parent:LinearLayout,label:String,value:String) { val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setPadding(0,dp(7),0,dp(7)) }; row.addView(TextView(this).apply { text=label; textSize=13f; setTextColor(Palette.muted) },LinearLayout.LayoutParams(0,-2,1f)); row.addView(TextView(this).apply { text=value; textSize=14f; setTextColor(Palette.ink); gravity=Gravity.END; maxWidth=dp(195) },LinearLayout.LayoutParams(0,-2,1f)); parent.addView(row) }
    private fun empty(parent:LinearLayout,title:String,detail:String) { val c=card(parent); c.gravity=Gravity.CENTER; c.addView(Glyph(this,"box",Palette.muted),LinearLayout.LayoutParams(dp(40),dp(40))); text(c,title,18,true); text(c,detail,14) }
    private fun watch(field:EditText,action:()->Unit) { field.addTextChangedListener(object:android.text.TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {} ; override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { action() }; override fun afterTextChanged(s:android.text.Editable?) {} }) }
    private fun dateLabel(value:String):String=try { val fmt=java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",java.util.Locale.US).apply { timeZone=java.util.TimeZone.getTimeZone("UTC") }; java.text.SimpleDateFormat("dd MMM yyyy",java.util.Locale.forLanguageTag("tr-TR")).format(fmt.parse(value)!!) } catch(_:Exception) { "Tarih belirtilmedi" }
}
