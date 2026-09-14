package com.yuklab.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val session by lazy { SessionStore(this) }
    private var token: String? = null
    private var refreshToken: String? = null
    private var server: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = session.accessToken
        refreshToken = session.refreshToken
        server = session.apiBaseUrl
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        if (android.os.Build.VERSION.SDK_INT >= 23) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        showPanel()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    // ---------- Main navigation ----------

    private fun showPanel() {
        val body = base("YükLab", activeTab = 0)
        body.addView(YukLabTheme.text(this, "Hoş geldin,", 23f, YukLabTheme.ANTHRACITE, true), lp(mBottom = 3))
        body.addView(YukLabTheme.text(this, "Yük ve taşıma işlerini tek yerden yönet.", 14f, YukLabTheme.MUTED), lp(mBottom = 18))

        val hero = YukLabTheme.card(this, dark = true)
        hero.addView(YukLabTheme.text(this, "HER YÜKE BİR YOL", 13f, YukLabTheme.TURQUOISE, true))
        hero.addView(space(10))
        hero.addView(YukLabTheme.text(this, "Yükünü kolayca\nyola çıkar.", 30f, Color.WHITE, true))
        hero.addView(space(10))
        hero.addView(YukLabTheme.text(this, "İlanını oluştur, taşıyıcılarla eşleş, tekliflerini tek yerden yönet.", 15f, Color.parseColor("#D9E0E3")))
        hero.addView(space(18))
        hero.addView(YukLabTheme.button(this, "Yeni ilan oluştur  +") { showCreateHub() })
        body.addView(hero, lp(mBottom = 16))

        if (token == null) {
            val loginCard = YukLabTheme.card(this)
            loginCard.addView(YukLabTheme.text(this, "Giriş yap veya hesap oluştur", 18f, YukLabTheme.TEXT, true))
            loginCard.addView(space(5))
            loginCard.addView(YukLabTheme.text(this, "İlanlarını ve tekliflerini hesabında sakla.", 14f, YukLabTheme.MUTED))
            loginCard.addView(space(12))
            loginCard.addView(YukLabTheme.button(this, "Giriş / Kayıt  →", false) { showLogin() })
            body.addView(loginCard, lp(mBottom = 20))
        }

        sectionTitle(body, "Hızlı işlemler")
        val row1 = horizontalRow()
        row1.addView(quickCard("İlanlarım", "Yayınladıkların ve güncel ilanların", "▤") { showListings() }, halfLp(right = 5))
        row1.addView(quickCard("Boş araçlar", "Müsait taşıyıcı işleri ve araçlar", "▣") { showProviderJobs() }, halfLp(left = 5))
        body.addView(row1, lp(mBottom = 10))
        val row2 = horizontalRow()
        row2.addView(quickCard("Tekliflerim", "Gönderilen teklifler ve fırsatlar", "◇") { showProviderOffers() }, halfLp(right = 5))
        row2.addView(quickCard("Portföyüm", "Taşıma geçmişi ve istatistikler", "▥") { showPortfolio() }, halfLp(left = 5))
        body.addView(row2, lp(mBottom = 16))
    }

    private fun showListings() {
        if (token == null) return showLogin()
        val body = base("İlanlar", activeTab = 1)
        val search = YukLabTheme.input(this, "Çıkış, varış veya yük ara")
        body.addView(search, lp(mBottom = 9))

        var selectedFilter = "ACTIVE"
        var orders: JSONArray? = null
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tabs = horizontalRow()
        fun refreshTabs() {
            tabs.removeAllViews()
            listOf("ACTIVE" to "Aktif", "WAITING" to "Teklif bekleyen", "DONE" to "Tamamlanan").forEach { pair ->
                tabs.addView(chip(pair.second, selectedFilter == pair.first) {
                    selectedFilter = pair.first
                    refreshTabs()
                    renderOrders(list, orders, search.text.toString(), selectedFilter)
                }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
        }
        refreshTabs()
        body.addView(tabs, lp(mBottom = 12))
        body.addView(YukLabTheme.button(this, "Ara / filtrele", false) { renderOrders(list, orders, search.text.toString(), selectedFilter) }, lp(mBottom = 12))
        body.addView(list)

        list.addView(loadingCard("İlanlar yükleniyor…"), lp(mBottom = 10))
        request("/v1/orders", "GET", null, token) { ok, data ->
            if (!ok) {
                list.removeAllViews()
                errorState(list, data.optString("error", "İlanlar yüklenirken bir sorun oluştu.")) { showListings() }
                return@request
            }
            orders = data.optJSONArray("orders") ?: JSONArray()
            renderOrders(list, orders, search.text.toString(), selectedFilter)
        }
    }

    private fun renderOrders(parent: LinearLayout, orders: JSONArray?, query: String, filter: String) {
        parent.removeAllViews()
        if (orders == null) return parent.addView(loadingCard("İlanlar yükleniyor…"))
        val q = query.trim().lowercase(Locale("tr", "TR"))
        var shown = 0
        for (i in 0 until orders.length()) {
            val order = orders.optJSONObject(i) ?: continue
            val status = order.optString("status")
            val filterMatch = when (filter) {
                "WAITING" -> status == "OFFERING"
                "DONE" -> status in listOf("DELIVERED", "COMPLETED")
                else -> status in listOf("DRAFT", "PUBLISHED", "OFFERING", "ACCEPTED", "DRIVER_ASSIGNED", "EN_ROUTE_PICKUP", "ARRIVED_PICKUP", "LOADED", "IN_TRANSIT", "ARRIVED_DELIVERY")
            }
            val payload = order.optJSONObject("payload")
            val haystack = listOf(order.optString("pickupAddress"), order.optString("deliveryAddress"), payload?.optString("loadType") ?: "", payload?.optString("vehicleType") ?: "").joinToString(" ").lowercase(Locale("tr", "TR"))
            if (!filterMatch || (q.isNotBlank() && !haystack.contains(q))) continue
            shown++
            parent.addView(orderCard(order), lp(mBottom = 12))
        }
        if (shown == 0) emptyState(parent, "Henüz ilan yok", "Bu filtreye uyan bir ilan bulunamadı.", "Yenile") { showListings() }
    }

    private fun orderCard(order: JSONObject): View {
        val card = YukLabTheme.card(this)
        val payload = order.optJSONObject("payload") ?: JSONObject()
        card.addView(YukLabTheme.text(this, "${serviceLabel(order.optString("serviceType"))}  •  ${statusLabel(order.optString("status"))}", 13f, YukLabTheme.TURQUOISE_DARK, true))
        card.addView(space(10))
        val route = horizontalRow()
        route.addView(YukLabTheme.text(this, "●  ${order.optString("pickupAddress", "-")}", 16f, YukLabTheme.TEXT, true), LinearLayout.LayoutParams(0, -2, 1f))
        route.addView(YukLabTheme.text(this, "→", 20f, YukLabTheme.MUTED, true))
        route.addView(YukLabTheme.text(this, "●  ${order.optString("deliveryAddress", "-")}", 16f, YukLabTheme.TEXT, true), LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(route)
        card.addView(space(12))
        val details = listOfNotNull(
            payload.optString("loadType").takeIf { it.isNotBlank() }?.let { "Yük: $it" },
            payload.optDouble("weightKg").takeIf { it > 0 }?.let { "Ağırlık: ${formatNumber(it)} kg" },
            payload.optString("vehicleType").takeIf { it.isNotBlank() && it != "ANY" }?.let { "Araç: $it" },
            order.optString("scheduledAt").takeIf { it.isNotBlank() && it != "null" }?.let { "Alım: ${shortDate(it)}" }
        )
        if (details.isNotEmpty()) card.addView(YukLabTheme.text(this, details.joinToString("   •   "), 13f, YukLabTheme.MUTED))
        val price = money(order.optString("budgetMinor"), order.optString("currency", "TRY"))
        if (price != null) {
            card.addView(space(10))
            card.addView(YukLabTheme.text(this, price, 22f, YukLabTheme.TURQUOISE_DARK, true))
        }
        card.addView(space(13))
        card.addView(YukLabTheme.button(this, "Detayları gör  →") { showOrderDetail(order.optString("id")) })
        return card
    }

    private fun showOrderDetail(orderId: String) {
        if (orderId.isBlank()) return
        val body = base("İlan detayı", back = { showListings() })
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        holder.addView(loadingCard("İlan bilgileri yükleniyor…"))
        body.addView(holder)
        request("/v1/orders/$orderId", "GET", null, token) { ok, data ->
            holder.removeAllViews()
            if (!ok) return@request errorState(holder, data.optString("error", "İlan alınamadı.")) { showOrderDetail(orderId) }
            val order = data.optJSONObject("order") ?: return@request errorState(holder, "İlan yanıtı eksik.") { showOrderDetail(orderId) }
            holder.addView(orderCardSummary(order), lp(mBottom = 12))
            holder.addView(YukLabTheme.button(this, "Gelen teklifleri gör", false) { showOrderOffers(orderId) }, lp(mBottom = 9))
            if (order.optString("status") in listOf("DRAFT", "PUBLISHED", "OFFERING")) {
                holder.addView(YukLabTheme.button(this, "İlanı düzenle", false) { showEditOrder(order) }, lp(mBottom = 9))
                holder.addView(YukLabTheme.dangerButton(this, "İlanı yayından kaldır") { confirmCancelOrder(orderId) }, lp(mBottom = 9))
            }
        }
    }

    private fun orderCardSummary(order: JSONObject): View {
        val card = YukLabTheme.card(this)
        val payload = order.optJSONObject("payload") ?: JSONObject()
        card.addView(YukLabTheme.text(this, statusLabel(order.optString("status")), 13f, YukLabTheme.TURQUOISE_DARK, true))
        card.addView(space(8))
        card.addView(YukLabTheme.text(this, order.optString("pickupAddress", "-"), 20f, YukLabTheme.TEXT, true))
        card.addView(YukLabTheme.text(this, "↓", 18f, YukLabTheme.MUTED))
        card.addView(YukLabTheme.text(this, order.optString("deliveryAddress", "-"), 20f, YukLabTheme.TEXT, true))
        card.addView(space(12))
        val lines = mutableListOf<String>()
        payload.optString("loadType").takeIf { it.isNotBlank() }?.let { lines += "Yük türü: $it" }
        payload.optDouble("weightKg").takeIf { it > 0 }?.let { lines += "Ağırlık: ${formatNumber(it)} kg" }
        payload.optString("dimensions").takeIf { it.isNotBlank() }?.let { lines += "Ölçüler: $it" }
        payload.optString("vehicleType").takeIf { it.isNotBlank() }?.let { lines += "Araç: $it" }
        payload.optString("deliveryDate").takeIf { it.isNotBlank() }?.let { lines += "Teslim tarihi: $it" }
        payload.optString("notes").takeIf { it.isNotBlank() }?.let { lines += "Açıklama: $it" }
        money(order.optString("budgetMinor"), order.optString("currency", "TRY"))?.let { lines += "Fiyat: $it" }
        lines.forEach { card.addView(YukLabTheme.text(this, it, 14f, YukLabTheme.MUTED), lp(mBottom = 4)) }
        return card
    }

    private fun showEditOrder(order: JSONObject) {
        val body = base("İlanı düzenle", back = { showOrderDetail(order.optString("id")) })
        val pickup = labeledField(body, "Çıkış noktası", order.optString("pickupAddress"))
        val delivery = labeledField(body, "Varış noktası", order.optString("deliveryAddress"))
        val budget = labeledField(body, "Fiyat (₺)", order.optString("budgetMinor").toLongOrNull()?.let { (it / 100.0).toString() } ?: "")
        body.addView(YukLabTheme.button(this, "Değişiklikleri kaydet") {
            if (pickup.text.isBlank() || delivery.text.isBlank()) return@button toast("Çıkış ve varış noktaları gerekli.")
            val patch = JSONObject().put("pickupAddress", pickup.text.toString().trim()).put("deliveryAddress", delivery.text.toString().trim())
            budget.text.toString().toDoubleOrNull()?.let { patch.put("budgetMinor", Math.round(it * 100)) }
            request("/v1/orders/${order.optString("id")}", "PATCH", patch, token) { ok, data ->
                if (ok) { toast("İlan güncellendi."); showOrderDetail(order.optString("id")) }
                else toast(errorMessage(data, "İlan güncellenemedi."))
            }
        }, lp(mBottom = 10))
    }

    private fun confirmCancelOrder(orderId: String) {
        AlertDialog.Builder(this)
            .setTitle("İlanı yayından kaldır")
            .setMessage("Bu ilan iptal durumuna alınacak. Devam edilsin mi?")
            .setNegativeButton("Vazgeç", null)
            .setPositiveButton("Yayından kaldır") { _, _ ->
                request("/v1/orders/$orderId", "DELETE", null, token) { ok, data ->
                    if (ok) { toast("İlan yayından kaldırıldı."); showListings() }
                    else toast(errorMessage(data, "İlan kaldırılamadı."))
                }
            }.show()
    }

    private fun showOrderOffers(orderId: String) {
        val body = base("Gelen teklifler", back = { showOrderDetail(orderId) })
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        holder.addView(loadingCard("Teklifler yükleniyor…"))
        body.addView(holder)
        request("/v1/orders/$orderId/offers", "GET", null, token) { ok, data ->
            holder.removeAllViews()
            if (!ok) return@request errorState(holder, errorMessage(data, "Teklifler alınamadı.")) { showOrderOffers(orderId) }
            val offers = data.optJSONArray("offers") ?: JSONArray()
            if (offers.length() == 0) return@request emptyState(holder, "Henüz teklif yok", "Taşıyıcı teklifleri geldiğinde burada listelenecek.")
            for (i in 0 until offers.length()) {
                val offer = offers.optJSONObject(i) ?: continue
                val card = YukLabTheme.card(this)
                val provider = offer.optJSONObject("provider")
                card.addView(YukLabTheme.text(this, provider?.let { "${it.optString("firstName")} ${it.optString("lastName")}" } ?: "Taşıyıcı", 17f, YukLabTheme.TEXT, true))
                card.addView(YukLabTheme.text(this, money(offer.optString("amountMinor"), offer.optString("currency", "TRY")) ?: "-", 21f, YukLabTheme.TURQUOISE_DARK, true))
                offer.optInt("etaMinutes").takeIf { it > 0 }?.let { card.addView(YukLabTheme.text(this, "Tahmini süre: $it dk", 13f, YukLabTheme.MUTED)) }
                offer.optString("note").takeIf { it.isNotBlank() }?.let { card.addView(YukLabTheme.text(this, it, 13f, YukLabTheme.MUTED)) }
                if (offer.optString("status") == "PENDING") {
                    card.addView(space(10))
                    card.addView(YukLabTheme.button(this, "Teklifi kabul et") {
                        request("/v1/orders/$orderId/offers/${offer.optString("id")}/accept", "POST", null, token) { accepted, response ->
                            if (accepted) { toast("Teklif kabul edildi."); showOrderOffers(orderId) }
                            else toast(errorMessage(response, "Teklif kabul edilemedi."))
                        }
                    })
                }
                holder.addView(card, lp(mBottom = 10))
            }
        }
    }

    private fun showCreateHub() {
        val body = base("Yeni oluştur", activeTab = 2)
        pageIntro(body, "Nasıl başlamak istersin?", "Yükünü veya aracını birkaç adımda ekle.")
        actionCard(body, "Yük ilanı oluştur", "Güzergâh, yük ve fiyat bilgilerini girerek ilanını oluştur.", "□") { showCreateOrder() }
        actionCard(body, "Metinden ilan hazırla", "LLM bağlı değil; metnini güvenli bir yerel taslak olarak sakla.", "✧") { showTextDraft() }
        actionCard(body, "Taşıyıcı olmak istiyorum", "Araç ve çalışma bölgesi bilgilerinle taşıyıcı hesabını hazırla.", "▣") { showCarrierOnboarding() }
    }

    private fun showCreateOrder() {
        if (token == null) return showLogin()
        val body = base("Yük ilanı oluştur", back = { showCreateHub() })
        pageIntro(body, "Yeni yük ilanı", "Zorunlu alanları doldur, önizle ve yayınla.")
        val draft = session.draft(SessionStore.DRAFT_ORDER)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()

        sectionLabel(body, "1 · Güzergâh")
        val pickup = labeledField(body, "Çıkış noktası *", draft.optString("pickupAddress"))
        body.addView(YukLabTheme.button(this, "Mevcut konumu kullan", false) { location { lat, lng -> pickup.setText("$lat,$lng") } }, lp(mBottom = 10))
        val delivery = labeledField(body, "Varış noktası *", draft.optString("deliveryAddress"))

        sectionLabel(body, "2 · Yük bilgileri")
        val loadType = labeledField(body, "Yük türü *", draft.optString("loadType"))
        val weight = labeledField(body, "Ağırlık (kg) *", draft.optString("weightKg"))
        val dimensions = labeledField(body, "Ölçüler (örn. 120×80×150 cm)", draft.optString("dimensions"))
        val vehicle = spinner(body, "Araç tipi", arrayOf("ANY", "MOTORCYCLE", "VAN", "TRUCK", "KIRKAYAK", "TIR", "TOW_TRUCK", "REFRIGERATED", "DUMP_TRUCK", "FLATBED"), draft.optString("vehicleType", "ANY"))

        sectionLabel(body, "3 · Tarih ve fiyat")
        val pickupDate = labeledField(body, "Alım tarihi (YYYY-AA-GG) *", draft.optString("pickupDate"))
        val deliveryDate = labeledField(body, "Teslim tarihi (YYYY-AA-GG)", draft.optString("deliveryDate"))
        val budget = labeledField(body, "Fiyat / bütçe (₺)", draft.optString("budget"))

        sectionLabel(body, "4 · Açıklama ve ekler")
        fieldLabel(body, "Açıklama")
        val notes = YukLabTheme.input(this, "Yükle ilgili önemli ayrıntılar", singleLine = false).apply { setText(draft.optString("notes")) }
        body.addView(notes, lp(mBottom = 10))
        val uploadInfo = infoCard("Fotoğraf ve ek dosya", "Dosya depolama/multipart backend'i henüz bağlı olmadığı için yükleme bu sürümde etkin değil. Bu alan çalışıyormuş gibi gösterilmiyor.")
        body.addView(uploadInfo, lp(mBottom = 12))

        body.addView(YukLabTheme.button(this, "Taslak kaydet", false) {
            session.saveDraft(SessionStore.DRAFT_ORDER, collectOrderDraft(pickup, delivery, loadType, weight, dimensions, vehicle, pickupDate, deliveryDate, budget, notes).toString())
            toast("Taslak bu cihazda kaydedildi.")
        }, lp(mBottom = 9))
        body.addView(YukLabTheme.button(this, "Önizle ve yayınla") {
            val payload = collectOrderDraft(pickup, delivery, loadType, weight, dimensions, vehicle, pickupDate, deliveryDate, budget, notes)
            val validation = validateOrderDraft(payload)
            if (validation != null) return@button toast(validation)
            showPublishPreview(payload)
        }, lp(mBottom = 12))
    }

    private fun collectOrderDraft(pickup: EditText, delivery: EditText, loadType: EditText, weight: EditText, dimensions: EditText, vehicle: Spinner, pickupDate: EditText, deliveryDate: EditText, budget: EditText, notes: EditText) =
        JSONObject()
            .put("pickupAddress", pickup.text.toString().trim())
            .put("deliveryAddress", delivery.text.toString().trim())
            .put("loadType", loadType.text.toString().trim())
            .put("weightKg", weight.text.toString().trim())
            .put("dimensions", dimensions.text.toString().trim())
            .put("vehicleType", vehicle.selectedItem.toString())
            .put("pickupDate", pickupDate.text.toString().trim())
            .put("deliveryDate", deliveryDate.text.toString().trim())
            .put("budget", budget.text.toString().trim())
            .put("notes", notes.text.toString().trim())

    private fun validateOrderDraft(draft: JSONObject): String? {
        if (draft.optString("pickupAddress").isBlank()) return "Çıkış noktası gerekli."
        if (draft.optString("deliveryAddress").isBlank()) return "Varış noktası gerekli."
        if (draft.optString("loadType").isBlank()) return "Yük türü gerekli."
        val weight = draft.optString("weightKg").toDoubleOrNull() ?: return "Ağırlık sayısal ve sıfırdan büyük olmalı."
        if (weight <= 0) return "Ağırlık sıfırdan büyük olmalı."
        val pickupDate = draft.optString("pickupDate")
        if (!validDate(pickupDate)) return "Alım tarihi YYYY-AA-GG formatında ve geçerli olmalı."
        val deliveryDate = draft.optString("deliveryDate")
        if (deliveryDate.isNotBlank() && !validDate(deliveryDate)) return "Teslim tarihi geçerli değil."
        if (deliveryDate.isNotBlank() && deliveryDate < pickupDate) return "Teslim tarihi alım tarihinden önce olamaz."
        val budgetText = draft.optString("budget")
        if (budgetText.isNotBlank() && (budgetText.toDoubleOrNull() == null || budgetText.toDouble() < 0)) return "Fiyat geçerli bir sayı olmalı."
        return null
    }

    private fun showPublishPreview(draft: JSONObject) {
        val summary = "${draft.optString("pickupAddress")} → ${draft.optString("deliveryAddress")}\n\n" +
            "Yük: ${draft.optString("loadType")} · ${draft.optString("weightKg")} kg\n" +
            "Araç: ${draft.optString("vehicleType")}\n" +
            "Alım: ${draft.optString("pickupDate")}\n" +
            (draft.optString("budget").takeIf { it.isNotBlank() }?.let { "Fiyat: $it ₺\n" } ?: "")
        AlertDialog.Builder(this)
            .setTitle("İlan önizleme")
            .setMessage(summary)
            .setNegativeButton("Düzenlemeye dön", null)
            .setPositiveButton("Yayınla") { _, _ -> publishOrder(draft) }
            .show()
    }

    private fun publishOrder(draft: JSONObject) {
        val payload = JSONObject()
            .put("loadType", draft.optString("loadType"))
            .put("weightKg", draft.optString("weightKg").toDouble())
            .put("dimensions", draft.optString("dimensions"))
            .put("vehicleType", draft.optString("vehicleType"))
            .put("deliveryDate", draft.optString("deliveryDate"))
            .put("notes", draft.optString("notes"))
        val body = JSONObject()
            .put("serviceType", "LOAD")
            .put("pickupAddress", draft.optString("pickupAddress"))
            .put("deliveryAddress", draft.optString("deliveryAddress"))
            .put("scheduledAt", "${draft.optString("pickupDate")}T09:00:00+03:00")
            .put("currency", "TRY")
            .put("payload", payload)
        draft.optString("budget").toDoubleOrNull()?.let { body.put("budgetMinor", Math.round(it * 100)) }
        request("/v1/orders", "POST", body, token) { ok, data ->
            if (ok) {
                session.saveDraft(SessionStore.DRAFT_ORDER, null)
                toast("İlan başarıyla yayınlandı.")
                showListings()
            } else toast(errorMessage(data, "İlan yayınlanamadı."))
        }
    }

    private fun showTextDraft() {
        val body = base("Metinden ilan taslağı", back = { showCreateHub() })
        pageIntro(body, "Metnini taslak olarak sakla", "Bu sürümde LLM/AI backend'i bağlı değil. Metin analiz edilmiş gibi davranılmayacak.")
        val text = YukLabTheme.input(this, "Örn. Kayseri'den Ankara'ya 2 ton paletli ürün…", singleLine = false).apply { setText(session.draft(SessionStore.DRAFT_TEXT) ?: "") }
        body.addView(text, lp(mBottom = 12))
        body.addView(YukLabTheme.button(this, "Yerel taslağı kaydet") {
            val value = text.text.toString().trim()
            if (value.isBlank()) return@button toast("Taslak metni boş olamaz.")
            session.saveDraft(SessionStore.DRAFT_TEXT, value)
            toast("Metin taslağı kaydedildi.")
        })
    }

    private fun showCarrierOnboarding() {
        if (token == null) return showLogin()
        val body = base("Taşıyıcı ol", back = { showCreateHub() })
        pageIntro(body, "Taşıyıcı hesabını hazırla", "Hesap türü, çalışma bölgesi ve ilk aracını gerçek API'ye kaydet.")
        val providerType = spinner(body, "Hesap türü", arrayOf("DRIVER", "SERVICE_PROVIDER"), "DRIVER")
        val category = labeledField(body, "Hizmet kategorisi", "GENERAL")
        val company = labeledField(body, "Firma adı (isteğe bağlı)")
        val city = labeledField(body, "Çalışma şehri *")
        val district = labeledField(body, "İlçe")
        val radius = labeledField(body, "Hizmet yarıçapı (km)", "50")
        val vehicleType = spinner(body, "Araç tipi", arrayOf("MOTORCYCLE", "VAN", "TRUCK", "KIRKAYAK", "TIR", "TOW_TRUCK", "REFRIGERATED", "DUMP_TRUCK", "FLATBED"), "TRUCK")
        val plate = labeledField(body, "Plaka *")
        val capacity = labeledField(body, "Kapasite (kg)")
        body.addView(infoCard("Belge doğrulama", "Belge modeli ve onay durumu backend'de var; gerçek dosya yükleme depolaması henüz bağlı değil. Belge yükleme butonu bu nedenle sahte olarak eklenmedi."), lp(mBottom = 12))
        body.addView(YukLabTheme.button(this, "Taşıyıcı hesabını oluştur") {
            if (city.text.isBlank() || plate.text.isBlank()) return@button toast("Şehir ve plaka zorunlu.")
            val km = radius.text.toString().toDoubleOrNull()
            if (km == null || km !in 1.0..500.0) return@button toast("Hizmet yarıçapı 1–500 km arasında olmalı.")
            val startProvider = {
                val providerBody = JSONObject().put("providerType", providerType.selectedItem.toString()).put("category", category.text.toString().trim().ifBlank { "GENERAL" })
                request("/v1/auth/become-provider", "POST", providerBody, token) { providerOk, providerData ->
                    if (!providerOk) return@request toast(errorMessage(providerData, "Taşıyıcı hesabı açılamadı."))
                    val regions = JSONArray().put(JSONObject().put("country", "TR").put("city", city.text.toString().trim()).put("district", district.text.toString().trim()).put("radiusKm", km))
                    request("/v1/users/me/work-regions", "PUT", JSONObject().put("regions", regions), token) { regionOk, regionData ->
                        if (!regionOk) return@request toast(errorMessage(regionData, "Çalışma bölgesi kaydedilemedi."))
                        val vehicleBody = JSONObject().put("type", vehicleType.selectedItem.toString()).put("plateNumber", plate.text.toString().trim())
                        capacity.text.toString().toDoubleOrNull()?.let { vehicleBody.put("capacityKg", it) }
                        request("/v1/vehicles", "POST", vehicleBody, token) { vehicleOk, vehicleData ->
                            if (vehicleOk) { toast("Taşıyıcı hesabı ve araç kaydı oluşturuldu."); showProfile() }
                            else toast(errorMessage(vehicleData, "Araç kaydı oluşturulamadı."))
                        }
                    }
                }
            }
            if (company.text.isNotBlank()) {
                request("/v1/users/me", "PATCH", JSONObject().put("company", JSONObject().put("companyName", company.text.toString().trim())), token) { companyOk, companyData ->
                    if (companyOk) startProvider() else toast(errorMessage(companyData, "Firma bilgisi kaydedilemedi."))
                }
            } else startProvider()
        })
    }

    private fun showPortfolio() {
        if (token == null) return showLogin()
        val body = base("Portföy", activeTab = 3)
        pageIntro(body, "Taşıma özeti", "Yalnızca gerçek hesabındaki verilerden hesaplanan istatistikler.")
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        holder.addView(loadingCard("Portföy hesaplanıyor…"))
        body.addView(holder)
        request("/v1/orders", "GET", null, token) { ok, data ->
            holder.removeAllViews()
            if (!ok) return@request errorState(holder, errorMessage(data, "Portföy yüklenemedi.")) { showPortfolio() }
            val orders = data.optJSONArray("orders") ?: JSONArray()
            var active = 0; var completed = 0; var cancelled = 0
            for (i in 0 until orders.length()) {
                when (orders.optJSONObject(i)?.optString("status")) {
                    "COMPLETED", "DELIVERED" -> completed++
                    "CANCELLED", "EXPIRED", "FAILED" -> cancelled++
                    else -> active++
                }
            }
            val row = horizontalRow()
            row.addView(statCard("${orders.length()}", "Toplam ilan"), halfLp(right = 5))
            row.addView(statCard("$active", "Aktif"), halfLp(left = 5))
            holder.addView(row, lp(mBottom = 10))
            val row2 = horizontalRow()
            row2.addView(statCard("$completed", "Tamamlanan"), halfLp(right = 5))
            row2.addView(statCard("$cancelled", "İptal / kapanan"), halfLp(left = 5))
            holder.addView(row2, lp(mBottom = 12))
            holder.addView(infoCard("Toplam taşıma mesafesi ve gelir", "Mevcut sipariş modelinde kesinleşmiş mesafe ve tahsilat kaydı tutulmadığı için burada uydurma rakam gösterilmiyor."), lp(mBottom = 12))
            if (session.role in listOf("DRIVER", "SERVICE_PROVIDER")) {
                request("/v1/provider/offers", "GET", null, token) { offersOk, offersData ->
                    if (offersOk) {
                        val count = offersData.optJSONArray("offers")?.length() ?: 0
                        holder.addView(statCard("$count", "Verilen teklifler"), lp(mBottom = 10))
                    }
                }
            }
        }
    }

    private fun showProfile() {
        val body = base("Profil", activeTab = 4)
        if (token == null) {
            val card = YukLabTheme.card(this)
            card.addView(YukLabTheme.text(this, "●", 46f, YukLabTheme.TURQUOISE, true))
            card.addView(YukLabTheme.text(this, "Hesabına giriş yap", 23f, YukLabTheme.TEXT, true))
            card.addView(space(7))
            card.addView(YukLabTheme.text(this, "İlanların, araçların ve portföyün burada.", 15f, YukLabTheme.MUTED))
            card.addView(space(16))
            card.addView(YukLabTheme.button(this, "Giriş / Kayıt  →") { showLogin() })
            body.addView(card, lp(mBottom = 18))
            sectionTitle(body, "Giriş yapmadan")
            actionCard(body, "Bağlantı ayarları", "API sunucusunu kontrol et", "↗") { showSettings() }
            actionCard(body, "Hakkında", "YükLab Global Smart Logistics Network", "i") { aboutDialog() }
            return
        }
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        holder.addView(loadingCard("Profil yükleniyor…"))
        body.addView(holder)
        request("/v1/users/me", "GET", null, token) { ok, data ->
            holder.removeAllViews()
            if (!ok) return@request errorState(holder, errorMessage(data, "Profil alınamadı.")) { showProfile() }
            val user = data.optJSONObject("user") ?: return@request errorState(holder, "Profil yanıtı eksik.") { showProfile() }
            renderProfile(holder, user)
        }
    }

    private fun renderProfile(parent: LinearLayout, user: JSONObject) {
        val identity = YukLabTheme.card(this)
        identity.addView(YukLabTheme.text(this, "●", 42f, YukLabTheme.TURQUOISE, true))
        identity.addView(YukLabTheme.text(this, "${user.optString("firstName")} ${user.optString("lastName")}", 23f, YukLabTheme.TEXT, true))
        identity.addView(YukLabTheme.text(this, user.optString("email", user.optString("phone", "")), 14f, YukLabTheme.MUTED))
        identity.addView(YukLabTheme.text(this, roleLabel(user.optString("role")), 13f, YukLabTheme.TURQUOISE_DARK, true))
        user.optJSONObject("companyProfile")?.optString("companyName")?.takeIf { it.isNotBlank() }?.let { identity.addView(YukLabTheme.text(this, it, 14f, YukLabTheme.MUTED)) }
        identity.addView(space(12))
        identity.addView(YukLabTheme.button(this, "Profili düzenle", false) { showEditProfile(user) })
        parent.addView(identity, lp(mBottom = 18))

        sectionTitle(parent, "Hesap ayarları")
        actionCard(parent, "Araçlarım", "Araç ekle, düzenle veya pasifleştir", "→") { showVehicles() }
        actionCard(parent, "İlanlarım", "Yayınlanan ve tamamlanan ilanlar", "→") { showListings() }
        actionCard(parent, "Tekliflerim", "Taşıyıcı tekliflerini yönet", "→") { showProviderOffers() }
        actionCard(parent, "Bildirimler", "Hesap bildirimlerini görüntüle", "→") { showNotifications() }
        actionCard(parent, "Bağlantı ayarları", "Sunucu ve uygulama bilgileri", "→") { showSettings() }
        actionCard(parent, "Şifre değiştir", "Mevcut şifreni güvenli şekilde yenile", "→") { showPasswordChange() }
        actionCard(parent, "Yardım ve destek", "Destek bilgileri", "→") { supportDialog() }
        parent.addView(YukLabTheme.button(this, "Çıkış yap", false) { logout() }, lp(mTop = 8, mBottom = 9))
        parent.addView(YukLabTheme.dangerButton(this, "Hesabı devre dışı bırak") { confirmDeleteAccount() }, lp(mBottom = 12))
    }

    private fun showEditProfile(user: JSONObject) {
        val body = base("Profili düzenle", back = { showProfile() })
        val first = labeledField(body, "Ad *", user.optString("firstName"))
        val last = labeledField(body, "Soyad *", user.optString("lastName"))
        val email = labeledField(body, "E-posta", user.optString("email"))
        val phone = labeledField(body, "Telefon", user.optString("phone"))
        val companyData = user.optJSONObject("companyProfile")
        val company = labeledField(body, "Firma adı", companyData?.optString("companyName") ?: "")
        val city = labeledField(body, "Firma şehri", companyData?.optString("city") ?: "")
        body.addView(YukLabTheme.button(this, "Kaydet") {
            if (first.text.isBlank() || last.text.isBlank()) return@button toast("Ad ve soyad zorunlu.")
            val patch = JSONObject()
                .put("firstName", first.text.toString().trim())
                .put("lastName", last.text.toString().trim())
                .put("email", email.text.toString().trim().ifBlank { JSONObject.NULL })
                .put("phone", phone.text.toString().trim().ifBlank { JSONObject.NULL })
            if (company.text.isNotBlank()) patch.put("company", JSONObject().put("companyName", company.text.toString().trim()).put("city", city.text.toString().trim().ifBlank { JSONObject.NULL }))
            request("/v1/users/me", "PATCH", patch, token) { ok, data ->
                if (ok) { toast("Profil güncellendi."); showProfile() }
                else toast(errorMessage(data, "Profil güncellenemedi."))
            }
        })
    }

    private fun showNotifications() {
        val body = base("Bildirimler", back = { showProfile() })
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        holder.addView(loadingCard("Bildirimler yükleniyor…"))
        body.addView(YukLabTheme.button(this, "Tümünü okundu işaretle", false) {
            request("/v1/notifications/read-all", "POST", JSONObject(), token) { ok, data ->
                if (ok) showNotifications() else toast(errorMessage(data, "Bildirimler güncellenemedi."))
            }
        }, lp(mBottom = 10))
        body.addView(holder)
        request("/v1/notifications", "GET", null, token) { ok, data ->
            holder.removeAllViews()
            if (!ok) return@request errorState(holder, errorMessage(data, "Bildirimler alınamadı.")) { showNotifications() }
            val array = data.optJSONArray("notifications") ?: JSONArray()
            if (array.length() == 0) return@request emptyState(holder, "Bildirim yok", "Yeni bildirimler burada görünecek.")
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val card = YukLabTheme.card(this)
                card.addView(YukLabTheme.text(this, item.optString("title"), 16f, YukLabTheme.TEXT, true))
                card.addView(YukLabTheme.text(this, item.optString("body"), 14f, YukLabTheme.MUTED))
                card.addView(YukLabTheme.text(this, shortDate(item.optString("createdAt")), 12f, YukLabTheme.MUTED))
                if (item.isNull("readAt")) {
                    card.addView(space(8))
                    card.addView(YukLabTheme.button(this, "Okundu işaretle", false) {
                        request("/v1/notifications/${item.optString("id")}/read", "PATCH", JSONObject(), token) { _, _ -> showNotifications() }
                    })
                }
                holder.addView(card, lp(mBottom = 9))
            }
        }
    }

    private fun showPasswordChange() {
        val body = base("Şifre değiştir", back = { showProfile() })
        val current = labeledField(body, "Mevcut şifre *", password = true)
        val next = labeledField(body, "Yeni şifre *", password = true)
        val again = labeledField(body, "Yeni şifre tekrar *", password = true)
        body.addView(YukLabTheme.button(this, "Şifreyi değiştir") {
            if (next.text.length < 8) return@button toast("Yeni şifre en az 8 karakter olmalı.")
            if (next.text.toString() != again.text.toString()) return@button toast("Yeni şifreler eşleşmiyor.")
            request("/v1/users/me/password", "POST", JSONObject().put("currentPassword", current.text.toString()).put("newPassword", next.text.toString()), token) { ok, data ->
                if (ok) {
                    clearSession()
                    toast("Şifre değiştirildi. Güvenlik nedeniyle yeniden giriş yap.")
                    showLogin()
                } else toast(errorMessage(data, "Şifre değiştirilemedi."))
            }
        })
    }

    private fun confirmDeleteAccount() {
        AlertDialog.Builder(this)
            .setTitle("Hesabı devre dışı bırak")
            .setMessage("Kişisel bilgiler temizlenecek, oturumlar kapatılacak ve araçların pasif duruma alınacak. Bu işlem ciddi bir işlemdir.")
            .setNegativeButton("Vazgeç", null)
            .setPositiveButton("Devre dışı bırak") { _, _ ->
                request("/v1/users/me", "DELETE", null, token) { ok, data ->
                    if (ok) { clearSession(); toast("Hesap devre dışı bırakıldı."); showPanel() }
                    else toast(errorMessage(data, "Hesap devre dışı bırakılamadı."))
                }
            }.show()
    }

    // ---------- Authentication ----------

    private fun showLogin() {
        val body = base("Giriş", back = { showPanel() })
        pageIntro(body, "Hesabına giriş yap", "İlanlarını, tekliflerini ve araçlarını güvenli oturumla yönet.")
        val identifier = labeledField(body, "E-posta veya telefon *")
        val password = labeledField(body, "Şifre *", password = true)
        body.addView(YukLabTheme.button(this, "Giriş yap") {
            if (identifier.text.isBlank() || password.text.isBlank()) return@button toast("E-posta/telefon ve şifre gerekli.")
            request("/v1/auth/login", "POST", JSONObject().put("identifier", identifier.text.toString().trim()).put("password", password.text.toString()), null, allowRefresh = false) { ok, data ->
                if (!ok) return@request toast(errorMessage(data, "Giriş başarısız."))
                val access = data.optString("accessToken")
                val refresh = data.optString("refreshToken")
                val role = data.optJSONObject("user")?.optString("role")
                if (access.isBlank() || refresh.isBlank()) return@request toast("Oturum yanıtı eksik.")
                token = access; refreshToken = refresh; session.saveSession(access, refresh, role)
                showPanel()
            }
        }, lp(mBottom = 9))
        body.addView(YukLabTheme.button(this, "Hesap oluştur", false) { showRegister() })
    }

    private fun showRegister() {
        val body = base("Yeni hesap", back = { showLogin() })
        pageIntro(body, "YükLab'a katıl", "Önce bireysel hesabını oluştur; taşıyıcı hesabına sonra geçebilirsin.")
        val first = labeledField(body, "Ad *")
        val last = labeledField(body, "Soyad *")
        val email = labeledField(body, "E-posta *")
        val password = labeledField(body, "Şifre (en az 8 karakter) *", password = true)
        body.addView(YukLabTheme.button(this, "Hesap oluştur") {
            if (first.text.isBlank() || last.text.isBlank() || email.text.isBlank() || password.text.length < 8) return@button toast("Tüm zorunlu alanları doğru doldur.")
            val payload = JSONObject().put("firstName", first.text.toString().trim()).put("lastName", last.text.toString().trim()).put("email", email.text.toString().trim()).put("password", password.text.toString())
            request("/v1/auth/register", "POST", payload, null, allowRefresh = false) { ok, data ->
                if (ok) { toast("Hesap oluşturuldu. Şimdi giriş yap."); showLogin() }
                else toast(errorMessage(data, "Kayıt başarısız."))
            }
        })
    }

    private fun logout() {
        val refresh = refreshToken
        if (token == null || refresh.isNullOrBlank()) {
            clearSession(); showPanel(); return
        }
        request("/v1/auth/logout", "POST", JSONObject().put("refreshToken", refresh), token) { _, _ ->
            clearSession()
            toast("Oturum kapatıldı.")
            showPanel()
        }
    }

    private fun clearSession() {
        token = null
        refreshToken = null
        session.clearSession()
    }

    // ---------- Provider / vehicles / offers ----------

    private fun showProviderJobs() {
        if (token == null) return showLogin()
        if (session.role !in listOf("DRIVER", "SERVICE_PROVIDER")) return showCarrierOnboarding()
        val body = base("Boş işler", back = { showPanel() })
        pageIntro(body, "Uygun taşıma işleri", "Taşıyıcı hesabına açık gerçek siparişler.")
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        holder.addView(loadingCard("İşler yükleniyor…"))
        body.addView(holder)
        request("/v1/provider/orders", "GET", null, token) { ok, data ->
            holder.removeAllViews()
            if (!ok) return@request errorState(holder, errorMessage(data, "İşler alınamadı.")) { showProviderJobs() }
            val array = data.optJSONArray("orders") ?: JSONArray()
            if (array.length() == 0) return@request emptyState(holder, "Şu an uygun iş yok", "Yeni işler geldikçe burada görünecek.")
            for (i in 0 until array.length()) {
                val order = array.optJSONObject(i) ?: continue
                val card = orderCard(order) as LinearLayout
                card.addView(space(8))
                card.addView(YukLabTheme.button(this, "Teklif ver", false) { showMakeOffer(order.optString("id")) })
                holder.addView(card, lp(mBottom = 10))
            }
        }
    }

    private fun showMakeOffer(orderId: String) {
        val body = base("Teklif ver", back = { showProviderJobs() })
        val amount = labeledField(body, "Teklif tutarı (₺) *")
        val eta = labeledField(body, "Tahmini süre (dakika)")
        val note = labeledField(body, "Not")
        body.addView(YukLabTheme.button(this, "Teklifi gönder") {
            val value = amount.text.toString().toDoubleOrNull()
            if (value == null || value <= 0) return@button toast("Teklif tutarı sıfırdan büyük olmalı.")
            val payload = JSONObject().put("amountMinor", Math.round(value * 100)).put("currency", "TRY").put("note", note.text.toString().trim())
            eta.text.toString().toIntOrNull()?.let { payload.put("etaMinutes", it) }
            request("/v1/orders/$orderId/offers", "POST", payload, token) { ok, data ->
                if (ok) { toast("Teklif gönderildi."); showProviderOffers() }
                else toast(errorMessage(data, "Teklif gönderilemedi."))
            }
        })
    }

    private fun showProviderOffers() {
        if (token == null) return showLogin()
        if (session.role !in listOf("DRIVER", "SERVICE_PROVIDER")) return toast("Teklifler taşıyıcı hesabı gerektirir.")
        val body = base("Tekliflerim", back = { showProfile() })
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        holder.addView(loadingCard("Teklifler yükleniyor…"))
        body.addView(holder)
        request("/v1/provider/offers", "GET", null, token) { ok, data ->
            holder.removeAllViews()
            if (!ok) return@request errorState(holder, errorMessage(data, "Teklifler alınamadı.")) { showProviderOffers() }
            val array = data.optJSONArray("offers") ?: JSONArray()
            if (array.length() == 0) return@request emptyState(holder, "Henüz teklif yok", "Gönderdiğin teklifler burada listelenecek.")
            for (i in 0 until array.length()) {
                val offer = array.optJSONObject(i) ?: continue
                val card = YukLabTheme.card(this)
                card.addView(YukLabTheme.text(this, money(offer.optString("amountMinor"), offer.optString("currency", "TRY")) ?: "-", 20f, YukLabTheme.TURQUOISE_DARK, true))
                card.addView(YukLabTheme.text(this, statusLabel(offer.optString("status")), 13f, YukLabTheme.MUTED))
                offer.optString("note").takeIf { it.isNotBlank() }?.let { card.addView(YukLabTheme.text(this, it, 13f, YukLabTheme.MUTED)) }
                if (offer.optString("status") == "PENDING") {
                    card.addView(space(10))
                    card.addView(YukLabTheme.button(this, "Teklifi düzenle", false) { showEditOffer(offer) }, lp(mBottom = 8))
                    card.addView(YukLabTheme.dangerButton(this, "Teklifi geri çek") {
                        request("/v1/orders/${offer.optString("orderId")}/offers/${offer.optString("id")}", "DELETE", null, token) { withdrawn, response ->
                            if (withdrawn) { toast("Teklif geri çekildi."); showProviderOffers() } else toast(errorMessage(response, "Teklif geri çekilemedi."))
                        }
                    })
                }
                holder.addView(card, lp(mBottom = 10))
            }
        }
    }

    private fun showEditOffer(offer: JSONObject) {
        val body = base("Teklifi düzenle", back = { showProviderOffers() })
        val amountValue = offer.optString("amountMinor").toLongOrNull()?.let { (it / 100.0).toString() } ?: ""
        val amount = labeledField(body, "Teklif tutarı (₺) *", amountValue)
        val eta = labeledField(body, "Tahmini süre (dakika)", offer.optInt("etaMinutes").takeIf { it > 0 }?.toString() ?: "")
        val note = labeledField(body, "Not", offer.optString("note"))
        body.addView(YukLabTheme.button(this, "Teklifi güncelle") {
            val value = amount.text.toString().toDoubleOrNull()
            if (value == null || value <= 0) return@button toast("Geçerli teklif tutarı gir.")
            val patch = JSONObject().put("amountMinor", Math.round(value * 100)).put("note", note.text.toString().trim())
            eta.text.toString().toIntOrNull()?.let { patch.put("etaMinutes", it) }
            request("/v1/orders/${offer.optString("orderId")}/offers/${offer.optString("id")}", "PATCH", patch, token) { ok, data ->
                if (ok) { toast("Teklif güncellendi."); showProviderOffers() } else toast(errorMessage(data, "Teklif güncellenemedi."))
            }
        })
    }

    private fun showVehicles() {
        if (token == null) return showLogin()
        if (session.role !in listOf("DRIVER", "SERVICE_PROVIDER")) return showCarrierOnboarding()
        val body = base("Araçlarım", back = { showProfile() })
        body.addView(YukLabTheme.button(this, "Yeni araç ekle  +") { showVehicleForm(null) }, lp(mBottom = 12))
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        holder.addView(loadingCard("Araçlar yükleniyor…"))
        body.addView(holder)
        request("/v1/vehicles", "GET", null, token) { ok, data ->
            holder.removeAllViews()
            if (!ok) return@request errorState(holder, errorMessage(data, "Araçlar alınamadı.")) { showVehicles() }
            val array = data.optJSONArray("vehicles") ?: JSONArray()
            if (array.length() == 0) return@request emptyState(holder, "Kayıtlı araç yok", "İlk aracını ekleyerek taşıyıcı profilini tamamla.", "Araç ekle") { showVehicleForm(null) }
            for (i in 0 until array.length()) {
                val vehicle = array.optJSONObject(i) ?: continue
                val card = YukLabTheme.card(this)
                card.addView(YukLabTheme.text(this, "${vehicle.optString("type")} ${vehicle.optString("subtype")}", 17f, YukLabTheme.TEXT, true))
                card.addView(YukLabTheme.text(this, "Plaka: ${vehicle.optString("plateNumber", "-")}", 14f, YukLabTheme.MUTED))
                card.addView(YukLabTheme.text(this, "Kapasite: ${vehicle.optString("capacityKg", "-")} kg", 14f, YukLabTheme.MUTED))
                card.addView(space(9))
                card.addView(YukLabTheme.button(this, "Düzenle", false) { showVehicleForm(vehicle) }, lp(mBottom = 7))
                if (vehicle.optBoolean("active", true)) card.addView(YukLabTheme.dangerButton(this, "Pasifleştir") {
                    request("/v1/vehicles/${vehicle.optString("id")}", "DELETE", null, token) { deleted, response ->
                        if (deleted) showVehicles() else toast(errorMessage(response, "Araç pasifleştirilemedi."))
                    }
                })
                holder.addView(card, lp(mBottom = 10))
            }
        }
    }

    private fun showVehicleForm(existing: JSONObject?) {
        val body = base(if (existing == null) "Araç ekle" else "Aracı düzenle", back = { showVehicles() })
        val values = arrayOf("MOTORCYCLE", "VAN", "TRUCK", "KIRKAYAK", "TIR", "TOW_TRUCK", "REFRIGERATED", "DUMP_TRUCK", "FLATBED")
        val type = spinner(body, "Araç tipi *", values, existing?.optString("type") ?: "TRUCK")
        val subtype = labeledField(body, "Alt tip", existing?.optString("subtype") ?: "")
        val plate = labeledField(body, "Plaka *", existing?.optString("plateNumber") ?: "")
        val capacity = labeledField(body, "Kapasite (kg)", existing?.optString("capacityKg") ?: "")
        val volume = labeledField(body, "Hacim (m³)", existing?.optString("volumeM3") ?: "")
        val refrigerated = CheckBox(this).apply { text = "Soğutuculu"; setTextColor(YukLabTheme.TEXT); isChecked = existing?.optBoolean("refrigerated") ?: false }
        body.addView(refrigerated, lp(mBottom = 10))
        body.addView(YukLabTheme.button(this, if (existing == null) "Aracı ekle" else "Değişiklikleri kaydet") {
            if (plate.text.isBlank()) return@button toast("Plaka zorunlu.")
            val payload = JSONObject().put("type", type.selectedItem.toString()).put("subtype", subtype.text.toString().trim()).put("plateNumber", plate.text.toString().trim()).put("refrigerated", refrigerated.isChecked)
            capacity.text.toString().toDoubleOrNull()?.let { payload.put("capacityKg", it) }
            volume.text.toString().toDoubleOrNull()?.let { payload.put("volumeM3", it) }
            val path = if (existing == null) "/v1/vehicles" else "/v1/vehicles/${existing.optString("id")}"
            val method = if (existing == null) "POST" else "PATCH"
            request(path, method, payload, token) { ok, data ->
                if (ok) { toast(if (existing == null) "Araç eklendi." else "Araç güncellendi."); showVehicles() }
                else toast(errorMessage(data, "Araç kaydedilemedi."))
            }
        })
    }

    // ---------- Settings ----------

    private fun showSettings() {
        val body = base("Ayarlar ve bağlantı", back = { showProfile() })
        val statusHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statusHolder.addView(infoCard("Bağlantı durumu", "Henüz test edilmedi."))
        body.addView(statusHolder, lp(mBottom = 12))

        val serverField = labeledField(body, "API sunucusu adresi", server)
        val buttons = horizontalRow()
        buttons.addView(YukLabTheme.button(this, "Sunucu adresini kaydet") {
            val value = serverField.text.toString().trim().removeSuffix("/")
            if (!validServer(value)) return@button toast("Geçerli bir https:// sunucu adresi gir.")
            server = value; session.apiBaseUrl = value
            toast("Sunucu adresi kaydedildi.")
        }, halfLp(right = 5))
        buttons.addView(YukLabTheme.button(this, "Bağlantıyı test et", false) {
            statusHolder.removeAllViews(); statusHolder.addView(infoCard("Kontrol ediliyor", "Sunucu yanıtı bekleniyor…"))
            request("/health", "GET", null, null, allowRefresh = false) { ok, data ->
                statusHolder.removeAllViews()
                statusHolder.addView(YukLabTheme.statusCard(this, ok && data.optString("status") == "ok", if (ok) "Bağlantı aktif" else "Bağlantı başarısız", if (ok) "YükLab API sunucusu sağlıklı yanıt verdi." else errorMessage(data, "Sunucuya bağlanılamadı.")))
            }
        }, halfLp(left = 5))
        body.addView(buttons, lp(mBottom = 14))

        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "-"
        body.addView(infoCard("Uygulama sürümü", "YükLab $version"), lp(mBottom = 16))
        sectionTitle(body, "Uygulama ayarları")
        actionCard(body, "Tema", "Açık tema aktif. Koyu tema renk katmanı tamamlanmadan sahte seçim sunulmuyor.", "☼") { toast("Şu anda açık tema kullanılabilir.") }
        actionCard(body, "Dil", "Türkçe. Web/i18n kaynakları var; mobil İngilizce metinleri henüz tamamlanmadı.", "TR") { toast("Mobil arayüz şu anda Türkçe.") }
        actionCard(body, "Bildirimler", "Hesap bildirimlerini görüntüle", "→") { if (token == null) showLogin() else showNotifications() }
        actionCard(body, "Gizlilik", "Veri ve güvenlik ilkeleri", "→") { privacyDialog() }
        actionCard(body, "Hakkında", "Uygulama bilgileri ve marka", "→") { aboutDialog() }
        actionCard(body, "Yardım ve destek", "Destek kanalları", "→") { supportDialog() }
    }

    private fun validServer(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    // ---------- UI helpers ----------

    private fun base(title: String, activeTab: Int? = null, back: (() -> Unit)? = null): LinearLayout {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(YukLabTheme.BACKGROUND) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10)); setBackgroundColor(Color.WHITE) }
        if (back != null) {
            val backView = YukLabTheme.text(this, "‹", 38f, YukLabTheme.ANTHRACITE, true).apply { gravity = Gravity.CENTER; isClickable = true; contentDescription = "Geri"; setOnClickListener { back() } }
            top.addView(backView, LinearLayout.LayoutParams(dp(48), dp(56)))
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(YukLabTheme.text(this, title, 22f, YukLabTheme.ANTHRACITE, true))
        if (title == "YükLab") brand.addView(YukLabTheme.text(this, "YÜK VE TAŞIMA PLATFORMU", 10f, YukLabTheme.MUTED))
        top.addView(brand, LinearLayout.LayoutParams(0, dp(58), 1f))
        val settings = YukLabTheme.text(this, "⚙", 23f, YukLabTheme.TURQUOISE_DARK, true).apply { gravity = Gravity.CENTER; isClickable = true; contentDescription = "Ayarlar"; setOnClickListener { showSettings() } }
        top.addView(settings, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(top)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(20), dp(16), dp(24)) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (activeTab != null) root.addView(bottomNav(activeTab))
        setContentView(root)
        return body
    }

    private fun bottomNav(active: Int): View {
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(dp(5), dp(5), dp(5), dp(7)); setBackgroundColor(Color.WHITE) }
        val items = listOf(
            Triple("Panel", "⌂") { showPanel() },
            Triple("İlanlar", "▤") { showListings() },
            Triple("Oluştur", "+") { showCreateHub() },
            Triple("Portföy", "▥") { showPortfolio() },
            Triple("Profil", "○") { showProfile() }
        )
        items.forEachIndexed { index, item ->
            val selected = index == active
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(2), dp(5), dp(2), dp(5)); isClickable = true; isFocusable = true; contentDescription = item.first; setOnClickListener { item.third() }
                if (selected) background = YukLabTheme.shape(this@MainActivity, YukLabTheme.TURQUOISE_SOFT, 16f)
            }
            box.addView(YukLabTheme.text(this, item.second, if (index == 2) 28f else 19f, if (selected) YukLabTheme.TURQUOISE_DARK else YukLabTheme.MUTED, true))
            box.addView(YukLabTheme.text(this, item.first, 10f, if (selected) YukLabTheme.TURQUOISE_DARK else YukLabTheme.MUTED, selected))
            nav.addView(box, LinearLayout.LayoutParams(0, dp(62), 1f).apply { setMargins(dp(1), 0, dp(1), 0) })
        }
        return nav
    }

    private fun pageIntro(parent: LinearLayout, title: String, subtitle: String) {
        parent.addView(YukLabTheme.text(this, title, 26f, YukLabTheme.ANTHRACITE, true), lp(mBottom = 7))
        parent.addView(YukLabTheme.text(this, subtitle, 15f, YukLabTheme.MUTED), lp(mBottom = 20))
    }

    private fun sectionTitle(parent: LinearLayout, value: String) { parent.addView(YukLabTheme.text(this, value, 18f, YukLabTheme.ANTHRACITE, true), lp(mTop = 3, mBottom = 10)) }
    private fun sectionLabel(parent: LinearLayout, value: String) { parent.addView(YukLabTheme.text(this, value, 14f, YukLabTheme.TURQUOISE_DARK, true), lp(mTop = 8, mBottom = 9)) }
    private fun fieldLabel(parent: LinearLayout, value: String) { parent.addView(YukLabTheme.text(this, value, 12f, YukLabTheme.MUTED, true), lp(mBottom = 6)) }

    private fun labeledField(parent: LinearLayout, label: String, value: String = "", password: Boolean = false): EditText {
        fieldLabel(parent, label)
        val field = YukLabTheme.input(this, label, password).apply { setText(value) }
        parent.addView(field, lp(mBottom = 11))
        return field
    }

    private fun spinner(parent: LinearLayout, label: String, values: Array<String>, selected: String): Spinner {
        fieldLabel(parent, label)
        val view = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, values)
            background = YukLabTheme.shape(this@MainActivity, Color.WHITE, 14f, YukLabTheme.BORDER)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            minimumHeight = dp(48)
            setSelection(values.indexOf(selected).coerceAtLeast(0))
        }
        parent.addView(view, lp(mBottom = 11))
        return view
    }

    private fun horizontalRow() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    private fun quickCard(title: String, subtitle: String, icon: String, onClick: () -> Unit): View {
        val card = YukLabTheme.card(this).apply { isClickable = true; isFocusable = true; setOnClickListener { onClick() }; minimumHeight = dp(154) }
        card.addView(YukLabTheme.text(this, icon, 24f, YukLabTheme.TURQUOISE_DARK, true))
        card.addView(space(10))
        card.addView(YukLabTheme.text(this, title, 17f, YukLabTheme.TEXT, true))
        card.addView(space(4))
        card.addView(YukLabTheme.text(this, subtitle, 12f, YukLabTheme.MUTED))
        return card
    }

    private fun actionCard(parent: LinearLayout, title: String, subtitle: String, icon: String, onClick: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(16), dp(14), dp(16)); background = YukLabTheme.shape(this@MainActivity, Color.WHITE, 18f, YukLabTheme.BORDER); elevation = dp(1).toFloat(); isClickable = true; isFocusable = true; setOnClickListener { onClick() }
        }
        val badge = YukLabTheme.text(this, icon, 22f, YukLabTheme.TURQUOISE_DARK, true).apply { gravity = Gravity.CENTER; background = YukLabTheme.shape(this@MainActivity, YukLabTheme.TURQUOISE_SOFT, 14f) }
        card.addView(badge, LinearLayout.LayoutParams(dp(52), dp(52)).apply { setMargins(0, 0, dp(13), 0) })
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(YukLabTheme.text(this@MainActivity, title, 17f, YukLabTheme.TEXT, true))
        copy.addView(space(4))
        copy.addView(YukLabTheme.text(this@MainActivity, subtitle, 13f, YukLabTheme.MUTED))
        card.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(YukLabTheme.text(this, "→", 20f, YukLabTheme.MUTED, true))
        parent.addView(card, lp(mBottom = 10))
    }

    private fun chip(label: String, selected: Boolean, onClick: () -> Unit): TextView = YukLabTheme.text(this, label, 12f, if (selected) YukLabTheme.ANTHRACITE else YukLabTheme.MUTED, selected).apply {
        gravity = Gravity.CENTER; minHeight = dp(44); background = YukLabTheme.shape(this@MainActivity, if (selected) YukLabTheme.TURQUOISE else Color.WHITE, 14f, if (selected) YukLabTheme.TURQUOISE else YukLabTheme.BORDER); isClickable = true; setOnClickListener { onClick() }
    }

    private fun statCard(value: String, label: String): View = YukLabTheme.card(this).apply {
        addView(YukLabTheme.text(this@MainActivity, value, 27f, YukLabTheme.TURQUOISE_DARK, true))
        addView(YukLabTheme.text(this@MainActivity, label, 13f, YukLabTheme.MUTED))
    }

    private fun infoCard(title: String, subtitle: String): LinearLayout = YukLabTheme.card(this).apply {
        addView(YukLabTheme.text(this@MainActivity, title, 16f, YukLabTheme.TEXT, true))
        addView(space(5))
        addView(YukLabTheme.text(this@MainActivity, subtitle, 13f, YukLabTheme.MUTED))
    }

    private fun loadingCard(message: String): View = infoCard("Yükleniyor", message)

    private fun emptyState(parent: LinearLayout, title: String, subtitle: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        val card = YukLabTheme.card(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        card.addView(YukLabTheme.text(this, "□", 34f, YukLabTheme.TURQUOISE_DARK, true))
        card.addView(space(8)); card.addView(YukLabTheme.text(this, title, 17f, YukLabTheme.TEXT, true)); card.addView(space(5)); card.addView(YukLabTheme.text(this, subtitle, 13f, YukLabTheme.MUTED))
        if (actionLabel != null && action != null) { card.addView(space(12)); card.addView(YukLabTheme.button(this, actionLabel, false, action)) }
        parent.addView(card, lp(mBottom = 10))
    }

    private fun errorState(parent: LinearLayout, message: String, retry: () -> Unit) {
        val card = YukLabTheme.statusCard(this, false, "Bir hata oluştu", message)
        card.addView(space(10)); card.addView(YukLabTheme.button(this, "Tekrar dene", false, retry)); parent.addView(card)
    }

    private fun aboutDialog() = AlertDialog.Builder(this).setTitle("YükLab").setMessage("Global Smart Logistics Network\n\nTürkiye öncelikli, global ölçeklenebilir yük ve taşıma platformu.").setPositiveButton("Kapat", null).show()
    private fun privacyDialog() = AlertDialog.Builder(this).setTitle("Gizlilik").setMessage("Oturum anahtarları cihazda özel uygulama depolamasında tutulur. Hassas değerler arayüzde veya loglarda gösterilmez. Ayrıntılı hukuki metin henüz projeye eklenmedi.").setPositiveButton("Kapat", null).show()
    private fun supportDialog() = AlertDialog.Builder(this).setTitle("Yardım ve destek").setMessage("Uygulama içi destek bileti backend'i henüz bulunmuyor. Bu ekran iletişim kanalı varmış gibi sahte veri göstermiyor.").setPositiveButton("Kapat", null).show()

    // ---------- Network ----------

    private data class HttpResult(val code: Int, val body: JSONObject)

    private fun request(path: String, method: String, body: JSONObject?, bearer: String?, allowRefresh: Boolean = true, done: (Boolean, JSONObject) -> Unit) {
        if (server.isBlank()) return done(false, JSONObject().put("error", "API sunucu adresi ayarlanmadı"))
        executor.execute {
            var result = performRequest(path, method, body, bearer)
            if (result.code == 401 && allowRefresh && !bearer.isNullOrBlank() && path != "/v1/auth/refresh") {
                val refresh = refreshToken
                if (!refresh.isNullOrBlank()) {
                    val refreshed = performRequest("/v1/auth/refresh", "POST", JSONObject().put("refreshToken", refresh), null)
                    if (refreshed.code in 200..299) {
                        val access = refreshed.body.optString("accessToken")
                        val nextRefresh = refreshed.body.optString("refreshToken")
                        if (access.isNotBlank() && nextRefresh.isNotBlank()) {
                            token = access; refreshToken = nextRefresh; session.updateTokens(access, nextRefresh)
                            result = performRequest(path, method, body, access)
                        }
                    } else {
                        token = null; refreshToken = null; session.clearSession()
                    }
                }
            }
            runOnUiThread { done(result.code in 200..299, result.body) }
        }
    }

    private fun performRequest(path: String, method: String, body: JSONObject?, bearer: String?): HttpResult {
        return try {
            val connection = URL(server + path).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("Accept", "application/json")
            if (!bearer.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null && method !in listOf("GET", "HEAD")) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrElse { JSONObject().put("error", text) }
            connection.disconnect()
            HttpResult(code, json)
        } catch (error: Exception) {
            HttpResult(0, JSONObject().put("error", error.message ?: "Bağlantı hatası"))
        }
    }

    // ---------- Misc ----------

    private fun location(done: (Double, Double) -> Unit) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 42)
            return toast("Konum izni verin.")
        }
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val provider = manager.getProviders(true).firstOrNull() ?: return toast("Konum servisi kapalı.")
        val found: Location? = try { manager.getLastKnownLocation(provider) } catch (_: Exception) { null }
        if (found != null) done(found.latitude, found.longitude) else toast("Konum henüz hazır değil.")
    }

    private fun validDate(value: String): Boolean = if (value.length != 10) false else runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value) != null }.getOrDefault(false)
    private fun shortDate(value: String): String = value.take(10).takeIf { it.length == 10 } ?: value
    private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
    private fun money(minor: String, currency: String): String? = minor.toLongOrNull()?.let { String.format(Locale("tr", "TR"), "%,.2f %s", it / 100.0, if (currency == "TRY") "₺" else currency) }
    private fun statusLabel(raw: String) = when (raw) { "DRAFT" -> "Taslak"; "PUBLISHED" -> "Aktif"; "OFFERING" -> "Teklif bekliyor"; "ACCEPTED" -> "Kabul edildi"; "DRIVER_ASSIGNED" -> "Taşıyıcı atandı"; "EN_ROUTE_PICKUP" -> "Alıma gidiyor"; "ARRIVED_PICKUP" -> "Alım noktasında"; "LOADED" -> "Yüklendi"; "IN_TRANSIT" -> "Yolda"; "ARRIVED_DELIVERY" -> "Teslimat noktasında"; "DELIVERED" -> "Teslim edildi"; "COMPLETED" -> "Tamamlandı"; "CANCELLED" -> "İptal"; "PENDING" -> "Bekliyor"; "REJECTED" -> "Reddedildi"; "WITHDRAWN" -> "Geri çekildi"; else -> raw }
    private fun serviceLabel(raw: String) = when (raw) { "LOAD", "Yük Taşımacılığı" -> "Yük taşıma"; "COURIER", "Kurye" -> "Kurye"; "EMERGENCY", "Acil Yardım" -> "Acil yardım"; else -> raw }
    private fun roleLabel(raw: String) = when (raw) { "CUSTOMER" -> "Müşteri"; "DRIVER" -> "Taşıyıcı / Sürücü"; "SERVICE_PROVIDER" -> "Hizmet sağlayıcı"; "BUSINESS" -> "İşletme"; "ADMIN" -> "Yönetici"; "SUPER_ADMIN" -> "Süper yönetici"; else -> raw }
    private fun errorMessage(data: JSONObject, fallback: String): String = data.optString("message").ifBlank { data.optString("error").ifBlank { fallback } }
    private fun lp(w: Int = -1, h: Int = -2, mTop: Int = 0, mBottom: Int = 0) = LinearLayout.LayoutParams(w, h).apply { setMargins(0, dp(mTop), 0, dp(mBottom)) }
    private fun halfLp(left: Int = 0, right: Int = 0) = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(left), 0, dp(right), 0) }
    private fun space(height: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(value: String) = runOnUiThread { Toast.makeText(this, value, Toast.LENGTH_LONG).show() }
}
