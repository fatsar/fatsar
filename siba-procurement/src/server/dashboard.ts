import { prisma } from "@/lib/prisma";
import {
  getMaterialInsights,
  isQuotationExpired,
  quotationValue,
  sortByPriority,
  type MaterialInsight,
} from "@/server/material-insights";

/**
 * Dashboard KPI aggregation. Each KPI documents its own definition — the
 * numbers must always be traceable.
 */

export interface DashboardKpis {
  /** Recommendation is one of the action-demanding states. */
  materialsRequiringAction: number;
  /** Risk level HIGH or CRITICAL. */
  highRiskMaterials: number;
  /** Open RFQ-type actions (OPEN / IN_PROGRESS / WAITING_SUPPLIER). */
  rfqsPending: number;
  /** Σ quantity × normalized price of valid quotations, by currency. */
  purchaseValueUnderReview: { currency: string; value: number }[];
  /** Actions in WAITING_SUPPLIER status. */
  supplierResponsesPending: number;
  /** Mean basic stock coverage across active materials with demand data. */
  avgStockCoverageDays: number | null;
  /** Materials whose latest quotation is above the previous one. */
  priceIncreaseAlerts: number;
  /** Actions past due date and not completed/cancelled. */
  overdueActions: number;
}

export interface DashboardData {
  kpis: DashboardKpis;
  /** All active-material insights, priority-sorted. */
  insights: MaterialInsight[];
  hasSampleData: boolean;
}

const ACTIONABLE = new Set([
  "URGENT_PURCHASE",
  "BUY_NOW",
  "SECURE_SUPPLY",
  "REQUEST_QUOTATION",
  "NEGOTIATE",
]);

export async function getDashboardData(): Promise<DashboardData> {
  const now = new Date();
  const [insights, actions, validQuotations, sampleCount] = await Promise.all([
    getMaterialInsights(),
    prisma.actionItem.findMany({
      where: { status: { notIn: ["COMPLETED", "CANCELLED"] } },
      select: { id: true, actionType: true, status: true, dueDate: true },
    }),
    prisma.quotation.findMany({
      where: { status: "ACTIVE", validUntil: { gte: now } },
      include: { material: { select: { unit: true } } },
    }),
    prisma.material.count({ where: { isSample: true } }),
  ]);

  const valueByCurrency = new Map<string, number>();
  for (const q of validQuotations) {
    if (isQuotationExpired(q, now)) continue;
    const value = quotationValue({
      quantity: q.quantity,
      quantityUnit: q.quantityUnit,
      normalizedPrice: q.normalizedPrice,
      materialUnit: q.material.unit,
    });
    if (value == null) continue;
    valueByCurrency.set(
      q.currency,
      (valueByCurrency.get(q.currency) ?? 0) + value
    );
  }

  const coverages = insights
    .map((i) => i.coverage.coverageDays)
    .filter((v): v is number => v != null);

  const kpis: DashboardKpis = {
    materialsRequiringAction: insights.filter((i) =>
      ACTIONABLE.has(i.recommendation.recommendation)
    ).length,
    highRiskMaterials: insights.filter(
      (i) => i.risk.level === "HIGH" || i.risk.level === "CRITICAL"
    ).length,
    rfqsPending: actions.filter((a) => a.actionType === "RFQ").length,
    purchaseValueUnderReview: [...valueByCurrency.entries()]
      .map(([currency, value]) => ({ currency, value: Math.round(value) }))
      .sort((a, b) => b.value - a.value),
    supplierResponsesPending: actions.filter(
      (a) => a.status === "WAITING_SUPPLIER"
    ).length,
    avgStockCoverageDays: coverages.length
      ? Math.round(
          (coverages.reduce((s, v) => s + v, 0) / coverages.length) * 10
        ) / 10
      : null,
    priceIncreaseAlerts: insights.filter(
      (i) => i.priceStats.changePct != null && i.priceStats.changePct > 0
    ).length,
    overdueActions: actions.filter(
      (a) => a.dueDate != null && a.dueDate.getTime() < now.getTime()
    ).length,
  };

  return {
    kpis,
    insights: [...insights].sort(sortByPriority),
    hasSampleData: sampleCount > 0,
  };
}
