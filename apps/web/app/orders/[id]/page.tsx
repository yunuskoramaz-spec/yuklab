"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { AppShell } from "../../components/AppShell";
import { acceptOffer, browserAccessToken, cancelOrder, getOrder, listOrderOffers, updateOrder, type Offer, type Order } from "../../lib/api";

function money(value?: string | null, currency = "TRY") { const minor = value ? Number(value) : NaN; return Number.isFinite(minor) ? new Intl.NumberFormat("tr-TR", { style: "currency", currency }).format(minor / 100) : "-"; }

export default function OrderDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const id = params.id;
  const [token, setToken] = useState<string | null>(null);
  const [order, setOrder] = useState<Order | null>(null);
  const [offers, setOffers] = useState<Offer[]>([]);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState(false);
  const [pickup, setPickup] = useState(""); const [delivery, setDelivery] = useState(""); const [budget, setBudget] = useState("");

  const load = useCallback(async (access?: string | null) => {
    const next = access ?? browserAccessToken(); setToken(next);
    if (!next) return;
    setError("");
    try {
      const [orderResult, offerResult] = await Promise.all([getOrder(next, id), listOrderOffers(next, id)]);
      setOrder(orderResult.order); setOffers(offerResult.offers);
      setPickup(orderResult.order.pickupAddress); setDelivery(orderResult.order.deliveryAddress ?? ""); setBudget(orderResult.order.budgetMinor ? String(Number(orderResult.order.budgetMinor) / 100) : "");
    } catch (e) { setError(e instanceof Error ? e.message : "İlan yüklenemedi."); }
  }, [id]);
  useEffect(() => { void load(); }, [load]);

  async function save(e: FormEvent) {
    e.preventDefault(); if (!token || !order) return;
    try { const result = await updateOrder(token, order.id, { pickupAddress: pickup, deliveryAddress: delivery, budgetMinor: budget ? Math.round(Number(budget) * 100) : undefined }); setOrder(result.order); setEditing(false); }
    catch (e) { setError(e instanceof Error ? e.message : "İlan güncellenemedi."); }
  }
  async function remove() { if (!token || !order || !confirm("İlan yayından kaldırılsın mı?")) return; try { await cancelOrder(token, order.id); router.push("/orders"); } catch (e) { setError(e instanceof Error ? e.message : "İlan kaldırılamadı."); } }
  async function accept(offerId: string) { if (!token || !order) return; try { await acceptOffer(token, order.id, offerId); await load(token); } catch (e) { setError(e instanceof Error ? e.message : "Teklif kabul edilemedi."); } }

  return <AppShell title="İlan detayı" kicker="İLAN YÖNETİMİ">
    <Link className="yl-btn-secondary" href="/orders" style={{ marginBottom: 14 }}>← İlanlara dön</Link>
    {error && <p className="yl-error">{error}</p>}
    {!token ? <div className="yl-card yl-empty"><strong>Giriş gerekli</strong><p>İlan detayları hesabına özeldir.</p><Link href="/profile" className="yl-btn" style={{marginTop:14}}>Giriş yap</Link></div> : !order ? <div className="yl-card yl-loading">İlan yükleniyor…</div> : <div className="yl-stack">
      <section className="yl-card">
        <div className="yl-order-top"><span className="yl-badge">{order.payload?.vehicleType ?? "Yük"}</span><span className="yl-badge neutral">{order.status}</span></div>
        <div className="yl-route" style={{marginTop:16}}><div><strong>{order.pickupAddress}</strong><small>Çıkış</small></div><span className="yl-route-arrow">→</span><div><strong>{order.deliveryAddress ?? "-"}</strong><small>Varış</small></div></div>
        <div className="yl-order-meta" style={{marginTop:18}}><div><span>Yük türü</span><strong>{order.payload?.loadType ?? "-"}</strong></div><div><span>Ağırlık</span><strong>{order.payload?.weightKg ? `${order.payload.weightKg} kg` : "-"}</strong></div><div><span>Alım</span><strong>{order.scheduledAt?.slice(0,10) ?? "-"}</strong></div><div><span>Fiyat</span><strong className="yl-price">{money(order.budgetMinor, order.currency)}</strong></div></div>
        {order.payload?.notes && <p className="yl-muted">{order.payload.notes}</p>}
        {["DRAFT","PUBLISHED","OFFERING"].includes(order.status) && <div className="yl-form-row" style={{marginTop:16}}><button className="yl-btn-secondary" onClick={()=>setEditing(!editing)}>İlanı düzenle</button><button className="yl-btn-danger" onClick={()=>void remove()}>Yayından kaldır</button></div>}
      </section>
      {editing && <form className="yl-card yl-form" onSubmit={save}><h2 style={{margin:0}}>İlanı düzenle</h2><label>Çıkış<input value={pickup} onChange={e=>setPickup(e.target.value)} required/></label><label>Varış<input value={delivery} onChange={e=>setDelivery(e.target.value)} required/></label><label>Fiyat (₺)<input type="number" min="0" step="0.01" value={budget} onChange={e=>setBudget(e.target.value)}/></label><button className="yl-btn" type="submit">Kaydet</button></form>}
      <section><div className="yl-section-head"><div><h2>Gelen teklifler</h2><p>Gerçek taşıyıcı teklifleri.</p></div></div>{offers.length===0?<div className="yl-card yl-empty"><strong>Henüz teklif yok</strong><p>Teklif geldiğinde burada görünecek.</p></div>:<div className="yl-stack">{offers.map(offer=><article className="yl-card" key={offer.id}><div className="yl-order-top"><strong>{offer.provider ? `${offer.provider.firstName} ${offer.provider.lastName}` : "Taşıyıcı"}</strong><span className="yl-badge neutral">{offer.status}</span></div><p className="yl-price">{money(offer.amountMinor,offer.currency)}</p>{offer.note&&<p className="yl-muted">{offer.note}</p>}{offer.status==="PENDING"&&<button className="yl-btn" onClick={()=>void accept(offer.id)}>Teklifi kabul et</button>}</article>)}</div>}</section>
    </div>}
  </AppShell>;
}