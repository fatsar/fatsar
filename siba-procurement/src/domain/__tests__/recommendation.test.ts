import { describe, expect, it } from "vitest";
import { DEFAULT_RECOMMENDATION_CONFIG } from "../config";
import { calculateCoverage, type StockInput } from "../stock-coverage";
import {
  recommendPurchase,
  type RecommendationInput,
} from "../recommendation";

const cfg = DEFAULT_RECOMMENDATION_CONFIG;

function makeInput(
  stock: Partial<StockInput>,
  rest: Partial<RecommendationInput> = {}
): RecommendationInput {
  const stockInput: StockInput = {
    currentStock: 100_000,
    reservedStock: 0,
    safetyStock: 15_000,
    avgDailyConsumption: 1_000,
    avgMonthlyConsumption: 30_000,
    forecastMonthlyDemand: null,
    leadTimeDays: 30,
    openPurchaseQty: 0,
    inboundQty: 0,
    inboundEtaDays: null,
    ...stock,
  };
  return {
    coverage: calculateCoverage(stockInput),
    leadTimeDays: stockInput.leadTimeDays,
    safetyStock: stockInput.safetyStock,
    targetPrice: 1.0,
    latestValidPrice: 1.0,
    latestPrice: 1.0,
    latestQuotationAgeDays: 10,
    avg3m: 1.0,
    approvedSupplierCount: 2,
    highRiskIntelActive: false,
    openPurchaseQty: stockInput.openPurchaseQty,
    inboundQty: stockInput.inboundQty,
    ...rest,
  };
}

describe("recommendPurchase — decision tree", () => {
  it("0: MONITOR with low confidence when consumption is unknown", () => {
    const r = recommendPurchase(
      makeInput({ avgDailyConsumption: null, avgMonthlyConsumption: null }),
      cfg
    );
    expect(r.recommendation).toBe("MONITOR");
    expect(r.decisionStep).toBe("0-no-demand-data");
    expect(r.confidence).toBeLessThanOrEqual(70);
    expect(r.reasons.join(" ")).toContain("consumption");
  });

  it("1: URGENT_PURCHASE when stockout is far inside lead time", () => {
    // 11 days of stock, 50-day lead, below safety → urgent
    const r = recommendPurchase(
      makeInput({
        currentStock: 3_500,
        reservedStock: 500,
        safetyStock: 4_000,
        avgDailyConsumption: null,
        avgMonthlyConsumption: 8_000,
        leadTimeDays: 50,
      }),
      cfg
    );
    expect(r.recommendation).toBe("URGENT_PURCHASE");
    expect(r.reasons.join(" ")).toContain("cannot arrive in time");
  });

  it("2: REQUEST_QUOTATION when stockout inside lead time and no valid quote (PMDI case)", () => {
    const r = recommendPurchase(
      makeInput(
        {
          currentStock: 340_000,
          reservedStock: 20_000,
          safetyStock: 150_000,
          avgDailyConsumption: 10_000,
          avgMonthlyConsumption: 300_000,
          leadTimeDays: 45,
        },
        {
          latestValidPrice: null, // expired
          latestPrice: 1.79,
          latestQuotationAgeDays: 41,
          targetPrice: 1.72,
        }
      ),
      cfg
    );
    expect(r.recommendation).toBe("REQUEST_QUOTATION");
    expect(r.decisionStep).toBe("2-stockout-in-lead-no-quote");
    expect(r.reasons.join(" ")).toContain("stock coverage is 32 days");
    // deduction for expired quotation applied
    expect(r.confidenceDeductions.some((d) => d.reason.includes("expired"))).toBe(true);
  });

  it("2: BUY_NOW when stockout inside lead time and a valid quote exists", () => {
    const r = recommendPurchase(
      makeInput({
        currentStock: 21_000,
        reservedStock: 1_000,
        safetyStock: 12_500,
        avgDailyConsumption: null,
        avgMonthlyConsumption: 25_000,
        leadTimeDays: 42,
      }),
      cfg
    );
    expect(r.recommendation).toBe("BUY_NOW");
    expect(r.decisionStep).toBe("2-stockout-in-lead");
  });

  it("3: BUY_NOW when below safety now even if price is above target", () => {
    // Inbound pushes projected stockout (day 40) beyond the 30-day lead time,
    // but stock sits below safety TODAY → replenish now regardless of price.
    const r = recommendPurchase(
      makeInput(
        {
          currentStock: 32_000,
          reservedStock: 2_000,
          safetyStock: 45_000,
          avgDailyConsumption: 3_000,
          avgMonthlyConsumption: 90_000,
          leadTimeDays: 30,
          inboundQty: 90_000,
          inboundEtaDays: 5,
        },
        { latestValidPrice: 1.01, latestPrice: 1.01, targetPrice: 0.92 }
      ),
      cfg
    );
    expect(r.recommendation).toBe("BUY_NOW");
    expect(r.decisionStep).toBe("3-below-safety");
    expect(r.reasons.join(" ")).toContain("negotiate the next lot");
  });

  it("4: NEGOTIATE at reorder point when price is above target", () => {
    // stockout day 59 > lead 50 (buffer exists), but position 55k < reorder
    // 60.7k; valid quote +6.4% over target → negotiate before committing.
    const r = recommendPurchase(
      makeInput(
        {
          currentStock: 56_000,
          reservedStock: 1_000,
          safetyStock: 14_000,
          avgDailyConsumption: null,
          avgMonthlyConsumption: 28_000,
          leadTimeDays: 50,
        },
        { latestValidPrice: 2.5, latestPrice: 2.5, targetPrice: 2.35 }
      ),
      cfg
    );
    expect(r.recommendation).toBe("NEGOTIATE");
    expect(r.decisionStep).toBe("4-reorder-negotiate");
  });

  it("4: BUY_NOW at reorder point when price is at/below target", () => {
    const r = recommendPurchase(
      makeInput(
        {
          currentStock: 41_000,
          reservedStock: 1_000,
          safetyStock: 11_000,
          avgDailyConsumption: null,
          avgMonthlyConsumption: 22_000,
          leadTimeDays: 50,
        },
        { latestValidPrice: 2.42, latestPrice: 2.42, targetPrice: 2.45 }
      ),
      cfg
    );
    expect(r.recommendation).toBe("BUY_NOW");
    expect(r.decisionStep).toBe("4-reorder-buy");
  });

  it("4: REQUEST_QUOTATION at reorder point when the quote is stale", () => {
    const r = recommendPurchase(
      makeInput(
        {
          currentStock: 56_000,
          reservedStock: 1_000,
          safetyStock: 14_000,
          avgDailyConsumption: null,
          avgMonthlyConsumption: 28_000,
          leadTimeDays: 50,
        },
        { latestQuotationAgeDays: 90 }
      ),
      cfg
    );
    expect(r.recommendation).toBe("REQUEST_QUOTATION");
    expect(r.decisionStep).toBe("4-reorder-no-fresh-quote");
  });

  it("5a: SECURE_SUPPLY for single-sourced material under a high-risk alert", () => {
    // CP52-like: comfortable but within 1.5× reorder, single supplier, HIGH intel
    const r = recommendPurchase(
      makeInput(
        {
          currentStock: 122_000,
          reservedStock: 2_000,
          safetyStock: 30_000,
          avgDailyConsumption: 2_000,
          avgMonthlyConsumption: 60_000,
          leadTimeDays: 35,
        },
        {
          approvedSupplierCount: 1,
          highRiskIntelActive: true,
          latestValidPrice: 0.865,
          latestPrice: 0.865,
          targetPrice: 0.8,
        }
      ),
      cfg
    );
    expect(r.recommendation).toBe("SECURE_SUPPLY");
    expect(r.decisionStep).toBe("5a-secure-supply");
    expect(r.reasons.join(" ")).toContain("Single approved supplier");
  });

  it("5b: WAIT when stock is comfortable but price is above target (HS3130 case)", () => {
    const r = recommendPurchase(
      makeInput(
        {
          currentStock: 132_000,
          reservedStock: 2_000,
          safetyStock: 28_500,
          avgDailyConsumption: 1_900,
          avgMonthlyConsumption: 57_000,
          leadTimeDays: 50,
        },
        { latestValidPrice: 2.41, latestPrice: 2.41, targetPrice: 2.3 }
      ),
      cfg
    );
    expect(r.recommendation).toBe("WAIT");
    expect(r.decisionStep).toBe("5b-wait-price");
    expect(r.reasons.join(" ")).toContain("above the target price");
    expect(r.reasons.join(" ")).toContain("No immediate shortage risk exists.");
  });

  it("5c: MONITOR in the comfortable zone with acceptable price", () => {
    const r = recommendPurchase(makeInput({}), cfg);
    expect(r.recommendation).toBe("MONITOR");
    expect(r.decisionStep).toBe("5c-monitor");
    expect(r.confidence).toBeGreaterThanOrEqual(85);
  });

  it("confidence: borderline decisions lose points, and it never exceeds 95", () => {
    // position 90k vs reorder 85.5k → within 10% ⇒ borderline deduction
    const r = recommendPurchase(
      makeInput(
        {
          currentStock: 92_000,
          reservedStock: 2_000,
          safetyStock: 22_500,
          avgDailyConsumption: 1_500,
          avgMonthlyConsumption: 45_000,
          leadTimeDays: 42,
        },
        { latestValidPrice: 1.3, latestPrice: 1.3, targetPrice: 1.26 }
      ),
      cfg
    );
    expect(r.confidence).toBeLessThanOrEqual(95);
    expect(
      r.confidenceDeductions.some((d) => d.reason.includes("close to its threshold"))
    ).toBe(true);
  });
});
