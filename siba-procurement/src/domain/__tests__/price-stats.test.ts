import { describe, expect, it } from "vitest";
import { calculatePriceStats, type PricePoint } from "../price-stats";

const NOW = new Date("2026-08-17T12:00:00Z");
const daysAgo = (n: number) => new Date(NOW.getTime() - n * 86_400_000);

describe("calculatePriceStats", () => {
  it("reproduces the spec example: 1.79 vs 1.84 → −2.72%, target diff +4.07%", () => {
    const points: PricePoint[] = [
      { date: daysAgo(40), price: 1.79 },
      { date: daysAgo(70), price: 1.84 },
      { date: daysAgo(100), price: 1.8 },
    ];
    const s = calculatePriceStats(points, 1.72, NOW);
    expect(s.latest).toBe(1.79);
    expect(s.previous).toBe(1.84);
    expect(s.changePct).toBe(-2.72);
    expect(s.diffToTargetPct).toBe(4.07);
  });

  it("computes window averages and min/max over 12 months", () => {
    const points: PricePoint[] = [
      { date: daysAgo(10), price: 2.0 },
      { date: daysAgo(80), price: 2.2 },
      { date: daysAgo(170), price: 2.4 },
      { date: daysAgo(300), price: 2.8 },
      { date: daysAgo(400), price: 9.9 }, // outside 12m — ignored in windows
    ];
    const s = calculatePriceStats(points, null, NOW);
    expect(s.avg3m).toBeCloseTo(2.1, 4);
    expect(s.avg6m).toBeCloseTo((2.0 + 2.2 + 2.4) / 3, 4);
    expect(s.avg12m).toBeCloseTo((2.0 + 2.2 + 2.4 + 2.8) / 4, 4);
    expect(s.min12m).toBe(2.0);
    expect(s.max12m).toBe(2.8);
    expect(s.sampleCount).toBe(5);
  });

  it("handles empty and single-point histories", () => {
    const empty = calculatePriceStats([], 1.5, NOW);
    expect(empty.latest).toBeNull();
    expect(empty.changePct).toBeNull();
    expect(empty.diffToTargetPct).toBeNull();

    const single = calculatePriceStats([{ date: daysAgo(5), price: 3 }], 2, NOW);
    expect(single.latest).toBe(3);
    expect(single.previous).toBeNull();
    expect(single.changePct).toBeNull();
    expect(single.diffToTargetPct).toBe(50);
  });
});
