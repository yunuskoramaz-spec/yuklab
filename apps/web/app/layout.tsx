import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";
import "./professional.css";
import "./brand.css";

export const metadata: Metadata = {
  title: { default: "YükLab", template: "%s · YükLab" },
  description: "Global Smart Logistics Network",
  applicationName: "YükLab",
  icons: {
    icon: "/brand/favicon.png",
    shortcut: "/brand/favicon.png",
    apple: "/brand/app-icon.jpg",
  },
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="tr">
      <body>{children}</body>
    </html>
  );
}
