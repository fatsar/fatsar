import type { Metadata, Viewport } from "next";
import "./globals.css";
import { Sidebar } from "@/components/layout/sidebar";
import { TopBar } from "@/components/layout/topbar";
import { prisma } from "@/lib/prisma";

// Every screen renders live procurement data — never prerender at build time.
export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: {
    default: "Siba Procurement Command Center",
    template: "%s · Siba Procurement",
  },
  description:
    "Internal procurement intelligence for Siba Kimya: purchasing decisions, supplier intelligence, price history, stock coverage and market risk.",
  applicationName: "Siba Procurement Command Center",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#141d2e",
};

async function SampleDataBanner() {
  const sampleCount = await prisma.material.count({ where: { isSample: true } });
  if (sampleCount === 0) return null;
  return (
    <div className="border-b border-warn/40 bg-warn-soft px-4 py-1.5 text-center text-[12.5px] font-medium text-warn-ink">
      This database contains SAMPLE development data — figures are not real
      purchasing data.
    </div>
  );
}

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>
        <Sidebar />
        <div className="lg:pl-60">
          <TopBar />
          <SampleDataBanner />
          <main className="mx-auto max-w-[1440px] px-4 py-5 lg:px-6">
            {children}
          </main>
        </div>
      </body>
    </html>
  );
}
