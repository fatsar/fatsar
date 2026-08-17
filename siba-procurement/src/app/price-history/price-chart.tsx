"use client";

import { useMemo } from "react";
import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { fmtDate, fmtPrice } from "@/lib/format";

/**
 * Price history line chart.
 * Follows the validated viz spec: 2px lines, ≥8px markers with a 2px
 * surface ring, hairline solid grid, crosshair tooltip, legend for ≥2 series,
 * text in ink tokens (never series colors). Colors are assigned to suppliers
 * in a FIXED order (the material's full supplier list), so filtering never
 * repaints a surviving series.
 */

const SERIES_COLORS = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100"];
const SURFACE = "#fcfcfb";
const GRID = "#e1e0d9";
const AXIS_INK = "#86847e";
const TARGET_INK = "#4f4e4b";

export interface PricePointDto {
  ts: number; // epoch ms
  supplier: string;
  price: number;
}

export function PriceChart({
  points,
  supplierOrder,
  targetPrice,
  currency,
  unit,
}: {
  points: PricePointDto[];
  /** Full fixed supplier order for this material — color assignment base. */
  supplierOrder: string[];
  targetPrice: number | null;
  currency: string;
  unit: string;
}) {
  const visibleSuppliers = useMemo(
    () => supplierOrder.filter((s) => points.some((p) => p.supplier === s)),
    [points, supplierOrder]
  );

  const colorFor = (supplier: string) =>
    SERIES_COLORS[supplierOrder.indexOf(supplier) % SERIES_COLORS.length];

  const data = useMemo(() => {
    const byTs = new Map<number, Record<string, number>>();
    for (const p of points) {
      const row = byTs.get(p.ts) ?? {};
      row[p.supplier] = p.price;
      byTs.set(p.ts, row);
    }
    return [...byTs.entries()]
      .sort((a, b) => a[0] - b[0])
      .map(([ts, row]) => ({ ts, ...row }));
  }, [points]);

  if (points.length === 0) {
    return (
      <p className="py-12 text-center text-[13px] text-ink-muted">
        No price points in the selected window.
      </p>
    );
  }

  const prices = points.map((p) => p.price).concat(targetPrice != null ? [targetPrice] : []);
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  const pad = (max - min || max * 0.1 || 1) * 0.12;

  return (
    <div>
      {visibleSuppliers.length >= 2 ? (
        <div className="flex flex-wrap gap-x-4 gap-y-1 px-1 pb-2">
          {visibleSuppliers.map((s) => (
            <span key={s} className="inline-flex items-center gap-1.5 text-[12.5px] text-ink-secondary">
              <span
                className="inline-block h-[3px] w-4 rounded-full"
                style={{ backgroundColor: colorFor(s) }}
              />
              {s}
            </span>
          ))}
          {targetPrice != null ? (
            <span className="inline-flex items-center gap-1.5 text-[12.5px] text-ink-secondary">
              <span className="inline-block h-0 w-4 border-t-2 border-dashed" style={{ borderColor: TARGET_INK }} />
              Target
            </span>
          ) : null}
        </div>
      ) : null}
      <div className="h-72 w-full lg:h-80">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 8, right: 16, bottom: 4, left: 4 }}>
            <CartesianGrid stroke={GRID} strokeWidth={1} vertical={false} />
            <XAxis
              dataKey="ts"
              type="number"
              scale="time"
              domain={["dataMin", "dataMax"]}
              tickFormatter={(ts: number) =>
                new Date(ts).toLocaleDateString("en-GB", { month: "short", year: "2-digit" })
              }
              tick={{ fill: AXIS_INK, fontSize: 11.5 }}
              tickLine={false}
              axisLine={{ stroke: GRID }}
              tickCount={7}
            />
            <YAxis
              domain={[Math.max(0, min - pad), max + pad]}
              tickFormatter={(v: number) => fmtPrice(v)}
              tick={{ fill: AXIS_INK, fontSize: 11.5 }}
              tickLine={false}
              axisLine={false}
              width={56}
            />
            <Tooltip
              cursor={{ stroke: AXIS_INK, strokeWidth: 1 }}
              content={({ active, payload, label }) => {
                if (!active || !payload?.length) return null;
                return (
                  <div className="rounded-md border border-line bg-surface px-3 py-2 text-[12.5px] shadow-md">
                    <p className="mb-1 font-semibold text-ink">{fmtDate(new Date(label as number))}</p>
                    {payload
                      .filter((e) => e.value != null)
                      .map((entry) => (
                        <p key={String(entry.dataKey)} className="flex items-center gap-1.5 text-ink-secondary">
                          <span
                            className="inline-block h-2 w-2 rounded-full"
                            style={{ backgroundColor: entry.color }}
                          />
                          {String(entry.dataKey)}:{" "}
                          <span className="tnum font-medium text-ink">
                            {fmtPrice(entry.value as number, currency, unit)}
                          </span>
                        </p>
                      ))}
                  </div>
                );
              }}
            />
            {targetPrice != null ? (
              <ReferenceLine
                y={targetPrice}
                stroke={TARGET_INK}
                strokeDasharray="5 4"
                strokeWidth={1.5}
                label={{
                  value: `Target ${fmtPrice(targetPrice)}`,
                  position: "insideBottomRight",
                  fill: TARGET_INK,
                  fontSize: 11.5,
                }}
              />
            ) : null}
            {visibleSuppliers.map((s) => (
              <Line
                key={s}
                dataKey={s}
                type="linear"
                stroke={colorFor(s)}
                strokeWidth={2}
                strokeLinecap="round"
                strokeLinejoin="round"
                connectNulls
                dot={{ r: 4, fill: colorFor(s), stroke: SURFACE, strokeWidth: 2 }}
                activeDot={{ r: 5, fill: colorFor(s), stroke: SURFACE, strokeWidth: 2 }}
                isAnimationActive={false}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
