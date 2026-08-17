/**
 * Deterministic price-history statistics over a homogeneous list of price
 * points (same material, same currency, prices already normalized to the
 * material's base unit). The caller is responsible for filtering.
 */

export interface PricePoint {
  date: Date;
  price: number; // normalized price per material base unit
  supplierName?: string;
}

export interface PriceStats {
  latest: number | null;
  latestDate: Date | null;
  previous: number | null;
  previousDate: Date | null;
  /** (latest − previous) / previous × 100, rounded to 2 decimals. */
  changePct: number | null;
  avg3m: number | null;
  avg6m: number | null;
  avg12m: number | null;
  min12m: number | null;
  max12m: number | null;
  targetPrice: number | null;
  /** (latest − target) / target × 100, rounded to 2 decimals. */
  diffToTargetPct: number | null;
  sampleCount: number;
}

function round2(v: number): number {
  return Math.round(v * 100) / 100;
}
function round4(v: number): number {
  return Math.round(v * 10000) / 10000;
}

function avg(points: PricePoint[]): number | null {
  if (points.length === 0) return null;
  return round4(points.reduce((s, p) => s + p.price, 0) / points.length);
}

export function calculatePriceStats(
  points: PricePoint[],
  targetPrice: number | null,
  now: Date = new Date()
): PriceStats {
  const sorted = [...points].sort((a, b) => b.date.getTime() - a.date.getTime());
  const latest = sorted[0] ?? null;
  const previous = sorted[1] ?? null;

  const within = (days: number) =>
    sorted.filter(
      (p) => now.getTime() - p.date.getTime() <= days * 86_400_000
    );

  const w12 = within(365);

  const changePct =
    latest && previous && previous.price !== 0
      ? round2(((latest.price - previous.price) / previous.price) * 100)
      : null;
  const diffToTargetPct =
    latest && targetPrice != null && targetPrice !== 0
      ? round2(((latest.price - targetPrice) / targetPrice) * 100)
      : null;

  return {
    latest: latest?.price ?? null,
    latestDate: latest?.date ?? null,
    previous: previous?.price ?? null,
    previousDate: previous?.date ?? null,
    changePct,
    avg3m: avg(within(90)),
    avg6m: avg(within(180)),
    avg12m: avg(w12),
    min12m: w12.length ? round4(Math.min(...w12.map((p) => p.price))) : null,
    max12m: w12.length ? round4(Math.max(...w12.map((p) => p.price))) : null,
    targetPrice,
    diffToTargetPct,
    sampleCount: sorted.length,
  };
}
