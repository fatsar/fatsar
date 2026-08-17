import type { Metadata } from "next";
import { PageHeader } from "@/components/ui/page-header";
import { prisma } from "@/lib/prisma";
import { IntelClient, type IntelRow } from "./intel-client";
import type { IntelCategory, PriceDirection, RiskLevel } from "@/domain/enums";

export const metadata: Metadata = { title: "Market Intelligence" };

export default async function MarketIntelligencePage() {
  const entries = await prisma.marketIntelligence.findMany({
    orderBy: { date: "desc" },
  });

  const rows: IntelRow[] = entries.map((e) => ({
    id: e.id,
    date: e.date.toISOString(),
    title: e.title,
    category: e.category as IntelCategory,
    affectedMaterials: e.affectedMaterials,
    geography: e.geography,
    summary: e.summary,
    expectedImpact: e.expectedImpact,
    priceDirection: e.priceDirection as PriceDirection,
    supplyImpact: e.supplyImpact,
    riskLevel: e.riskLevel as RiskLevel,
    source: e.source,
    url: e.url,
    notes: e.notes,
    isActive: e.isActive,
    isSample: e.isSample,
  }));

  return (
    <div>
      <PageHeader
        title="Market Intelligence"
        description="Manually curated market, macro, freight and geopolitical signals. Active HIGH/CRITICAL entries raise the risk of matching materials. No automatic scraping — entries are recorded by the team."
      />
      <IntelClient rows={rows} />
    </div>
  );
}
