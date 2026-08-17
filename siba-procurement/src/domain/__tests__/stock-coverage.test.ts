import { describe, expect, it } from "vitest";
import { calculateCoverage, type StockInput } from "../stock-coverage";

const base: StockInput = {
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
};

describe("calculateCoverage", () => {
  it("computes basic coverage = usable / daily (PMDI case: 32 days)", () => {
    const r = calculateCoverage(base);
    expect(r.usableStock).toBe(320_000);
    expect(r.coverageDays).toBe(32);
    expect(r.safetyStockDays).toBe(15);
    expect(r.projectedStockoutDay).toBe(32); // consistent with basic coverage
    expect(r.reorderPointQty).toBe(10_000 * 45 + 150_000); // 600,000
    expect(r.reorderPointDays).toBe(60);
    expect(r.belowReorderPoint).toBe(true);
  });

  it("derives daily demand from monthly when daily is missing", () => {
    const r = calculateCoverage({
      ...base,
      avgDailyConsumption: null,
      avgMonthlyConsumption: 300_000,
    });
    expect(r.dailyDemand).toBe(10_000);
    expect(r.demandSource).toBe("monthly");
  });

  it("returns nulls (not crashes) when demand is unknown", () => {
    const r = calculateCoverage({
      ...base,
      avgDailyConsumption: null,
      avgMonthlyConsumption: null,
    });
    expect(r.dailyDemand).toBeNull();
    expect(r.coverageDays).toBeNull();
    expect(r.projectedStockoutDay).toBeNull();
    expect(r.belowReorderPoint).toBeNull();
    expect(r.demandSource).toBe("none");
    expect(r.inventoryPosition).toBe(320_000);
  });

  it("extends projected coverage when inbound arrives before stockout", () => {
    // DME-like: 30,000 usable, 3,000/day → basic 10 days; 60,000 inbound at day 5.
    const r = calculateCoverage({
      currentStock: 32_000,
      reservedStock: 2_000,
      safetyStock: 45_000,
      avgDailyConsumption: 3_000,
      avgMonthlyConsumption: 90_000,
      forecastMonthlyDemand: null,
      leadTimeDays: 30,
      openPurchaseQty: 0,
      inboundQty: 60_000,
      inboundEtaDays: 5,
    });
    expect(r.coverageDays).toBe(10);
    expect(r.projectedStockoutDay).toBe(30); // (30k + 60k) / 3k
    expect(r.projectedBelowSafetyDay).toBe(1); // already below safety
    expect(r.inventoryPosition).toBe(90_000);
  });

  it("counts an open PO (no ETA) as arriving after one lead time", () => {
    // Polyol 56-like: usable 180k, 4k/day, 80k open PO, lead 42.
    const r = calculateCoverage({
      currentStock: 190_000,
      reservedStock: 10_000,
      safetyStock: 60_000,
      avgDailyConsumption: 4_000,
      avgMonthlyConsumption: 120_000,
      forecastMonthlyDemand: null,
      leadTimeDays: 42,
      openPurchaseQty: 80_000,
      inboundQty: 0,
      inboundEtaDays: null,
    });
    expect(r.coverageDays).toBe(45);
    // stock at day 42: 180k − 168k + 80k = 92k → stockout at day 65
    expect(r.projectedStockoutDay).toBe(65);
    expect(r.projectedBelowSafetyDay).toBe(31); // dips below 60k before PO arrives
    expect(r.belowReorderPoint).toBe(false); // position 260k ≥ 228k
  });

  it("uses forecast demand for the projection when it is higher", () => {
    const r = calculateCoverage({
      ...base,
      forecastMonthlyDemand: 450_000, // 15,000/day > 10,000/day average
    });
    expect(r.dailyDemand).toBe(10_000); // basic figure uses average
    expect(r.projectedDailyDemand).toBe(15_000);
    expect(r.projectedStockoutDay).toBe(22); // ceil(320k / 15k) → day 22 goes ≤ 0
  });

  it("treats past inbound ETA as arriving on day 1", () => {
    const r = calculateCoverage({
      ...base,
      currentStock: 20_000,
      reservedStock: 0,
      inboundQty: 100_000,
      inboundEtaDays: -3,
    });
    expect(r.projectedStockoutDay).toBe(12); // (20k + 100k) / 10k
  });
});
