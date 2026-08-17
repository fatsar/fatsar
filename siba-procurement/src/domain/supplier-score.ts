import type { SupplierScoreWeights } from "./config";

/**
 * Supplier Score: 0–100, from six 0–10 ratings and configurable weights.
 *
 * score = 100 × Σ(weight_i × rating_i / 10) / Σ(weight_i)
 * summed over the dimensions that actually have a rating, so missing ratings
 * do not drag the score down — they simply carry no weight. The breakdown
 * makes the calculation fully explainable in the UI.
 */

export interface SupplierRatings {
  qualityRating: number | null;
  pricingRating: number | null;
  deliveryRating: number | null;
  responsivenessRating: number | null;
  technicalRating: number | null;
  commercialRating: number | null;
}

export interface ScoreBreakdownRow {
  dimension: keyof SupplierScoreWeights;
  label: string;
  rating: number | null; // 0–10
  weight: number; // configured weight
  /** Points contributed to the final 0–100 score (after renormalization). */
  contribution: number | null;
}

export interface SupplierScoreResult {
  score: number | null; // null if no ratings at all
  breakdown: ScoreBreakdownRow[];
  ratedWeightSum: number;
  totalWeightSum: number;
}

const DIMENSIONS: {
  key: keyof SupplierScoreWeights;
  field: keyof SupplierRatings;
  label: string;
}[] = [
  { key: "quality", field: "qualityRating", label: "Quality" },
  { key: "pricing", field: "pricingRating", label: "Pricing" },
  { key: "delivery", field: "deliveryRating", label: "Delivery Reliability" },
  {
    key: "responsiveness",
    field: "responsivenessRating",
    label: "Responsiveness",
  },
  { key: "technical", field: "technicalRating", label: "Technical Support" },
  {
    key: "commercial",
    field: "commercialRating",
    label: "Commercial Relationship",
  },
];

function round1(v: number): number {
  return Math.round(v * 10) / 10;
}

export function calculateSupplierScore(
  ratings: SupplierRatings,
  weights: SupplierScoreWeights
): SupplierScoreResult {
  const totalWeightSum = DIMENSIONS.reduce((s, d) => s + weights[d.key], 0);
  const rated = DIMENSIONS.filter((d) => ratings[d.field] != null);
  const ratedWeightSum = rated.reduce((s, d) => s + weights[d.key], 0);

  if (rated.length === 0 || ratedWeightSum === 0) {
    return {
      score: null,
      breakdown: DIMENSIONS.map((d) => ({
        dimension: d.key,
        label: d.label,
        rating: null,
        weight: weights[d.key],
        contribution: null,
      })),
      ratedWeightSum: 0,
      totalWeightSum,
    };
  }

  const factor = 100 / ratedWeightSum;
  let score = 0;
  const breakdown: ScoreBreakdownRow[] = DIMENSIONS.map((d) => {
    const rating = ratings[d.field];
    if (rating == null) {
      return {
        dimension: d.key,
        label: d.label,
        rating: null,
        weight: weights[d.key],
        contribution: null,
      };
    }
    const contribution = (weights[d.key] * (rating / 10)) * factor;
    score += contribution;
    return {
      dimension: d.key,
      label: d.label,
      rating,
      weight: weights[d.key],
      contribution: round1(contribution),
    };
  });

  return {
    score: round1(score),
    breakdown,
    ratedWeightSum,
    totalWeightSum,
  };
}
