import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";
import "./professional.css";

export const metadata: Metadata = {
  title: { default: "YükLab", template: "%s · YükLab" },
  description: "Global Smart Logistics Network",
  applicationName: "YükLab",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="tr">
      <body>{children}</body>
    </html>
  );
}
