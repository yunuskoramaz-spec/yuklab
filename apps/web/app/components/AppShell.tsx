"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

const items = [
  { href: "/", label: "Panel", icon: "⌂" },
  { href: "/orders", label: "İlanlar", icon: "▤" },
  { href: "/create", label: "Oluştur", icon: "+" },
  { href: "/portfolio", label: "Portföy", icon: "▥" },
  { href: "/profile", label: "Profil", icon: "○" },
];

function activeFor(pathname: string, href: string) {
  if (href === "/") return pathname === "/";
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function AppShell({ children, title, kicker }: { children: ReactNode; title?: string; kicker?: string }) {
  const pathname = usePathname();
  return (
    <div className="yl-shell">
      <aside className="yl-sidebar" aria-label="Ana navigasyon">
        <Link href="/" className="yl-brand" aria-label="YükLab ana sayfa">
          <span className="yl-brand-mark" aria-hidden="true">Y</span>
          <span><strong>Yük<span>Lab</span></strong><small>GLOBAL SMART LOGISTICS NETWORK</small></span>
        </Link>
        <nav className="yl-side-nav">
          {items.map((item) => (
            <Link key={item.href} href={item.href} className={activeFor(pathname, item.href) ? "active" : ""}>
              <span aria-hidden="true">{item.icon}</span>{item.label}
            </Link>
          ))}
        </nav>
        <div className="yl-side-note">
          <strong>YükLab</strong>
          <span>Türkiye öncelikli, global ölçeklenebilir lojistik ağı.</span>
        </div>
      </aside>

      <div className="yl-main">
        <header className="yl-topbar">
          <div>
            {kicker && <span className="yl-kicker">{kicker}</span>}
            {title && <h1>{title}</h1>}
          </div>
          <div className="yl-top-actions">
            <Link href="/notifications" aria-label="Bildirimler">♢</Link>
            <Link href="/profile" aria-label="Profil">○</Link>
          </div>
        </header>
        <main className="yl-content">{children}</main>
      </div>

      <nav className="yl-mobile-nav" aria-label="Mobil navigasyon">
        {items.map((item) => (
          <Link key={item.href} href={item.href} className={activeFor(pathname, item.href) ? "active" : ""}>
            <span aria-hidden="true">{item.icon}</span><small>{item.label}</small>
          </Link>
        ))}
      </nav>
    </div>
  );
}
