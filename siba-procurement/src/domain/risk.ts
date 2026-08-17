import type { RiskLevel } from "./enums";
import type { RecommendationConfig } from "./config";
import type { CoverageResult } from "./stock-coverage";

/**
 * Deterministic procurement risk engine.
 *
 * Rules add points; the total maps to LOW / MEDIUM / HIGH / CRITICAL via
 * configurable thresholds. Every triggered rule contributes a human-readable
 * reason with the actual numbers, so no classification is ever unexplained.
 *
 * The stock-related rules are a FAMILY: only the single worst one counts
 * (they describe the same underlying shortage and must not stack).
 */

export interface RiskFactor {
  rule: string;
  points: number;
  reason: string;
}

export interface RiskAssessment {
  level: RiskLevel;
  score: number;
  factors: RiskFactor[];
  reasons: string[]; // convenience: factor reasons in order
}

export interface IntelSignal {
  title: string;
  riskLevel: RiskLevel;
}

export interface RiskInput {
  coverage: CoverageResult;
  leadTimeDays: number | null;
  /** Latest normalized price (same currency as target/avg). */
  latestPrice: number | null;
  avg3m: number | null;
  targetPrice: number | null;
  avgMonthlyConsumption: number | null;
  forecastMonthlyDemand: number | null;
  approvedSupplierCount: number;
  preferredSupplierName: string | null;
  preferredSupplierScore: number | null;
  /** Active market-intelligence entries already matched to this material. */
  intel: IntelSignal[];
}

const fmt = (n: number) =>
  n.toLocaleString("en-US", { maximumFractionDigits: 1 });
const pct = (n: number) => `${n > 0 ? "+" : ""}${n.toFixed(1)}%`;

export function assessRisk(
  input: RiskInput,
  config: RecommendationConfig
): RiskAssessment {
  const p = config.riskPoints;
  const c = input.coverage;
  const factors: RiskFactor[] = [];

  // ---- stock family (worst single rule wins) ----------------------------
  if (c.dailyDemand == null) {
    factors.push({
      rule: "noDemandData",
      points: p.noDemandData,
      reason:
        "No consumption data — stock coverage cannot be calculated. Enter average consumption.",
    });
  } else {
    const lead = input.leadTimeDays;
    const stockout = c.projectedStockoutDay;
    // Coverage below safety-days ⇔ usable stock below safety stock.
    const isBelowSafetyNow =
      c.safetyStockDays != null && c.coverageDays != null
        ? c.coverageDays < c.safetyStockDays
        : false;

    let family: RiskFactor | null = null;
    if (
      lead != null &&
      stockout != null &&
      stockout <= lead &&
      isBelowSafetyNow
    ) {
      family = {
        rule: "stockoutBelowSafety",
        points: p.stockoutBelowSafety,
        reason:
          `Stock is already below safety level and projected stockout is in ${fmt(stockout)} days, ` +
          `inside the ${fmt(lead)}-day supplier lead time — a new order today cannot arrive in time.`,
      };
    } else if (lead != null && stockout != null && stockout <= lead) {
      family = {
        rule: "stockoutInsideLead",
        points: p.stockoutInsideLead,
        reason:
          `Projected stockout in ${fmt(stockout)} days (incl. confirmed inbound) is inside the ` +
          `${fmt(lead)}-day supplier lead time.`,
      };
    } else if (
      isBelowSafetyNow ||
      (lead != null &&
        c.projectedBelowSafetyDay != null &&
        c.projectedBelowSafetyDay <= lead)
    ) {
      family = {
        rule: "belowSafety",
        points: p.belowSafety,
        reason: isBelowSafetyNow
          ? `Usable stock (${fmt(c.usableStock)}) is already below safety stock ` +
            `(coverage ${fmt(c.coverageDays ?? 0)} days vs safety requirement ${fmt(c.safetyStockDays ?? 0)} days).`
          : `Stock is projected to fall below safety level in ${fmt(c.projectedBelowSafetyDay!)} days, ` +
            `before a new order could arrive (lead time ${fmt(lead!)} days).`,
      };
    } else if (c.belowReorderPoint === true) {
      family = {
        rule: "belowReorder",
        points: p.belowReorder,
        reason:
          `Inventory position (${fmt(c.inventoryPosition)} incl. open orders) is below the reorder point ` +
          `(${fmt(c.reorderPointQty ?? 0)} = lead-time demand + safety stock).`,
      };
    }
    if (family) factors.push(family);
  }

  // ---- price rules -------------------------------------------------------
  if (
    input.latestPrice != null &&
    input.avg3m != null &&
    input.avg3m > 0
  ) {
    const trendPct = ((input.latestPrice - input.avg3m) / input.avg3m) * 100;
    if (trendPct > config.priceSpikeThresholdPct) {
      factors.push({
        rule: "priceTrend",
        points: p.priceTrend,
        reason: `Latest price is ${pct(trendPct)} above the 3-month average — upward price trend.`,
      });
    }
  }
  if (
    input.latestPrice != null &&
    input.targetPrice != null &&
    input.targetPrice > 0
  ) {
    const gapPct =
      ((input.latestPrice - input.targetPrice) / input.targetPrice) * 100;
    if (gapPct > config.negotiateThresholdPct) {
      factors.push({
        rule: "aboveTarget",
        points: p.aboveTarget,
        reason: `Latest price is ${pct(gapPct)} above the target price.`,
      });
    }
  }

  // ---- demand surge ------------------------------------------------------
  if (
    input.forecastMonthlyDemand != null &&
    input.avgMonthlyConsumption != null &&
    input.avgMonthlyConsumption > 0
  ) {
    const surgePct =
      ((input.forecastMonthlyDemand - input.avgMonthlyConsumption) /
        input.avgMonthlyConsumption) *
      100;
    if (surgePct > config.forecastSurgePct) {
      factors.push({
        rule: "forecastSurge",
        points: p.forecastSurge,
        reason: `Forecast demand is ${pct(surgePct)} above average consumption.`,
      });
    }
  }

  // ---- supplier rules ----------------------------------------------------
  if (input.approvedSupplierCount <= 1) {
    factors.push({
      rule: "singleSupplier",
      points: p.singleSupplier,
      reason:
        input.approvedSupplierCount === 1
          ? `Single approved supplier${input.preferredSupplierName ? ` (${input.preferredSupplierName})` : ""} — no qualified alternative.`
          : "No approved supplier on file.",
    });
  }
  if (
    input.preferredSupplierScore != null &&
    input.preferredSupplierScore < 60
  ) {
    factors.push({
      rule: "weakSupplier",
      points: p.weakSupplier,
      reason: `Preferred supplier score is ${fmt(input.preferredSupplierScore)}/100 (below 60).`,
    });
  }

  // ---- market intelligence (capped) --------------------------------------
  let intelPoints = 0;
  for (const signal of input.intel) {
    if (signal.riskLevel !== "HIGH" && signal.riskLevel !== "CRITICAL") continue;
    const pts = signal.riskLevel === "CRITICAL" ? p.intelCritical : p.intelHigh;
    const remaining = p.intelCap - intelPoints;
    if (remaining <= 0) break;
    const applied = Math.min(pts, remaining);
    intelPoints += applied;
    factors.push({
      rule: "marketIntel",
      points: applied,
      reason: `Active market alert (${signal.riskLevel}): ${signal.title}`,
    });
  }

  const score = factors.reduce((s, f) => s + f.points, 0);
  const t = config.riskLevels;
  const level: RiskLevel =
    score >= t.critical
      ? "CRITICAL"
      : score >= t.high
        ? "HIGH"
        : score >= t.medium
          ? "MEDIUM"
          : "LOW";

  return { level, score, factors, reasons: factors.map((f) => f.reason) };
}
