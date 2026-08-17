/**
 * Configurable business parameters.
 *
 * These are the DEFAULTS. Live values are stored as JSON in the `Setting`
 * table and can be edited on the Settings page; src/server/settings.ts merges
 * stored values over these defaults. Nothing else in the codebase may
 * hard-code a weight or threshold.
 */

export const SETTING_KEYS = {
  supplierScoreWeights: "supplierScoreWeights",
  offerScoreWeights: "offerScoreWeights",
  recommendationConfig: "recommendationConfig",
} as const;

/** Weights (must sum to 100) applied to supplier ratings (0–10 scale). */
export interface SupplierScoreWeights {
  quality: number;
  pricing: number;
  delivery: number;
  responsiveness: number;
  technical: number;
  commercial: number;
}

export const DEFAULT_SUPPLIER_SCORE_WEIGHTS: SupplierScoreWeights = {
  quality: 25,
  pricing: 20,
  delivery: 20,
  responsiveness: 15,
  technical: 10,
  commercial: 10,
};

/** Weights (must sum to 100) for the "Best Overall Offer" score. */
export interface OfferScoreWeights {
  price: number; // lower normalized price is better
  paymentTerms: number; // more payment days is better
  leadTime: number; // shorter lead time is better
  supplierScore: number; // higher supplier score is better
}

export const DEFAULT_OFFER_SCORE_WEIGHTS: OfferScoreWeights = {
  price: 50,
  paymentTerms: 15,
  leadTime: 15,
  supplierScore: 20,
};

/**
 * Thresholds and rule points for the risk + recommendation engines.
 * Every value is documented in docs/procurement-rules.md.
 */
export interface RecommendationConfig {
  /** Stockout earlier than leadTime × this factor ⇒ URGENT territory. */
  urgentStockoutFactor: number;
  /** Coverage above reorderDays × this factor ⇒ comfortable zone. */
  monitorFactor: number;
  /** Latest price more than this % above target ⇒ negotiate / wait. */
  negotiateThresholdPct: number;
  /** Latest price more than this % above 3-month average ⇒ price alert. */
  priceSpikeThresholdPct: number;
  /** Quotations older than this many days count as stale. */
  quotationStaleDays: number;
  /** Forecast more than this % above average consumption ⇒ demand surge. */
  forecastSurgePct: number;
  /** Points contributed by deterministic risk rules. */
  riskPoints: {
    stockoutBelowSafety: number; // stockout inside lead time AND already below safety stock
    stockoutInsideLead: number; // projected stockout before a new order could arrive
    belowSafety: number; // below safety stock now, or dips below it inside lead time
    belowReorder: number; // inventory position below reorder point
    noDemandData: number; // consumption unknown — coverage not computable
    priceTrend: number; // latest price above 3-month average by spike threshold
    aboveTarget: number; // latest price above target by negotiate threshold
    forecastSurge: number; // forecast demand well above average consumption
    singleSupplier: number; // only one approved supplier
    weakSupplier: number; // preferred supplier score below 60
    intelHigh: number; // active HIGH market-intelligence alert
    intelCritical: number; // active CRITICAL market-intelligence alert
    intelCap: number; // maximum total intel contribution
  };
  /** Score thresholds mapping risk points to levels. */
  riskLevels: {
    critical: number;
    high: number;
    medium: number;
  };
}

export const DEFAULT_RECOMMENDATION_CONFIG: RecommendationConfig = {
  urgentStockoutFactor: 0.5,
  monitorFactor: 1.5,
  negotiateThresholdPct: 3,
  priceSpikeThresholdPct: 5,
  quotationStaleDays: 45,
  forecastSurgePct: 15,
  riskPoints: {
    stockoutBelowSafety: 60,
    stockoutInsideLead: 45,
    belowSafety: 30,
    belowReorder: 18,
    noDemandData: 25,
    priceTrend: 10,
    aboveTarget: 8,
    forecastSurge: 8,
    singleSupplier: 12,
    weakSupplier: 8,
    intelHigh: 10,
    intelCritical: 15,
    intelCap: 25,
  },
  riskLevels: {
    critical: 65,
    high: 40,
    medium: 20,
  },
};
