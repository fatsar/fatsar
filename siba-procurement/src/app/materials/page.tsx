import Link from "next/link";
import { Plus } from "lucide-react";
import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { getMaterialInsights } from "@/server/material-insights";
import { MaterialsTable, type MaterialRow } from "./materials-table";
import type { MaterialCategory, Recommendation, RiskLevel } from "@/domain/enums";

export const metadata: Metadata = { title: "Materials" };

export default async function MaterialsPage() {
  const insights = await getMaterialInsights({ includeInactive: true });

  const rows: MaterialRow[] = insights.map((i) => ({
    id: i.material.id,
    code: i.material.code,
    name: i.material.name,
    category: i.material.category as MaterialCategory,
    unit: i.material.unit,
    usableStock: i.coverage.usableStock,
    coverageDays: i.coverage.coverageDays,
    projectedCoverageDays: i.coverage.projectedCoverageDays,
    risk: i.risk.level as RiskLevel,
    riskScore: i.risk.score,
    recommendation: i.recommendation.recommendation as Recommendation,
    latestPrice: i.priceStats.latest,
    targetPrice: i.material.targetPrice,
    currency: i.material.currency,
    preferredSupplier: i.material.preferredSupplier?.name ?? null,
    isActive: i.material.isActive,
    isSample: i.material.isSample,
  }));

  return (
    <div>
      <PageHeader
        title="Materials"
        description="Material master with live coverage, risk and recommendation per item."
        actions={
          <Link
            href="/materials/new"
            className="inline-flex h-9 items-center gap-1.5 rounded-md border border-accent bg-accent px-3.5 text-sm font-medium text-white hover:bg-accent-strong"
          >
            <Plus size={15} /> New material
          </Link>
        }
      />
      <Card>
        <MaterialsTable rows={rows} />
      </Card>
    </div>
  );
}
