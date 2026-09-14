"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { AppShell } from "../components/AppShell";
import { browserAccessToken, cancelOrder, listOrders, type Order } from "../lib/api";

type Filter = "active" | "waiting" | "done";

function statusLabel(status: string) {
  const labels: Record<string, string> = { DRAFT: "Taslak", PUBLISHED: "Aktif", OFFERING: "Teklif bekliyor", ACCEPTED: "Kabul edildi", DRIVER_ASSIGNED: "Taşıyıcı atandı", EN_ROUTE_PICKUP: "Alıma gidiyor", LOADED: "Yüklendi", IN_TRANSIT: "Yolda", DELIVERED: "Teslim edildi", COMPLETED: "Tamamlandı", CANCELLED: "İptal" };
  return labels[status] ?? status;
}
function isActive(status: string) { return !["COMPLETED", "DELIVERED", "CANCELLED", "EXPIRED", "FAILED"].includes(status); }
function money(value?: string | null, currency = "TRY") { const minor = value ? Number(value) : NaN; if (!Number.isFinite(minor)) return null; return new Intl.NumberFormat("tr-TR", { style: "currency", currency }).format(minor / 100); }

export default function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<Filter>("active");
  const [token, setToken] = useState<string | null>(null);

  async function load() {
    const access = browserAccessToken();
    setToken(access);
    if (!access) { setLoading(false); return; }
    setLoading(true); setError("");
    try { setOrders((await listOrders(access)).orders); }
    catch (e) { setError(e instanceof Error ? e.message : "İlanlar yüklenemedi."); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);

  const visible = useMemo(() => orders.filter((order) => {
    const byTab = filter === "waiting" ? order.status === "OFFERING" : filter === "done" ? ["COMPLETED", "DELIVERED"].includes(order.status) : isActive(order.status);
    const text = `${order.pickupAddress} ${order.deliveryAddress ?? ""} ${order.payload?.loadType ?? ""}`.toLocaleLowerCase("tr-TR");
    return byTab && text.includes(query.trim().toLocaleLowerCase("tr-TR"));
  }), [orders, query, filter]);

  async function remove(id: string) {
    if (!token || !window.confirm("İlan yayından kaldırılıp iptal durumuna alınsın mı?")) return;
    try { await cancelOrder(token, id); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : "İlan kaldırılamadı."); }
  }

  return (
    <AppShell title="İlanlar" kicker="LOJİSTİK MARKETPLACE">
      {!token && !loading ? (
        <section className="yl-card yl-empty"><strong>İlanlarını görmek için giriş yap</strong><p>Profil ekranından hesabına giriş yapabilir veya yeni hesap oluşturabilirsin.</p><Link className="yl-btn" href="/profile" style={{ marginTop: 14 }}>Giriş / Kayıt</Link></section>
      ) : <>
        <section className="yl-card" style={{ marginBottom: 14 }}>
          <div className="yl-form-row">
            <label style={{ display: "grid", gap: 7, fontSize: 12, color: "var(--yl-muted)", fontWeight: 800 }}>Ara<input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Çıkış, varış veya yük türü" /></label>
            <div className="yl-tabs" aria-label="İlan filtreleri">
              <button className={filter === "active" ? "active" : ""} onClick={() => setFilter("active")}>Aktif</button>
              <button className={filter === "waiting" ? "active" : ""} onClick={() => setFilter("waiting")}>Teklif bekleyen</button>
              <button className={filter === "done" ? "active" : ""} onClick={() => setFilter("done")}>Tamamlanan</button>
            </div>
          </div>
        </section>
        {error && <p className="yl-error">{error} <button className="yl-btn-secondary" onClick={() => void load()} style={{ marginLeft: 8 }}>Tekrar dene</button></p>}
        {loading ? <div className="yl-card yl-loading">İlanlar yükleniyor…</div> : visible.length === 0 ? (
          <section className="yl-card yl-empty"><strong>Henüz ilan yok</strong><p>Bu filtreye uyan gerçek bir ilan bulunamadı.</p><Link className="yl-btn" href="/create" style={{ marginTop: 14 }}>Yeni ilan oluştur</Link></section>
        ) : (
          <section className="yl-order-list">
            {visible.map((order) => (
              <article className="yl-card yl-order-card" key={order.id}>
                <div className="yl-order-top"><span className="yl-badge">{order.payload?.vehicleType ?? "Yük"}</span><span className="yl-badge neutral">{statusLabel(order.status)}</span></div>
                <div className="yl-route"><div><strong>{order.pickupAddress}</strong><small>Çıkış</small></div><span className="yl-route-arrow">→</span><div><strong>{order.deliveryAddress || "Belirtilmedi"}</strong><small>Varış</small></div></div>
                <div className="yl-order-meta">
                  <div><span>Yük tipi</span><strong>{order.payload?.loadType || "-"}</strong></div>
                  <div><span>Ağırlık</span><strong>{order.payload?.weightKg ? `${order.payload.weightKg} kg` : "-"}</strong></div>
                  <div><span>Alım tarihi</span><strong>{order.scheduledAt ? order.scheduledAt.slice(0, 10) : "-"}</strong></div>
                  <div><span>Fiyat</span><strong className="yl-price">{money(order.budgetMinor, order.currency) ?? "Teklif"}</strong></div>
                </div>
                <div className="yl-form-row"><Link className="yl-btn" href={`/orders/${order.id}`}>Detayları gör →</Link>{["DRAFT", "PUBLISHED", "OFFERING"].includes(order.status) && <button className="yl-btn-danger" onClick={() => void remove(order.id)}>Yayından kaldır</button>}</div>
              </article>
            ))}
          </section>
        )}
      </>}
    </AppShell>
  );
}
