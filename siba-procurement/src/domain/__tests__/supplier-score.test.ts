import { describe, expect, it } from "vitest";
import { DEFAULT_SUPPLIER_SCORE_WEIGHTS } from "../config";
import { calculateSupplierScore, type SupplierRatings } from "../supplier-score";

const full: SupplierRatings = {
  qualityRating: 9,
  pricingRating: 8.5,
  deliveryRating: 8,
  responsivenessRating: 7,
  technicalRating: 8,
  commercialRating: 8,
};

describe("calculateSupplierScore", () => {
  it("computes a 0–100 weighted score with full ratings", () => {
    const r = calculateSupplierScore(full, DEFAULT_SUPPLIER_SCORE_WEIGHTS);
    // 25×0.9 + 20×0.85 + 20×0.8 + 15×0.7 + 10×0.8 + 10×0.8 = 82.0
    expect(r.score).toBe(82);
    expect(r.breakdown).toHaveLength(6);
    const quality = r.breakdown.find((b) => b.dimension === "quality")!;
    expect(quality.contribution).toBe(22.5);
  });

  it("renormalizes when some ratings are missing", () => {
    const r = calculateSupplierScore(
      { ...full, technicalRating: null, commercialRating: null },
      DEFAULT_SUPPLIER_SCORE_WEIGHTS
    );
    // rated weights = 80; (25×0.9+20×0.85+20×0.8+15×0.7) = 66 → 66/80×100 = 82.5
    expect(r.score).toBe(82.5);
    expect(r.ratedWeightSum).toBe(80);
  });

  it("returns null when nothing is rated", () => {
    const r = calculateSupplierScore(
      {
        qualityRating: null,
        pricingRating: null,
        deliveryRating: null,
        responsivenessRating: null,
        technicalRating: null,
        commercialRating: null,
      },
      DEFAULT_SUPPLIER_SCORE_WEIGHTS
    );
    expect(r.score).toBeNull();
  });

  it("respects custom weights", () => {
    const r = calculateSupplierScore(full, {
      quality: 100,
      pricing: 0,
      delivery: 0,
      responsiveness: 0,
      technical: 0,
      commercial: 0,
    });
    expect(r.score).toBe(90);
  });
});
