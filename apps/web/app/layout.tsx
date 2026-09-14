import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";
import AppNavigation from "./AppNavigation";

export const metadata: Metadata = {
  title: "YükLab",
  description: "Global Smart Logistics Network",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="tr">
      <body><AppNavigation /><div id="app-content" className="app-content" tabIndex={-1}>{children}</div></body>
    </html>
  );
}
