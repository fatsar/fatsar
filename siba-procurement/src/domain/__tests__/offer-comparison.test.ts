import { describe, expect, it } from "vitest";
import { DEFAULT_OFFER_SCORE_WEIGHTS } from "../config";
import { compareOffers, type ComparableOffer } from "../offer-comparison";

const offers: ComparableOffer[] = [
  {
    id: "wanhua",
    supplierName: "Wanhua",
    supplierScore: 82,
    normalizedPrice: 1.79,
    currency: "USD",
    paymentTermDays: 60,
    leadTimeDays: 45,
    isExpired: false,
  },
  {
    id: "covestro",
    supplierName: "Covestro",
    supplierScore: 80,
    normalizedPrice: 1.95,
    currency: "USD",
    paymentTermDays: 30,
    leadTimeDays: 21,
    isExpired: false,
  },
  {
    id: "kimpex",
    supplierName: "Kimpex",
    supplierScore: 74,
    normalizedPrice: 1.88,
    currency: "USD",
    paymentTermDays: 45,
    leadTimeDays: 10,
    isExpired: true,
  },
];

describe("compareOffers", () => {
  it("assigns the four dimension badges and best overall", () => {
    const r = compareOffers(offers, DEFAULT_OFFER_SCORE_WEIGHTS);
    const byId = Object.fromEntries(r.offers.map((o) => [o.id, o]));
    expect(byId.wanhua.badges).toContain("LOWEST_PRICE");
    expect(byId.wanhua.badges).toContain("BEST_PAYMENT_TERMS");
    expect(byId.wanhua.badges).toContain("BEST_SUPPLIER_SCORE");
    // Kimpex has the shortest lead time but is EXPIRED → no badges at all.
    expect(byId.kimpex.badges).toEqual([]);
    expect(byId.covestro.badges).toContain("SHORTEST_LEAD_TIME");
    const overall = r.offers.filter((o) => o.badges.includes("BEST_OVERALL"));
    expect(overall).toHaveLength(1);
    // Wanhua: price 100×50% + payment 100×15% + lead (10/45=22.2)×15% + 82×20% = 84.7
    expect(byId.wanhua.overallScore).toBeCloseTo(84.7, 1);
    expect(overall[0].id).toBe("wanhua");
    expect(r.warnings.some((w) => w.includes("validity"))).toBe(true);
  });

  it("excludes price from the overall score for mixed currencies", () => {
    const mixed = offers.map((o, i) =>
      i === 0 ? { ...o, currency: "EUR" } : o
    );
    const r = compareOffers(mixed, DEFAULT_OFFER_SCORE_WEIGHTS);
    expect(r.priceComparable).toBe(false);
    expect(r.warnings.some((w) => w.includes("mixed currencies"))).toBe(true);
    for (const o of r.offers) {
      expect(o.badges).not.toContain("LOWEST_PRICE");
      const price = o.dimensions.find((d) => d.dimension === "price")!;
      expect(price.score).toBeNull();
    }
  });

  it("penalizes missing data visibly instead of silently favouring it", () => {
    const withMissing: ComparableOffer[] = [
      offers[0],
      { ...offers[1], paymentTermDays: null },
    ];
    const r = compareOffers(withMissing, DEFAULT_OFFER_SCORE_WEIGHTS);
    const cov = r.offers.find((o) => o.id === "covestro")!;
    const dim = cov.dimensions.find((d) => d.dimension === "paymentTerms")!;
    expect(dim.missing).toBe(true);
    expect(dim.weightedPoints).toBe(0);
  });

  it("handles a single offer (all badges)", () => {
    const r = compareOffers([offers[0]], DEFAULT_OFFER_SCORE_WEIGHTS);
    expect(r.offers[0].badges).toEqual(
      expect.arrayContaining([
        "LOWEST_PRICE",
        "BEST_PAYMENT_TERMS",
        "SHORTEST_LEAD_TIME",
        "BEST_SUPPLIER_SCORE",
        "BEST_OVERALL",
      ])
    );
  });
});
