"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useState } from "react";
import { AppShell } from "./components/AppShell";
import { browserAccessToken, getProfile, listOrders, type ProfileUser } from "./lib/api";

export default function HomePage() {
  const [token, setToken] = useState<string | null>(null);
  const [profile, setProfile] = useState<ProfileUser | null>(null);
  const [stats, setStats] = useState<{ active: number; completed: number } | null>(null);

  useEffect(() => {
    const access = browserAccessToken();
    setToken(access);
    if (!access) return;
    Promise.all([getProfile(access), listOrders(access)])
      .then(([profileResult, orderResult]) => {
        setProfile(profileResult.user);
        const active = orderResult.orders.filter((order) => !["COMPLETED", "DELIVERED", "CANCELLED", "EXPIRED", "FAILED"].includes(order.status)).length;
        const completed = orderResult.orders.filter((order) => ["COMPLETED", "DELIVERED"].includes(order.status)).length;
        setStats({ active, completed });
      })
      .catch(() => undefined);
  }, []);

  return (
    <AppShell title="Panel" kicker="YÜKLAB">
      <section style={{ marginBottom: 20 }}>
        <h2 style={{ margin: 0, fontSize: 26 }}>Hoş geldin{profile?.firstName ? `, ${profile.firstName}` : ""}</h2>
        <p className="yl-muted" style={{ margin: "6px 0 0" }}>Yük ve taşıma işlerini tek yerden yönet.</p>
      </section>

      <section className="yl-hero">
        <div className="yl-hero-copy">
          <span className="yl-kicker">HER YÜKE BİR YOL</span>
          <h2>Yükünü kolayca yola çıkar.</h2>
          <p>İlanını oluştur, taşıyıcılarla eşleş, tekliflerini tek yerden yönet.</p>
          <Link className="yl-btn" href="/create">Yeni ilan oluştur <span aria-hidden="true">＋</span></Link>
        </div>
        <div className="yl-hero-art" aria-hidden="true"><div className="yl-road"><span className="yl-pin" /></div></div>
      </section>

      {!token && (
        <section className="yl-card yl-auth-banner" style={{ marginTop: 16 }}>
          <Image className="yl-auth-logo" src="/brand/logo-full.webp" alt="YükLab Global Smart Logistics Network" width={126} height={126} />
          <div><h3>Giriş yap veya hesap oluştur</h3><p>İlanlarını ve tekliflerini hesabında sakla.</p></div>
          <Link className="yl-btn-secondary" href="/profile">Giriş / Kayıt →</Link>
        </section>
      )}

      {stats && (
        <section className="yl-grid-2" style={{ marginTop: 16 }}>
          <div className="yl-card yl-stat"><strong>{stats.active}</strong><span>Aktif ilan</span></div>
          <div className="yl-card yl-stat"><strong>{stats.completed}</strong><span>Tamamlanan taşıma</span></div>
        </section>
      )}

      <div className="yl-section-head"><div><h2>Hızlı işlemler</h2><p>En sık kullanılan YükLab alanları.</p></div></div>
      <section className="yl-grid-4">
        <Link className="yl-card yl-quick" href="/orders"><span className="yl-quick-icon">▤</span><strong>İlanlarım</strong><span>Yayınladıkların ve güncel ilanların.</span></Link>
        <Link className="yl-card yl-quick" href="/provider"><span className="yl-quick-icon">▣</span><strong>Boş araçlar</strong><span>Müsait taşıyıcı işleri ve araçlar.</span></Link>
        <Link className="yl-card yl-quick" href="/provider"><span className="yl-quick-icon">◇</span><strong>Tekliflerim</strong><span>Gönderilen teklifler ve fırsatlar.</span></Link>
        <Link className="yl-card yl-quick" href="/portfolio"><span className="yl-quick-icon">▥</span><strong>Portföyüm</strong><span>Taşıma geçmişi ve gerçek istatistikler.</span></Link>
      </section>
    </AppShell>
  );
}
