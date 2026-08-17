import type { OfferScoreWeights } from "./config";

/**
 * Quotation comparison for one material — fully explainable "Best Overall
 * Offer" scoring.
 *
 * Per-dimension scores (0–100, higher = better):
 *   price          = 100 × lowestPrice / offerPrice        (lower is better)
 *   paymentTerms   = 100 × offerDays  / longestDays        (longer is better)
 *   leadTime       = 100 × shortestLead / offerLead        (shorter is better)
 *   supplierScore  = the supplier's 0–100 score
 * Overall = Σ weight_i × score_i / 100. Missing values score 0 and are
 * flagged, so incomplete offers are visibly penalized rather than silently
 * favoured. If offers are in mixed currencies the price dimension is skipped
 * (no FX in Phase 1) and its weight is excluded from the denominator.
 */

export interface ComparableOffer {
  id: string;
  supplierName: string;
  supplierScore: number | null; // 0–100
  normalizedPrice: number;
  currency: string;
  paymentTermDays: number | null;
  leadTimeDays: number | null;
  isExpired: boolean;
}

export type OfferBadge =
  | "LOWEST_PRICE"
  | "BEST_PAYMENT_TERMS"
  | "SHORTEST_LEAD_TIME"
  | "BEST_SUPPLIER_SCORE"
  | "BEST_OVERALL";

export interface OfferDimensionScore {
  dimension: keyof OfferScoreWeights;
  label: string;
  rawValue: number | null;
  score: number | null; // 0–100
  weight: number;
  weightedPoints: number; // contribution to overall
  missing: boolean;
}

export interface ScoredOffer extends ComparableOffer {
  overallScore: number | null;
  dimensions: OfferDimensionScore[];
  badges: OfferBadge[];
}

export interface ComparisonResult {
  offers: ScoredOffer[];
  warnings: string[];
  priceComparable: boolean;
}

function round1(v: number): number {
  return Math.round(v * 10) / 10;
}

export function compareOffers(
  offers: ComparableOffer[],
  weights: OfferScoreWeights
): ComparisonResult {
  const warnings: string[] = [];
  if (offers.length === 0) {
    return { offers: [], warnings, priceComparable: true };
  }

  const currencies = [...new Set(offers.map((o) => o.currency))];
  const priceComparable = currencies.length === 1;
  if (!priceComparable) {
    warnings.push(
      `Offers are in mixed currencies (${currencies.join(", ")}). ` +
        "Price is excluded from the overall score — FX conversion is planned for a later phase."
    );
  }
  if (offers.some((o) => o.isExpired)) {
    warnings.push("Some offers are past their validity date (marked EXPIRED).");
  }

  const lowestPrice = Math.min(...offers.map((o) => o.normalizedPrice));
  const paymentDays = offers
    .map((o) => o.paymentTermDays)
    .filter((v): v is number => v != null && v > 0);
  const longestPayment = paymentDays.length ? Math.max(...paymentDays) : null;
  const leadDays = offers
    .map((o) => o.leadTimeDays)
    .filter((v): v is number => v != null && v > 0);
  const shortestLead = leadDays.length ? Math.min(...leadDays) : null;

  const activeWeightSum =
    (priceComparable ? weights.price : 0) +
    weights.paymentTerms +
    weights.leadTime +
    weights.supplierScore;

  const scored: ScoredOffer[] = offers.map((o) => {
    const dims: OfferDimensionScore[] = [];

    // price
    if (priceComparable) {
      const score =
        o.normalizedPrice > 0 ? (lowestPrice / o.normalizedPrice) * 100 : 0;
      dims.push({
        dimension: "price",
        label: "Price",
        rawValue: o.normalizedPrice,
        score: round1(score),
        weight: weights.price,
        weightedPoints: round1((score * weights.price) / 100),
        missing: false,
      });
    } else {
      dims.push({
        dimension: "price",
        label: "Price",
        rawValue: o.normalizedPrice,
        score: null,
        weight: 0,
        weightedPoints: 0,
        missing: false,
      });
    }

    // payment terms
    {
      const missing = o.paymentTermDays == null || longestPayment == null;
      const score = missing
        ? 0
        : (o.paymentTermDays! / longestPayment!) * 100;
      dims.push({
        dimension: "paymentTerms",
        label: "Payment Terms",
        rawValue: o.paymentTermDays,
        score: missing ? null : round1(score),
        weight: weights.paymentTerms,
        weightedPoints: round1((score * weights.paymentTerms) / 100),
        missing,
      });
    }

    // lead time
    {
      const missing = o.leadTimeDays == null || shortestLead == null;
      const score =
        missing || o.leadTimeDays === 0
          ? 0
          : (shortestLead! / o.leadTimeDays!) * 100;
      dims.push({
        dimension: "leadTime",
        label: "Lead Time",
        rawValue: o.leadTimeDays,
        score: missing ? null : round1(score),
        weight: weights.leadTime,
        weightedPoints: round1((score * weights.leadTime) / 100),
        missing,
      });
    }

    // supplier score
    {
      const missing = o.supplierScore == null;
      const score = missing ? 0 : o.supplierScore!;
      dims.push({
        dimension: "supplierScore",
        label: "Supplier Score",
        rawValue: o.supplierScore,
        score: missing ? null : round1(score),
        weight: weights.supplierScore,
        weightedPoints: round1((score * weights.supplierScore) / 100),
        missing,
      });
    }

    const overall =
      activeWeightSum > 0
        ? round1(
            (dims.reduce((s, d) => s + d.weightedPoints, 0) * 100) /
              activeWeightSum
          )
        : null;

    return { ...o, overallScore: overall, dimensions: dims, badges: [] };
  });

  // Badges (ties share the badge). Expired offers stay visible and scored,
  // but cannot win a badge — an offer that can no longer be accepted must
  // never be presented as the best choice. If everything is expired the
  // badges are assigned among the expired offers (with a warning above).
  const valid = scored.filter((o) => !o.isExpired);
  const badgePool = valid.length > 0 ? valid : scored;
  const withBadge = (
    badge: OfferBadge,
    value: (o: ScoredOffer) => number | null,
    best: "min" | "max"
  ) => {
    const values = badgePool
      .map((o) => value(o))
      .filter((v): v is number => v != null);
    if (values.length === 0) return;
    const target = best === "min" ? Math.min(...values) : Math.max(...values);
    for (const o of badgePool) {
      if (value(o) === target) o.badges.push(badge);
    }
  };

  if (priceComparable) {
    withBadge("LOWEST_PRICE", (o) => o.normalizedPrice, "min");
  }
  withBadge("BEST_PAYMENT_TERMS", (o) => o.paymentTermDays, "max");
  withBadge("SHORTEST_LEAD_TIME", (o) => o.leadTimeDays, "min");
  withBadge("BEST_SUPPLIER_SCORE", (o) => o.supplierScore, "max");
  withBadge("BEST_OVERALL", (o) => o.overallScore, "max");

  // Best overall first, then price.
  scored.sort(
    (a, b) =>
      (b.overallScore ?? -1) - (a.overallScore ?? -1) ||
      a.normalizedPrice - b.normalizedPrice
  );

  return { offers: scored, warnings, priceComparable };
}
