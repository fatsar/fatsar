import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { getMaterialInsights, sortByPriority } from "@/server/material-insights";
import { PlanTable, type PlanRow } from "./plan-table";
import type { Recommendation, RiskLevel } from "@/domain/enums";

export const metadata: Metadata = { title: "Purchasing plan" };

export default async function PlanPage() {
  const insights = (await getMaterialInsights()).sort(sortByPriority);

  const rows: PlanRow[] = insights.map((i, index) => ({
    id: i.material.id,
    name: i.material.name,
    code: i.material.code,
    unit: i.material.unit,
    usableStock: i.coverage.usableStock,
    dailyDemand: i.coverage.dailyDemand,
    coverageDays: i.coverage.coverageDays,
    projectedCoverageDays: i.coverage.projectedCoverageDays,
    belowSafetyDay: i.coverage.projectedBelowSafetyDay,
    reorderPointDays: i.coverage.reorderPointDays,
    reorderPointQty: i.coverage.reorderPointQty,
    inventoryPosition: i.coverage.inventoryPosition,
    leadTimeDays: i.material.leadTimeDays,
    risk: i.risk.level as RiskLevel,
    riskScore: i.risk.score,
    recommendation: i.recommendation.recommendation as Recommendation,
    confidence: i.recommendation.confidence,
    topReason: i.recommendation.reasons.join(" "),
    priorityIndex: index,
  }));

  return (
    <div>
      <PageHeader
        title="Purchasing Plan"
        description="All active materials ranked by purchasing urgency. Reorder point = daily consumption × lead time + safety stock; position counts stock plus open orders. Open a material for the full reasoning."
      />
      <Card>
        <PlanTable rows={rows} />
      </Card>
    </div>
  );
}
