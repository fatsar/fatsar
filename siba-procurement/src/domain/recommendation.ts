import type { Recommendation } from "./enums";
import type { RecommendationConfig } from "./config";
import type { CoverageResult } from "./stock-coverage";

/**
 * Deterministic purchasing recommendation engine.
 *
 * A fixed decision tree (first matching step wins — documented in
 * docs/procurement-rules.md) produces one recommendation plus the reasons
 * with real numbers.
 *
 * CONFIDENCE IS NOT AI. It is a deterministic data-quality measure:
 * start at 100 and subtract documented penalties for missing/stale inputs and
 * for decisive comparisons that sit within 10% of their threshold (borderline
 * decisions deserve less confidence). Clamped to [25, 95] — the engine never
 * claims certainty. Every deduction is returned so the UI can show the math.
 */

export interface RecommendationInput {
  coverage: CoverageResult;
  leadTimeDays: number | null;
  safetyStock: number;
  targetPrice: number | null;
  /** Latest VALID (unexpired, ACTIVE) quotation price, normalized. */
  latestValidPrice: number | null;
  /** Latest quotation of any validity, normalized (for context). */
  latestPrice: number | null;
  /** Age in days of the newest quotation, valid or not. */
  latestQuotationAgeDays: number | null;
  avg3m: number | null;
  approvedSupplierCount: number;
  /** True when an active HIGH/CRITICAL market-intel entry matches. */
  highRiskIntelActive: boolean;
  openPurchaseQty: number;
  inboundQty: number;
}

export interface ConfidenceDeduction {
  points: number;
  reason: string;
}

export interface RecommendationResult {
  recommendation: Recommendation;
  confidence: number; // 25–95
  reasons: string[];
  confidenceDeductions: ConfidenceDeduction[];
  /** Which decision-tree step fired (for docs/tests/debugging). */
  decisionStep: string;
}

export const RECOMMENDATION_PRIORITY: Record<Recommendation, number> = {
  URGENT_PURCHASE: 0,
  BUY_NOW: 1,
  SECURE_SUPPLY: 2,
  REQUEST_QUOTATION: 3,
  NEGOTIATE: 4,
  WAIT: 5,
  MONITOR: 6,
};

const fmt = (n: number) =>
  n.toLocaleString("en-US", { maximumFractionDigits: 1 });
const pctFmt = (n: number) => `${n > 0 ? "+" : ""}${n.toFixed(1)}%`;

/** |a − b| within `tolerancePct`% of b ⇒ the comparison is borderline. */
function nearThreshold(a: number, b: number, tolerancePct = 10): boolean {
  if (b === 0) return false;
  return Math.abs(a - b) / Math.abs(b) < tolerancePct / 100;
}

export function recommendPurchase(
  input: RecommendationInput,
  config: RecommendationConfig
): RecommendationResult {
  const c = input.coverage;
  const reasons: string[] = [];
  const deductions: ConfidenceDeduction[] = [];
  const borderline: string[] = [];

  // ---------- data-quality deductions (independent of the decision) -------
  if (c.dailyDemand == null) {
    deductions.push({
      points: 30,
      reason: "No consumption data — coverage cannot be calculated.",
    });
  }
  if (input.leadTimeDays == null) {
    deductions.push({ points: 15, reason: "Supplier lead time is not set." });
  }
  if (input.safetyStock <= 0) {
    deductions.push({ points: 5, reason: "Safety stock is not set." });
  }
  if (input.latestPrice == null) {
    deductions.push({
      points: 20,
      reason: "No quotation on file for this material.",
    });
  } else if (input.latestValidPrice == null) {
    deductions.push({
      points: 10,
      reason: "Latest quotation has expired — pricing may be outdated.",
    });
  } else if (
    input.latestQuotationAgeDays != null &&
    input.latestQuotationAgeDays > config.quotationStaleDays
  ) {
    deductions.push({
      points: 10,
      reason: `Latest quotation is ${fmt(input.latestQuotationAgeDays)} days old (stale after ${config.quotationStaleDays}).`,
    });
  }
  if (input.targetPrice == null) {
    deductions.push({ points: 10, reason: "No target price is set." });
  }

  // ---------- shared context lines ----------------------------------------
  const pushCoverageReasons = () => {
    if (c.coverageDays != null) {
      reasons.push(`Current stock coverage is ${fmt(c.coverageDays)} days.`);
    }
    if (
      c.projectedCoverageDays != null &&
      (input.inboundQty > 0 || input.openPurchaseQty > 0)
    ) {
      reasons.push(
        `Projected coverage including inbound/open orders is ${fmt(c.projectedCoverageDays)} days.`
      );
    } else if (
      c.projectedCoverageDays == null &&
      c.dailyDemand != null &&
      (input.inboundQty > 0 || input.openPurchaseQty > 0)
    ) {
      reasons.push(
        `Projected coverage including inbound/open orders exceeds ${c.horizonDays} days.`
      );
    }
    if (input.leadTimeDays != null) {
      reasons.push(`Supplier lead time is ${fmt(input.leadTimeDays)} days.`);
    }
    if (c.safetyStockDays != null && c.safetyStockDays > 0) {
      reasons.push(`Safety stock requirement is ${fmt(c.safetyStockDays)} days.`);
    }
  };

  const priceGapPct =
    input.latestValidPrice != null &&
    input.targetPrice != null &&
    input.targetPrice > 0
      ? ((input.latestValidPrice - input.targetPrice) / input.targetPrice) * 100
      : null;

  const pushPriceReason = () => {
    if (priceGapPct != null) {
      reasons.push(
        priceGapPct > 0
          ? `Latest quotation is ${pctFmt(priceGapPct)} above the target price.`
          : `Latest quotation is at or below the target price (${pctFmt(priceGapPct)}).`
      );
      if (
        nearThreshold(
          priceGapPct,
          config.negotiateThresholdPct,
          10
        )
      ) {
        borderline.push("price-vs-target is borderline");
      }
    }
  };

  const finish = (
    recommendation: Recommendation,
    decisionStep: string
  ): RecommendationResult => {
    for (const b of borderline.slice(0, 2)) {
      deductions.push({
        points: 10,
        reason: `Decision input is close to its threshold (${b}).`,
      });
    }
    let confidence = 100 - deductions.reduce((s, d) => s + d.points, 0);
    confidence = Math.max(25, Math.min(95, confidence));
    return {
      recommendation,
      confidence,
      reasons,
      confidenceDeductions: deductions,
      decisionStep,
    };
  };

  // ---------- STEP 0: no demand data --------------------------------------
  if (c.dailyDemand == null) {
    reasons.push(
      "Average consumption is not recorded, so coverage and reorder point cannot be calculated."
    );
    reasons.push("Enter stock and consumption data to enable recommendations.");
    return finish("MONITOR", "0-no-demand-data");
  }

  const lead = input.leadTimeDays;
  const stockout = c.projectedStockoutDay;
  const belowSafetyNow =
    c.safetyStockDays != null &&
    c.coverageDays != null &&
    c.coverageDays < c.safetyStockDays;
  const hasValidQuote = input.latestValidPrice != null;

  // ---------- STEP 1: URGENT PURCHASE --------------------------------------
  if (
    lead != null &&
    stockout != null &&
    (stockout <= lead * config.urgentStockoutFactor ||
      (stockout < lead && belowSafetyNow))
  ) {
    pushCoverageReasons();
    reasons.push(
      `Projected stockout in ${fmt(stockout)} days — replenishment cannot arrive in time even if ordered today.`
    );
    if (belowSafetyNow) {
      reasons.push("Stock is already below the safety level.");
    }
    reasons.push(
      hasValidQuote
        ? "A valid quotation is on file — place the order immediately."
        : "No valid quotation on file — obtain an emergency offer and order immediately."
    );
    if (nearThreshold(stockout, lead * config.urgentStockoutFactor)) {
      borderline.push("stockout-vs-urgent-threshold is borderline");
    }
    return finish("URGENT_PURCHASE", "1-urgent");
  }

  // ---------- STEP 2: stockout inside lead time ----------------------------
  if (lead != null && stockout != null && stockout <= lead) {
    pushCoverageReasons();
    reasons.push(
      `Projected stockout in ${fmt(stockout)} days is inside the ${fmt(lead)}-day lead time.`
    );
    if (belowSafetyNow) {
      reasons.push("Stock is already below the safety level.");
    }
    if (nearThreshold(stockout, lead)) {
      borderline.push("stockout-vs-lead-time is borderline");
    }
    if (hasValidQuote) {
      pushPriceReason();
      reasons.push("A valid quotation is available — order without delay.");
      return finish("BUY_NOW", "2-stockout-in-lead");
    }
    reasons.push(
      "No valid quotation on file — request quotations immediately so an order can be placed."
    );
    return finish("REQUEST_QUOTATION", "2-stockout-in-lead-no-quote");
  }

  // ---------- STEP 3: below safety stock now -------------------------------
  if (belowSafetyNow) {
    pushCoverageReasons();
    reasons.push(
      `Usable stock (${fmt(c.usableStock)}) is below the safety stock level.`
    );
    if (input.inboundQty > 0) {
      reasons.push(
        `Confirmed inbound of ${fmt(input.inboundQty)} improves the position but stock must be rebuilt above safety level.`
      );
    }
    if (hasValidQuote) {
      pushPriceReason();
      if (priceGapPct != null && priceGapPct > config.negotiateThresholdPct) {
        reasons.push(
          "Price is above target, but replenishment takes priority — buy the required volume now and negotiate the next lot."
        );
      }
      return finish("BUY_NOW", "3-below-safety");
    }
    reasons.push("No valid quotation on file — request quotations urgently.");
    return finish("REQUEST_QUOTATION", "3-below-safety-no-quote");
  }

  // ---------- STEP 4: inventory position below reorder point ---------------
  if (c.belowReorderPoint === true) {
    pushCoverageReasons();
    reasons.push(
      `Inventory position (${fmt(c.inventoryPosition)} incl. open orders) is below the reorder point (${fmt(c.reorderPointQty ?? 0)}).`
    );
    if (
      c.reorderPointQty != null &&
      nearThreshold(c.inventoryPosition, c.reorderPointQty)
    ) {
      borderline.push("inventory-position-vs-reorder-point is borderline");
    }
    const stale =
      input.latestQuotationAgeDays == null ||
      input.latestQuotationAgeDays > config.quotationStaleDays;
    if (!hasValidQuote || stale) {
      reasons.push(
        !hasValidQuote
          ? "No valid quotation on file — request updated offers."
          : "The latest quotation is stale — request updated offers before ordering."
      );
      return finish("REQUEST_QUOTATION", "4-reorder-no-fresh-quote");
    }
    pushPriceReason();
    if (priceGapPct != null && priceGapPct > config.negotiateThresholdPct) {
      reasons.push(
        "Reorder is due and the offer is above target — negotiate before committing; time buffer still exists."
      );
      return finish("NEGOTIATE", "4-reorder-negotiate");
    }
    reasons.push("Reorder point reached with an acceptable price — order now.");
    return finish("BUY_NOW", "4-reorder-buy");
  }

  // ---------- STEP 5: comfortable zone -------------------------------------
  pushCoverageReasons();
  reasons.push("No immediate shortage risk exists.");

  // 5a. Secure supply: single source + high-risk market signal + finite buffer.
  if (
    input.approvedSupplierCount <= 1 &&
    input.highRiskIntelActive &&
    c.reorderPointQty != null &&
    c.inventoryPosition < c.reorderPointQty * config.monitorFactor
  ) {
    reasons.push(
      "Single approved supplier and an active high-risk market alert affect this material."
    );
    reasons.push(
      `Inventory position (${fmt(c.inventoryPosition)}) is within ${config.monitorFactor}× of the reorder point — consider covering forward requirements early.`
    );
    pushPriceReason();
    return finish("SECURE_SUPPLY", "5a-secure-supply");
  }

  // 5b. Price above target → deliberately wait / recheck.
  if (priceGapPct != null && priceGapPct > config.negotiateThresholdPct) {
    pushPriceReason();
    reasons.push(
      "Stock is sufficient — wait and recheck the price before committing."
    );
    return finish("WAIT", "5b-wait-price");
  }

  // 5c. Default: monitor.
  if (priceGapPct != null) pushPriceReason();
  if (input.highRiskIntelActive) {
    reasons.push("An active market alert affects this material — keep watching.");
  }
  reasons.push("Coverage is comfortable; review again at the next cycle.");
  return finish("MONITOR", "5c-monitor");
}
