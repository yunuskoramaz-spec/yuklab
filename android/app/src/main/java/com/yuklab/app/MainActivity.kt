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
import android.view.View
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = prefs.getString("token", null)
        server = prefs.getString("server", "https://yuklab-api.onrender.com") ?: "https://yuklab-api.onrender.com"
        window.statusBarColor = YukLabTheme.ANTHRACITE_DEEP
        window.navigationBarColor = Color.WHITE
        showHome()
    }

    private fun showHome() {
        val body = base("YükLab", showBottomNav = true)

        val hero = YukLabTheme.card(this, dark = true)
        hero.addView(YukLabTheme.text(this, "Türkiye'nin akıllı lojistik ağı", 13f, Color.parseColor("#9DE4E6"), true))
        hero.addView(space(8))
        hero.addView(YukLabTheme.text(this, "Yükünü taşı, aracını çalıştır, yolunu hızlandır.", 27f, Color.WHITE, true))
        hero.addView(space(10))
        hero.addView(YukLabTheme.text(this, "Kurye, şehir içi taşıma, şehirler arası yük ve acil yol yardım tek platformda.", 15f, Color.parseColor("#D8E0E3")))
        hero.addView(space(18))
        hero.addView(YukLabTheme.button(this, "Taşıma talebi oluştur") { showCreateOrder() })
        body.addView(hero, lp(mBottom = 18))

        sectionTitle(body, "Hızlı işlemler")
        actionCard(body, "Siparişlerim", "Aktif ve geçmiş taşıma taleplerini görüntüle", "→") { showOrders() }
        actionCard(body, "Taşıyıcı merkezi", "İşleri, teklifleri ve araçlarını yönet", "→") { showProvider() }
        actionCard(body, "Acil yardım", "Çekici ve yol yardım talebi oluştur", "→") { showEmergencyOrder() }

        sectionTitle(body, "Hesabın")
        if (token == null) {
            val account = YukLabTheme.card(this)
            account.addView(YukLabTheme.text(this, "Henüz giriş yapmadın", 17f, YukLabTheme.TEXT, true))
            account.addView(space(5))
            account.addView(YukLabTheme.text(this, "Talep oluşturmak ve teklifleri yönetmek için hesabına giriş yap.", 14f, YukLabTheme.MUTED))
            account.addView(space(14))
            account.addView(YukLabTheme.button(this, "Giriş yap / Hesap oluştur") { showLogin() })
            body.addView(account, lp(mBottom = 16))
        } else {
            val account = YukLabTheme.card(this)
            account.addView(YukLabTheme.text(this, "Oturum açık", 17f, YukLabTheme.SUCCESS, true))
            account.addView(space(12))
            account.addView(YukLabTheme.button(this, "Çıkış yap", primary = false) {
                prefs.edit().remove("token").apply()
                token = null
                showHome()
            })
            body.addView(account, lp(mBottom = 16))
        }
    }

    private fun base(title: String, showBottomNav: Boolean = false): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(YukLabTheme.BACKGROUND)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(10))
            setBackgroundColor(Color.WHITE)
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(YukLabTheme.text(this, title, 21f, YukLabTheme.ANTHRACITE, true))
        brand.addView(YukLabTheme.text(this, "Global Smart Logistics Network", 10f, YukLabTheme.MUTED))
        top.addView(brand, LinearLayout.LayoutParams(0, dp(58), 1f))
        val settings = YukLabTheme.text(this, "⚙", 23f, YukLabTheme.ANTHRACITE, true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { showSettings() }
            isClickable = true
        }
        top.addView(settings, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(top)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(if (showBottomNav) 24 else 40))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        if (showBottomNav) root.addView(bottomNav())
        setContentView(root)
        return body
    }

    private fun bottomNav(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(5), dp(6), dp(7))
            setBackgroundColor(Color.WHITE)
        }
        val items = listOf(
            Triple("Ana Sayfa", "⌂") { showHome() },
            Triple("Talepler", "▤") { showOrders() },
            Triple("Taşıyıcı", "▣") { showProvider() },
            Triple("Hesap", "●") { if (token == null) showLogin() else showSettings() }
        )
        items.forEachIndexed { index, item ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(4), dp(3), dp(3))
                isClickable = true
                setOnClickListener { item.third() }
            }
            box.addView(YukLabTheme.text(this, item.second, 18f, if (index == 0) YukLabTheme.TURQUOISE else YukLabTheme.MUTED, true))
            box.addView(YukLabTheme.text(this, item.first, 10f, if (index == 0) YukLabTheme.TURQUOISE else YukLabTheme.MUTED, index == 0))
            nav.addView(box, LinearLayout.LayoutParams(0, dp(58), 1f))
        }
        return nav
    }

    private fun showLogin() {
        val b = base("Giriş")
        pageIntro(b, "Tekrar hoş geldin", "Yüklerini ve taşıyıcı işlemlerini hesabından yönet.")
        val identifier = field(b, "E-posta veya telefon")
        val password = field(b, "Şifre", true)
        primaryButton(b, "Giriş yap") {
            if (identifier.text.isBlank() || password.text.isBlank()) return@primaryButton toast("E-posta/telefon ve şifre gerekli")
            request("/v1/auth/login", "POST", JSONObject().put("identifier", identifier.text.toString()).put("password", password.text.toString()), null) { ok, data ->
                if (ok) {
                    token = data.optString("accessToken")
                    prefs.edit().putString("token", token).apply()
                    showHome()
                } else toast(data.optString("error", "Giriş başarısız"))
            }
        }
        secondaryButton(b, "Yeni hesap oluştur") { showRegister() }
        secondaryButton(b, "Ana sayfaya dön") { showHome() }
    }

    private fun showRegister() {
        val b = base("Yeni hesap")
        pageIntro(b, "YükLab'a katıl", "Bireysel kullanıcı olarak başla, istersen hesabını daha sonra taşıyıcı hesabına dönüştür.")
        val first = field(b, "Ad")
        val last = field(b, "Soyad")
        val email = field(b, "E-posta")
        val pass = field(b, "Şifre", true)
        primaryButton(b, "Hesap oluştur") {
            val body = JSONObject()
                .put("firstName", first.text.toString().trim())
                .put("lastName", last.text.toString().trim())
                .put("email", email.text.toString().trim())
                .put("password", pass.text.toString())
            request("/v1/auth/register", "POST", body, null) { ok, data ->
                if (ok) { toast("Hesap oluşturuldu"); showLogin() }
                else toast(data.optString("error", "Kayıt başarısız"))
            }
        }
        secondaryButton(b, "Giriş ekranına dön") { showLogin() }
    }

    private fun showCreateOrder(defaultService: String = "LOAD") {
        if (token == null) return showLogin()
        val b = base("Taşıma talebi")
        pageIntro(b, "Yeni taşıma oluştur", "Rota ve yük bilgilerini gir. Uygun taşıyıcılar eşleştirme motorunda sıralansın.")

        val service = spinner(b, "Hizmet türü", arrayOf("LOAD", "COURIER", "EMERGENCY"), defaultService)
        val pickup = field(b, "Nereden? Alım adresi")
        secondaryButton(b, "Mevcut konumumu kullan") { location { lat, lng -> pickup.setText("$lat,$lng") } }
        val delivery = field(b, "Nereye? Teslimat adresi")
        val weight = field(b, "Ağırlık (kg)")
        val volume = field(b, "Hacim (m³)")
        val vehicle = spinner(b, "Araç tipi", arrayOf("ANY", "MOTORCYCLE", "VAN", "TRUCK", "TRACTOR_TRAILER"), "ANY")
        val budget = field(b, "Bütçe (TL)")
        val refrigerated = CheckBox(this).apply {
            text = "Soğutuculu araç gerekli"
            setTextColor(YukLabTheme.TEXT)
        }
        b.addView(refrigerated, lp(mBottom = 14))

        primaryButton(b, "Talebi yayınla") {
            if (pickup.text.isBlank()) return@primaryButton toast("Alım adresi gerekli")
            val payload = JSONObject().apply {
                weight.text.toString().toDoubleOrNull()?.let { put("weightKg", it) }
                volume.text.toString().toDoubleOrNull()?.let { put("volumeM3", it) }
                put("vehicleType", vehicle.selectedItem.toString())
                put("refrigerated", refrigerated.isChecked)
            }
            val body = JSONObject()
                .put("serviceType", service.selectedItem.toString())
                .put("pickupAddress", pickup.text.toString().trim())
                .put("deliveryAddress", delivery.text.toString().trim())
                .put("currency", "TRY")
                .put("payload", payload)
            budget.text.toString().toDoubleOrNull()?.let { body.put("budgetMinor", (it * 100).toLong()) }
            request("/v1/orders", "POST", body, token) { ok, data ->
                if (ok) { toast("Talep yayınlandı"); showOrders() }
                else toast(data.optString("error", "Talep oluşturulamadı"))
            }
        }
        secondaryButton(b, "Ana sayfaya dön") { showHome() }
    }

    private fun showEmergencyOrder() = showCreateOrder("EMERGENCY")

    private fun showOrders() {
        if (token == null) return showLogin()
        val b = base("Siparişlerim")
        pageIntro(b, "Taşıma hareketleri", "Aktif ve geçmiş taleplerin sunucudan canlı olarak alınır.")
        val status = YukLabTheme.text(this, "Yükleniyor...", 13f, YukLabTheme.MUTED)
        b.addView(status, lp(mBottom = 12))
        request("/v1/orders", "GET", null, token) { ok, data ->
            status.visibility = View.GONE
            if (!ok) return@request toast(data.optString("error", "Siparişler alınamadı"))
            val arr = data.optJSONArray("orders")
            if (arr == null || arr.length() == 0) {
                emptyState(b, "Henüz taşıma talebin yok", "İlk talebini oluşturduğunda burada görünecek.")
            } else {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val card = YukLabTheme.card(this)
                    card.addView(YukLabTheme.text(this, "${serviceLabel(o.optString("serviceType"))} • ${o.optString("status")}", 15f, YukLabTheme.TURQUOISE_DARK, true))
                    card.addView(space(8))
                    card.addView(YukLabTheme.text(this, o.optString("pickupAddress", "-"), 15f, YukLabTheme.TEXT, true))
                    card.addView(YukLabTheme.text(this, "↓", 15f, YukLabTheme.MUTED))
                    card.addView(YukLabTheme.text(this, o.optString("deliveryAddress", "-"), 15f, YukLabTheme.TEXT))
                    card.addView(space(12))
                    card.addView(YukLabTheme.button(this, "Uygun taşıyıcıları gör", primary = false) { showMatches(o.optString("id")) })
                    b.addView(card, lp(mBottom = 12))
                }
            }
        }
        secondaryButton(b, "Yenile") { showOrders() }
        secondaryButton(b, "Ana sayfa") { showHome() }
    }

    private fun showMatches(orderId: String) {
        val b = base("Akıllı eşleşme")
        pageIntro(b, "Uygun taşıyıcılar", "Mesafe, puan ve araç uygunluğuna göre sıralanan sonuçlar.")
        val loading = YukLabTheme.text(this, "Eşleşmeler hesaplanıyor...", 13f, YukLabTheme.MUTED)
        b.addView(loading, lp(mBottom = 12))
        request("/v1/orders/$orderId/matches", "GET", null, token) { ok, data ->
            loading.visibility = View.GONE
            if (!ok) return@request toast(data.optString("error", "Eşleşme alınamadı"))
            val arr = data.optJSONArray("matches")
            if (arr == null || arr.length() == 0) emptyState(b, "Henüz eşleşme yok", "Uygun taşıyıcı bulunduğunda burada listelenecek.")
            else for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val card = YukLabTheme.card(this)
                card.addView(YukLabTheme.text(this, "Eşleşme skoru ${m.optDouble("score")}", 17f, YukLabTheme.TEXT, true))
                card.addView(space(7))
                card.addView(YukLabTheme.text(this, "${m.optDouble("distanceKm")} km • Puan ${m.optDouble("rating")}", 14f, YukLabTheme.MUTED))
                card.addView(YukLabTheme.text(this, "Araç: ${m.optString("vehicleType", "-")} ${m.optString("vehicleSubtype", "")}", 14f, YukLabTheme.MUTED))
                b.addView(card, lp(mBottom = 12))
            }
        }
        secondaryButton(b, "Siparişlere dön") { showOrders() }
    }

    private fun showProvider() {
        if (token == null) return showLogin()
        val b = base("Taşıyıcı merkezi")
        pageIntro(b, "İşini büyüt", "Uygun taşıma işlerini gör, tekliflerini ve filonu yönet.")
        actionCard(b, "Uygun işler", "Taşıyıcı hesabına açık siparişleri görüntüle", "→") { providerOrders() }
        actionCard(b, "Tekliflerim", "Gönderdiğin tekliflerin durumunu takip et", "→") { providerOffers() }
        actionCard(b, "Araçlarım", "Filondaki kayıtlı araçları görüntüle", "→") { vehicles() }
        primaryButton(b, "Taşıyıcı hesabını etkinleştir") {
            request("/v1/auth/become-provider", "POST", JSONObject().put("category", "GENERAL"), token) { ok, data ->
                toast(if (ok) "Taşıyıcı hesabı etkinleştirildi" else data.optString("error", "İşlem başarısız"))
            }
        }
        secondaryButton(b, "Ana sayfa") { showHome() }
    }

    private fun providerOrders() {
        val b = base("Uygun işler")
        request("/v1/provider/orders", "GET", null, token) { ok, data ->
            if (!ok) return@request toast(data.optString("error", "İşler alınamadı"))
            val a = data.optJSONArray("orders")
            if (a == null || a.length() == 0) emptyState(b, "Şu an uygun iş yok", "Yeni işler geldikçe burada görünecek.")
            else for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val card = YukLabTheme.card(this)
                card.addView(YukLabTheme.text(this, "${serviceLabel(o.optString("serviceType"))} • ${o.optString("status")}", 15f, YukLabTheme.TURQUOISE_DARK, true))
                card.addView(space(7))
                card.addView(YukLabTheme.text(this, "${o.optString("pickupAddress")} → ${o.optString("deliveryAddress")}", 14f, YukLabTheme.TEXT))
                b.addView(card, lp(mBottom = 12))
            }
        }
        secondaryButton(b, "Taşıyıcı merkezine dön") { showProvider() }
    }

    private fun providerOffers() {
        val b = base("Tekliflerim")
        request("/v1/provider/offers", "GET", null, token) { ok, data ->
            if (!ok) return@request toast(data.optString("error", "Teklifler alınamadı"))
            val a = data.optJSONArray("offers")
            if (a == null || a.length() == 0) emptyState(b, "Henüz teklif yok", "Gönderdiğin teklifler burada listelenecek.")
            else for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val card = YukLabTheme.card(this)
                card.addView(YukLabTheme.text(this, "${o.optString("status")} • ${o.optLong("amountMinor") / 100.0} ${o.optString("currency")}", 16f, YukLabTheme.TEXT, true))
                card.addView(YukLabTheme.text(this, o.optString("note", ""), 14f, YukLabTheme.MUTED))
                b.addView(card, lp(mBottom = 12))
            }
        }
        secondaryButton(b, "Taşıyıcı merkezine dön") { showProvider() }
    }

    private fun vehicles() {
        val b = base("Araçlarım")
        request("/v1/vehicles", "GET", null, token) { ok, data ->
            if (!ok) return@request toast(data.optString("error", "Araçlar alınamadı"))
            val a = data.optJSONArray("vehicles")
            if (a == null || a.length() == 0) emptyState(b, "Kayıtlı araç yok", "Araç ekleme akışı sonraki sürümde bu ekrana bağlanacak.")
            else for (i in 0 until a.length()) {
                val v = a.getJSONObject(i)
                val card = YukLabTheme.card(this)
                card.addView(YukLabTheme.text(this, "${v.optString("type")} ${v.optString("subtype", "")}", 17f, YukLabTheme.TEXT, true))
                card.addView(space(6))
                card.addView(YukLabTheme.text(this, "Plaka: ${v.optString("plateNumber", "-")}", 14f, YukLabTheme.MUTED))
                card.addView(YukLabTheme.text(this, "Kapasite: ${v.optString("capacityKg", "-")} kg • ${v.optString("volumeM3", "-")} m³", 14f, YukLabTheme.MUTED))
                b.addView(card, lp(mBottom = 12))
            }
        }
        secondaryButton(b, "Taşıyıcı merkezine dön") { showProvider() }
    }

    private fun showSettings() {
        val b = base("Ayarlar")
        pageIntro(b, "Uygulama ayarları", "Bağlantı, konum ve oturum seçenekleri.")
        fieldLabel(b, "API sunucusu")
        val s = YukLabTheme.input(this, "https://api.example.com").apply { setText(server) }
        b.addView(s, lp(mBottom = 12))
        primaryButton(b, "Sunucu adresini kaydet") {
            server = s.text.toString().trim().removeSuffix("/")
            prefs.edit().putString("server", server).apply()
            toast("Sunucu adresi kaydedildi")
        }
        secondaryButton(b, "Konum izni ver") {
            if (android.os.Build.VERSION.SDK_INT >= 23) requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 42)
        }
        if (token != null) secondaryButton(b, "Oturumu kapat") {
            prefs.edit().remove("token").apply(); token = null; showHome()
        }
        secondaryButton(b, "Ana sayfa") { showHome() }
    }

    private fun actionCard(parent: LinearLayout, title: String, subtitle: String, icon: String, onClick: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(17), dp(16), dp(14), dp(16))
            background = YukLabTheme.shape(this@MainActivity, Color.WHITE, 18f, YukLabTheme.BORDER)
            elevation = dp(1).toFloat()
            isClickable = true
            setOnClickListener { onClick() }
        }
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(YukLabTheme.text(this, title, 16f, YukLabTheme.TEXT, true))
        copy.addView(space(3))
        copy.addView(YukLabTheme.text(this, subtitle, 13f, YukLabTheme.MUTED))
        card.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(YukLabTheme.text(this, icon, 22f, YukLabTheme.TURQUOISE, true))
        parent.addView(card, lp(mBottom = 10))
    }

    private fun pageIntro(parent: LinearLayout, title: String, subtitle: String) {
        parent.addView(YukLabTheme.text(this, title, 25f, YukLabTheme.ANTHRACITE, true), lp(mBottom = 7))
        parent.addView(YukLabTheme.text(this, subtitle, 14f, YukLabTheme.MUTED), lp(mBottom = 20))
    }

    private fun sectionTitle(parent: LinearLayout, value: String) {
        parent.addView(YukLabTheme.text(this, value, 17f, YukLabTheme.ANTHRACITE, true), lp(mBottom = 10))
    }

    private fun field(parent: LinearLayout, hint: String, password: Boolean = false): EditText {
        val input = YukLabTheme.input(this, hint, password)
        parent.addView(input, lp(mBottom = 11))
        return input
    }

    private fun fieldLabel(parent: LinearLayout, value: String) {
        parent.addView(YukLabTheme.text(this, value, 12f, YukLabTheme.MUTED, true), lp(mBottom = 6))
    }

    private fun spinner(parent: LinearLayout, label: String, values: Array<String>, selected: String): Spinner {
        fieldLabel(parent, label)
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, values)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = YukLabTheme.shape(this@MainActivity, Color.WHITE, 14f, YukLabTheme.BORDER)
            setSelection(values.indexOf(selected).coerceAtLeast(0))
        }
        parent.addView(spinner, lp(mBottom = 12))
        return spinner
    }

    private fun primaryButton(parent: LinearLayout, label: String, onClick: () -> Unit) {
        parent.addView(YukLabTheme.button(this, label, true, onClick), lp(mBottom = 10))
    }

    private fun secondaryButton(parent: LinearLayout, label: String, onClick: () -> Unit) {
        parent.addView(YukLabTheme.button(this, label, false, onClick), lp(mBottom = 10))
    }

    private fun emptyState(parent: LinearLayout, title: String, subtitle: String) {
        val card = YukLabTheme.card(this)
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.addView(YukLabTheme.text(this, title, 17f, YukLabTheme.TEXT, true))
        card.addView(space(6))
        card.addView(YukLabTheme.text(this, subtitle, 14f, YukLabTheme.MUTED))
        parent.addView(card, lp(mBottom = 14))
    }

    private fun serviceLabel(raw: String) = when (raw) {
        "LOAD" -> "Yük taşıma"
        "COURIER" -> "Kurye"
        "EMERGENCY" -> "Acil yardım"
        else -> raw
    }

    private fun request(path: String, method: String, body: JSONObject?, bearer: String?, done: (Boolean, JSONObject) -> Unit) {
        if (server.isBlank()) return toast("API sunucu adresi ayarlanmadı")
        executor.execute {
            try {
                val c = URL(server + path).openConnection() as HttpURLConnection
                c.requestMethod = method
                c.connectTimeout = 15000
                c.readTimeout = 20000
                c.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json")
                    c.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                if (!bearer.isNullOrBlank()) c.setRequestProperty("Authorization", "Bearer $bearer")
                val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
                val txt = stream?.bufferedReader()?.readText() ?: "{}"
                val ok = c.responseCode in 200..299
                val result = try { JSONObject(txt) } catch (_: Exception) { JSONObject().put("error", txt.ifBlank { "Sunucu yanıtı okunamadı" }) }
                c.disconnect()
                runOnUiThread { done(ok, result) }
            } catch (e: Exception) {
                runOnUiThread { done(false, JSONObject().put("error", e.message ?: "Bağlantı hatası")) }
            }
        }
    }

    private fun location(done: (Double, Double) -> Unit) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 42)
            return toast("Konum izni verin")
        }
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val provider = lm.getProviders(true).firstOrNull() ?: return toast("Konum servisi kapalı")
        val loc: Location? = try { lm.getLastKnownLocation(provider) } catch (_: Exception) { null }
        if (loc != null) done(loc.latitude, loc.longitude) else toast("Konum henüz hazır değil")
    }

    private fun lp(w: Int = -1, h: Int = -2, mTop: Int = 0, mBottom: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { setMargins(0, dp(mTop), 0, dp(mBottom)) }

    private fun space(height: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(value: String) = runOnUiThread { Toast.makeText(this, value, Toast.LENGTH_LONG).show() }
}
