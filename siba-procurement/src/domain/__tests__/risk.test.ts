import { describe, expect, it } from "vitest";
import { DEFAULT_RECOMMENDATION_CONFIG } from "../config";
import { calculateCoverage } from "../stock-coverage";
import { assessRisk, type RiskInput } from "../risk";

const cfg = DEFAULT_RECOMMENDATION_CONFIG;

function baseInput(overrides: Partial<RiskInput> = {}): RiskInput {
  return {
    coverage: calculateCoverage({
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
    }),
    leadTimeDays: 30,
    latestPrice: 1.0,
    avg3m: 1.0,
    targetPrice: 1.0,
    avgMonthlyConsumption: 30_000,
    forecastMonthlyDemand: null,
    approvedSupplierCount: 2,
    preferredSupplierName: "Acme",
    preferredSupplierScore: 80,
    intel: [],
    ...overrides,
  };
}

describe("assessRisk", () => {
  it("classifies a comfortable position as LOW with no factors", () => {
    const r = assessRisk(baseInput(), cfg);
    expect(r.level).toBe("LOW");
    expect(r.score).toBe(0);
    expect(r.factors).toHaveLength(0);
  });

  it("PMDI-like case: stockout inside lead time + HIGH intel ⇒ HIGH with reasons", () => {
    const r = assessRisk(
      baseInput({
        coverage: calculateCoverage({
          currentStock: 340_000,
          reservedStock: 20_000,
          safetyStock: 150_000,
          avgDailyConsumption: 10_000,
          avgMonthlyConsumption: 300_000,
          forecastMonthlyDemand: null,
          leadTimeDays: 45,
          openPurchaseQty: 0,
          inboundQty: 0,
          inboundEtaDays: null,
        }),
        leadTimeDays: 45,
        latestPrice: 1.79,
        avg3m: 1.81,
        targetPrice: 1.72,
        approvedSupplierCount: 3,
        intel: [{ title: "Producer maintenance next month", riskLevel: "HIGH" }],
      }),
      cfg
    );
    expect(r.level).toBe("HIGH");
    // stockoutInsideLead 45 + aboveTarget 8 (+4.1% > 3%) + intel 10 = 63
    expect(r.score).toBe(63);
    expect(r.reasons.join(" ")).toContain("inside the 45-day supplier lead time");
    expect(r.reasons.join(" ")).toContain("Producer maintenance");
  });

  it("already below safety + stockout inside lead ⇒ CRITICAL", () => {
    const r = assessRisk(
      baseInput({
        coverage: calculateCoverage({
          currentStock: 3_500,
          reservedStock: 500,
          safetyStock: 4_000,
          avgDailyConsumption: null,
          avgMonthlyConsumption: 8_000,
          forecastMonthlyDemand: null,
          leadTimeDays: 50,
          openPurchaseQty: 0,
          inboundQty: 0,
          inboundEtaDays: null,
        }),
        leadTimeDays: 50,
        approvedSupplierCount: 1,
        preferredSupplierName: "Hoshine",
      }),
      cfg
    );
    // stockoutBelowSafety 60 + singleSupplier 12 = 72 ≥ 65
    expect(r.level).toBe("CRITICAL");
    expect(r.score).toBe(72);
    expect(r.reasons.some((x) => x.includes("cannot arrive in time"))).toBe(true);
    expect(r.reasons.some((x) => x.includes("Single approved supplier"))).toBe(true);
  });

  it("stock rules do not stack — only the worst one counts", () => {
    const r = assessRisk(
      baseInput({
        coverage: calculateCoverage({
          currentStock: 10_000,
          reservedStock: 0,
          safetyStock: 15_000, // below safety AND below reorder AND stockout in lead
          avgDailyConsumption: 1_000,
          avgMonthlyConsumption: 30_000,
          forecastMonthlyDemand: null,
          leadTimeDays: 30,
          openPurchaseQty: 0,
          inboundQty: 0,
          inboundEtaDays: null,
        }),
      }),
      cfg
    );
    const stockFactors = r.factors.filter((f) =>
      ["stockoutBelowSafety", "stockoutInsideLead", "belowSafety", "belowReorder"].includes(f.rule)
    );
    expect(stockFactors).toHaveLength(1);
    expect(stockFactors[0].rule).toBe("stockoutBelowSafety");
  });

  it("flags missing consumption data instead of failing", () => {
    const r = assessRisk(
      baseInput({
        coverage: calculateCoverage({
          currentStock: 100_000,
          reservedStock: 0,
          safetyStock: 0,
          avgDailyConsumption: null,
          avgMonthlyConsumption: null,
          forecastMonthlyDemand: null,
          leadTimeDays: null,
          openPurchaseQty: 0,
          inboundQty: 0,
          inboundEtaDays: null,
        }),
        leadTimeDays: null,
      }),
      cfg
    );
    expect(r.level).toBe("MEDIUM");
    expect(r.reasons[0]).toContain("No consumption data");
  });

  it("caps market-intelligence points", () => {
    const r = assessRisk(
      baseInput({
        intel: [
          { title: "A", riskLevel: "CRITICAL" },
          { title: "B", riskLevel: "HIGH" },
          { title: "C", riskLevel: "HIGH" },
          { title: "D", riskLevel: "MEDIUM" }, // ignored
        ],
      }),
      cfg
    );
    const intelPts = r.factors
      .filter((f) => f.rule === "marketIntel")
      .reduce((s, f) => s + f.points, 0);
    expect(intelPts).toBe(cfg.riskPoints.intelCap);
  });
});
