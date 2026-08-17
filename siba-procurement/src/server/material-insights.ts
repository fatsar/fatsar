import { prisma } from "@/lib/prisma";
import type { Prisma } from "@/generated/prisma/client";
import type { QuantityUnit } from "@/domain/enums";
import { calculateCoverage, type CoverageResult } from "@/domain/stock-coverage";
import { calculatePriceStats, type PriceStats } from "@/domain/price-stats";
import { assessRisk, type RiskAssessment } from "@/domain/risk";
import {
  recommendPurchase,
  RECOMMENDATION_PRIORITY,
  type RecommendationResult,
} from "@/domain/recommendation";
import {
  calculateSupplierScore,
  type SupplierScoreResult,
} from "@/domain/supplier-score";
import { intelMatchesMaterial } from "@/domain/intel-match";
import { convertQuantity, canConvert } from "@/domain/units";
import {
  getRecommendationConfig,
  getSupplierScoreWeights,
} from "@/server/settings";

/**
 * Central read-side assembly: loads materials with their relations and runs
 * every domain engine once per material. All pages (dashboard, materials,
 * purchasing plan) consume this single source so numbers never diverge.
 */

const materialInclude = {
  preferredSupplier: true,
  suppliers: { include: { supplier: true } },
} satisfies Prisma.MaterialInclude;

export type MaterialWithRelations = Prisma.MaterialGetPayload<{
  include: typeof materialInclude;
}>;

export interface QuotationValidity {
  isExpired: boolean;
  isStale: boolean;
}

export interface MaterialInsight {
  material: MaterialWithRelations;
  coverage: CoverageResult;
  risk: RiskAssessment;
  recommendation: RecommendationResult;
  priceStats: PriceStats;
  /** Newest quotation regardless of validity (with supplier name). */
  latestQuotation: {
    id: string;
    supplierName: string;
    normalizedPrice: number;
    currency: string;
    quotationDate: Date;
    validUntil: Date | null;
    isExpired: boolean;
  } | null;
  approvedSupplierCount: number;
  matchedIntel: { id: string; title: string; riskLevel: string }[];
  /** Quotations excluded from price stats because of a different currency. */
  excludedQuotationCount: number;
}

/** A quotation is valid when ACTIVE and not past its validity date. */
export function isQuotationExpired(
  q: { status: string; validUntil: Date | null },
  now: Date
): boolean {
  if (q.status !== "ACTIVE") return true;
  if (q.validUntil == null) return false;
  return q.validUntil.getTime() < now.getTime();
}

function daysBetween(from: Date, to: Date): number {
  return (to.getTime() - from.getTime()) / 86_400_000;
}

export async function getMaterialInsights(options?: {
  includeInactive?: boolean;
  materialId?: string;
}): Promise<MaterialInsight[]> {
  const now = new Date();
  const [config, supplierWeights] = await Promise.all([
    getRecommendationConfig(),
    getSupplierScoreWeights(),
  ]);

  const where: Prisma.MaterialWhereInput = {};
  if (!options?.includeInactive) where.isActive = true;
  if (options?.materialId) where.id = options.materialId;

  const [materials, quotations, intelEntries] = await Promise.all([
    prisma.material.findMany({
      where,
      include: materialInclude,
      orderBy: { name: "asc" },
    }),
    prisma.quotation.findMany({
      where: options?.materialId ? { materialId: options.materialId } : {},
      include: { supplier: { select: { name: true } } },
      orderBy: { quotationDate: "desc" },
    }),
    prisma.marketIntelligence.findMany({ where: { isActive: true } }),
  ]);

  const quotationsByMaterial = new Map<string, typeof quotations>();
  for (const q of quotations) {
    const list = quotationsByMaterial.get(q.materialId) ?? [];
    list.push(q);
    quotationsByMaterial.set(q.materialId, list);
  }

  const supplierScoreCache = new Map<string, SupplierScoreResult>();
  const scoreFor = (supplier: MaterialWithRelations["preferredSupplier"]) => {
    if (!supplier) return null;
    let cached = supplierScoreCache.get(supplier.id);
    if (!cached) {
      cached = calculateSupplierScore(supplier, supplierWeights);
      supplierScoreCache.set(supplier.id, cached);
    }
    return cached.score;
  };

  return materials.map((material) => {
    const mQuotes = quotationsByMaterial.get(material.id) ?? [];

    // Price stats use only quotations in the material's own currency —
    // cross-currency averaging without FX rates would be wrong.
    const sameCurrency = mQuotes.filter((q) => q.currency === material.currency);
    const excludedQuotationCount = mQuotes.length - sameCurrency.length;
    const priceStats = calculatePriceStats(
      sameCurrency.map((q) => ({
        date: q.quotationDate,
        price: q.normalizedPrice,
        supplierName: q.supplier.name,
      })),
      material.targetPrice,
      now
    );

    const newest = mQuotes[0] ?? null;
    const newestValid =
      sameCurrency.find((q) => !isQuotationExpired(q, now)) ?? null;

    const coverage = calculateCoverage({
      currentStock: material.currentStock,
      reservedStock: material.reservedStock,
      safetyStock: material.safetyStock,
      avgDailyConsumption: material.avgDailyConsumption,
      avgMonthlyConsumption: material.avgMonthlyConsumption,
      forecastMonthlyDemand: material.forecastMonthlyDemand,
      leadTimeDays: material.leadTimeDays,
      openPurchaseQty: material.openPurchaseQty,
      inboundQty: material.inboundQty,
      inboundEtaDays: material.inboundEta
        ? Math.ceil(daysBetween(now, material.inboundEta))
        : null,
    });

    const matchedIntel = intelEntries.filter((e) =>
      intelMatchesMaterial(e, material)
    );
    const approvedSupplierCount = material.suppliers.filter(
      (s) => s.isApproved
    ).length;

    const risk = assessRisk(
      {
        coverage,
        leadTimeDays: material.leadTimeDays,
        latestPrice: priceStats.latest,
        avg3m: priceStats.avg3m,
        targetPrice: material.targetPrice,
        avgMonthlyConsumption: material.avgMonthlyConsumption,
        forecastMonthlyDemand: material.forecastMonthlyDemand,
        approvedSupplierCount,
        preferredSupplierName: material.preferredSupplier?.name ?? null,
        preferredSupplierScore: scoreFor(material.preferredSupplier),
        intel: matchedIntel.map((e) => ({
          title: e.title,
          riskLevel: e.riskLevel as "LOW" | "MEDIUM" | "HIGH" | "CRITICAL",
        })),
      },
      config
    );

    const recommendation = recommendPurchase(
      {
        coverage,
        leadTimeDays: material.leadTimeDays,
        safetyStock: material.safetyStock,
        targetPrice: material.targetPrice,
        latestValidPrice: newestValid?.normalizedPrice ?? null,
        latestPrice: priceStats.latest,
        latestQuotationAgeDays: newest
          ? Math.floor(daysBetween(newest.quotationDate, now))
          : null,
        avg3m: priceStats.avg3m,
        approvedSupplierCount,
        highRiskIntelActive: matchedIntel.some(
          (e) => e.riskLevel === "HIGH" || e.riskLevel === "CRITICAL"
        ),
        openPurchaseQty: material.openPurchaseQty,
        inboundQty: material.inboundQty,
      },
      config
    );

    return {
      material,
      coverage,
      risk,
      recommendation,
      priceStats,
      latestQuotation: newest
        ? {
            id: newest.id,
            supplierName: newest.supplier.name,
            normalizedPrice: newest.normalizedPrice,
            currency: newest.currency,
            quotationDate: newest.quotationDate,
            validUntil: newest.validUntil,
            isExpired: isQuotationExpired(newest, now),
          }
        : null,
      approvedSupplierCount,
      matchedIntel: matchedIntel.map((e) => ({
        id: e.id,
        title: e.title,
        riskLevel: e.riskLevel,
      })),
      excludedQuotationCount,
    };
  });
}

export async function getMaterialInsight(
  materialId: string
): Promise<MaterialInsight | null> {
  const list = await getMaterialInsights({
    includeInactive: true,
    materialId,
  });
  return list[0] ?? null;
}

const RISK_ORDER = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 } as const;

/** Sort for "Priority Purchasing Actions": action urgency, then risk, then coverage. */
export function sortByPriority(a: MaterialInsight, b: MaterialInsight): number {
  const recDiff =
    RECOMMENDATION_PRIORITY[a.recommendation.recommendation] -
    RECOMMENDATION_PRIORITY[b.recommendation.recommendation];
  if (recDiff !== 0) return recDiff;
  const riskDiff =
    RISK_ORDER[a.risk.level as keyof typeof RISK_ORDER] -
    RISK_ORDER[b.risk.level as keyof typeof RISK_ORDER];
  if (riskDiff !== 0) return riskDiff;
  return (
    (a.coverage.coverageDays ?? Number.MAX_SAFE_INTEGER) -
    (b.coverage.coverageDays ?? Number.MAX_SAFE_INTEGER)
  );
}

/** Sum of quantity × normalized price for valid quotations, per currency. */
export function quotationValue(q: {
  quantity: number;
  quantityUnit: string;
  normalizedPrice: number;
  materialUnit: string;
}): number | null {
  if (!canConvert(q.quantityUnit as QuantityUnit, q.materialUnit as QuantityUnit)) {
    return null;
  }
  const qtyInMaterialUnit = convertQuantity(
    q.quantity,
    q.quantityUnit as QuantityUnit,
    q.materialUnit as QuantityUnit
  );
  return qtyInMaterialUnit * q.normalizedPrice;
}
