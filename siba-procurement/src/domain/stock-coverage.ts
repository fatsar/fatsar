/**
 * Stock coverage & replenishment mathematics. Pure functions — no database.
 * Formulas are documented in docs/procurement-rules.md.
 */

export interface StockInput {
  currentStock: number;
  reservedStock: number;
  safetyStock: number;
  /** Explicit daily consumption; falls back to monthly / 30. */
  avgDailyConsumption: number | null;
  avgMonthlyConsumption: number | null;
  /** If set and higher than average, the projection uses forecast demand. */
  forecastMonthlyDemand: number | null;
  leadTimeDays: number | null;
  /** Ordered but without a confirmed ETA — assumed to arrive at leadTimeDays. */
  openPurchaseQty: number;
  /** Shipped / confirmed quantity. */
  inboundQty: number;
  /** Days from now until the inbound arrives (negative/0 = already arriving). */
  inboundEtaDays: number | null;
}

export type DemandSource = "explicit" | "monthly" | "forecast" | "none";

export interface CoverageResult {
  usableStock: number;
  /** Average daily demand used for the BASIC coverage figure. */
  dailyDemand: number | null;
  /** Daily demand used for the projection (max of average and forecast). */
  projectedDailyDemand: number | null;
  demandSource: DemandSource;
  /** Basic coverage: usableStock / dailyDemand. */
  coverageDays: number | null;
  safetyStockDays: number | null;
  /** First simulated day stock reaches zero. null = beyond horizon. */
  projectedStockoutDay: number | null;
  /** First simulated day stock drops below safety stock. null = never. */
  projectedBelowSafetyDay: number | null;
  /** Same as projectedStockoutDay but reads as "days of cover incl. inbound". */
  projectedCoverageDays: number | null;
  /** usable + open PO + inbound (classic inventory position). */
  inventoryPosition: number;
  /** dailyDemand × leadTimeDays + safetyStock. */
  reorderPointQty: number | null;
  /** leadTimeDays + safetyStockDays. */
  reorderPointDays: number | null;
  /** inventoryPosition < reorderPointQty. */
  belowReorderPoint: boolean | null;
  horizonDays: number;
}

export const PROJECTION_HORIZON_DAYS = 365;

function round1(v: number): number {
  return Math.round(v * 10) / 10;
}

export function calculateCoverage(input: StockInput): CoverageResult {
  const usableStock = Math.max(input.currentStock - input.reservedStock, 0);

  let dailyDemand: number | null = null;
  let demandSource: DemandSource = "none";
  if (input.avgDailyConsumption != null && input.avgDailyConsumption > 0) {
    dailyDemand = input.avgDailyConsumption;
    demandSource = "explicit";
  } else if (
    input.avgMonthlyConsumption != null &&
    input.avgMonthlyConsumption > 0
  ) {
    dailyDemand = input.avgMonthlyConsumption / 30;
    demandSource = "monthly";
  } else if (
    input.forecastMonthlyDemand != null &&
    input.forecastMonthlyDemand > 0
  ) {
    dailyDemand = input.forecastMonthlyDemand / 30;
    demandSource = "forecast";
  }

  // Projection is conservative: if a forecast exists and exceeds the average,
  // consume at the forecast rate.
  const forecastDaily =
    input.forecastMonthlyDemand != null && input.forecastMonthlyDemand > 0
      ? input.forecastMonthlyDemand / 30
      : null;
  const projectedDailyDemand =
    dailyDemand == null
      ? null
      : forecastDaily != null
        ? Math.max(dailyDemand, forecastDaily)
        : dailyDemand;

  const inventoryPosition =
    usableStock + input.openPurchaseQty + input.inboundQty;

  const coverageDays =
    dailyDemand != null ? round1(usableStock / dailyDemand) : null;
  const safetyStockDays =
    dailyDemand != null ? round1(input.safetyStock / dailyDemand) : null;

  const reorderPointQty =
    dailyDemand != null && input.leadTimeDays != null
      ? dailyDemand * input.leadTimeDays + input.safetyStock
      : null;
  const reorderPointDays =
    reorderPointQty != null && dailyDemand != null
      ? round1(input.leadTimeDays! + input.safetyStock / dailyDemand)
      : null;
  const belowReorderPoint =
    reorderPointQty != null ? inventoryPosition < reorderPointQty : null;

  // ---- day-by-day projection --------------------------------------------
  // Arrivals are credited at the START of their day, consumption at the END.
  // The open PO (no confirmed ETA) is assumed to arrive after one lead time.
  let projectedStockoutDay: number | null = null;
  let projectedBelowSafetyDay: number | null = null;

  if (projectedDailyDemand != null) {
    const inboundDay =
      input.inboundQty > 0
        ? Math.max(1, Math.ceil(input.inboundEtaDays ?? 1))
        : null;
    const openPoDay =
      input.openPurchaseQty > 0 && input.leadTimeDays != null
        ? Math.max(1, input.leadTimeDays)
        : null;

    let stock = usableStock;
    for (let day = 1; day <= PROJECTION_HORIZON_DAYS; day++) {
      if (inboundDay === day) stock += input.inboundQty;
      if (openPoDay === day) stock += input.openPurchaseQty;
      stock -= projectedDailyDemand;
      if (projectedBelowSafetyDay == null && stock < input.safetyStock) {
        projectedBelowSafetyDay = day;
      }
      if (stock <= 0) {
        projectedStockoutDay = day;
        break;
      }
    }
  }

  return {
    usableStock,
    dailyDemand,
    projectedDailyDemand,
    demandSource,
    coverageDays,
    safetyStockDays,
    projectedStockoutDay,
    projectedBelowSafetyDay,
    projectedCoverageDays: projectedStockoutDay,
    inventoryPosition,
    reorderPointQty,
    reorderPointDays,
    belowReorderPoint,
    horizonDays: PROJECTION_HORIZON_DAYS,
  };
}
