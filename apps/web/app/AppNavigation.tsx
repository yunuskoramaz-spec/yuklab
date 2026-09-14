"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
const entries = [
  { href: "/", label: "Panel", path: "M3 10 12 3l9 7M5 9v12h5v-6h4v6h5V9" },
  { href: "/orders", label: "İlanlar", path: "M6 3h12v18H6zM9 7h6M9 12h6M9 17h4" },
  { href: "/provider", label: "Taşıyıcı", path: "M2 6h12v11H2zM14 10h4l4 4v3h-8M8 18a2 2 0 1 1-4 0 2 2 0 0 1 4 0M20 18a2 2 0 1 1-4 0 2 2 0 0 1 4 0" }
];
export default function AppNavigation() {
  const pathname = usePathname();
  return <><a className="skip-link" href="#app-content">İçeriğe geç</a>
    <nav className="app-navigation" aria-label="Ana navigasyon">
      <Link className="brand-wordmark" href="/" aria-label="YükLab ana sayfa">Yük<span>Lab</span><small>SMART LOGISTICS</small></Link>
      <div className="app-nav-items">{entries.map(item => {
        const active = item.href === "/" ? pathname === "/" : pathname.startsWith(item.href);
        return <Link key={item.href} href={item.href} aria-current={active ? "page" : undefined}>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={item.path}/></svg><span>{item.label}</span>
        </Link>;
      })}</div>
    </nav></>;
}
