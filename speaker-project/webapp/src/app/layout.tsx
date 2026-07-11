import type { Metadata, Viewport } from "next";
import "./globals.css";
import Nav from "@/components/Nav";
import SwRegister from "@/components/SwRegister";

export const metadata: Metadata = {
  title: "SpeakerBox Pro",
  manifest: "/manifest.webmanifest",
  appleWebApp: { capable: true, statusBarStyle: "black-translucent", title: "SpeakerBox" },
};
export const viewport: Viewport = {
  themeColor: "#0c0e12",
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="de">
      <body className="min-h-dvh flex flex-col">
        <main className="flex-1 max-w-md w-full mx-auto px-4 pb-24 pt-4">{children}</main>
        <Nav />
        <SwRegister />
      </body>
    </html>
  );
}
