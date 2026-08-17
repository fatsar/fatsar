"use client";

import { useMemo, useState } from "react";
import { DataTable, type ColumnDef } from "@/components/ui/data-table";
import { Select } from "@/components/ui/form";
import {
  InactiveBadge,
  RecommendationBadge,
  RiskBadge,
  SampleBadge,
} from "@/components/domain-badges";
import {
  MATERIAL_CATEGORY_LABELS,
  type MaterialCategory,
  type Recommendation,
  type RiskLevel,
} from "@/domain/enums";
import { fmtDays, fmtPrice, fmtQty } from "@/lib/format";

export interface MaterialRow {
  id: string;
  code: string;
  name: string;
  category: MaterialCategory;
  unit: string;
  usableStock: number;
  coverageDays: number | null;
  projectedCoverageDays: number | null;
  risk: RiskLevel;
  riskScore: number;
  recommendation: Recommendation;
  latestPrice: number | null;
  targetPrice: number | null;
  currency: string;
  preferredSupplier: string | null;
  isActive: boolean;
  isSample: boolean;
}

export function MaterialsTable({ rows }: { rows: MaterialRow[] }) {
  const [category, setCategory] = useState<string>("");
  const [showInactive, setShowInactive] = useState(false);

  const filtered = useMemo(
    () =>
      rows.filter(
        (r) =>
          (category === "" || r.category === category) &&
          (showInactive || r.isActive)
      ),
    [rows, category, showInactive]
  );

  const columns: ColumnDef<MaterialRow>[] = [
    {
      key: "name",
      header: "Material",
      sortValue: (r) => r.name,
      cell: (r) => (
        <div>
          <span className="font-semibold">{r.name}</span>{" "}
          {r.isSample ? <SampleBadge /> : null}{" "}
          {!r.isActive ? <InactiveBadge /> : null}
          <span className="block text-[12px] text-ink-muted">{r.code}</span>
        </div>
      ),
    },
    {
      key: "category",
      header: "Category",
      sortValue: (r) => MATERIAL_CATEGORY_LABELS[r.category],
      hideOnMobile: true,
      cell: (r) => (
        <span className="text-ink-secondary">
          {MATERIAL_CATEGORY_LABELS[r.category]}
        </span>
      ),
    },
    {
      key: "stock",
      header: "Usable stock",
      align: "right",
      hideOnMobile: true,
      sortValue: (r) => r.usableStock,
      cell: (r) => (
        <span className="tnum">{fmtQty(r.usableStock, r.unit)}</span>
      ),
    },
    {
      key: "coverage",
      header: "Coverage",
      align: "right",
      sortValue: (r) => r.coverageDays,
      cell: (r) => (
        <div className="tnum">
          <span className="font-medium">{fmtDays(r.coverageDays)}</span>
          {r.projectedCoverageDays != null &&
          r.projectedCoverageDays !== r.coverageDays ? (
            <span className="block text-[12px] text-ink-muted">
              proj. {fmtDays(r.projectedCoverageDays)}
            </span>
          ) : null}
        </div>
      ),
    },
    {
      key: "risk",
      header: "Risk",
      sortValue: (r) => ["CRITICAL", "HIGH", "MEDIUM", "LOW"].indexOf(r.risk),
      cell: (r) => <RiskBadge level={r.risk} score={r.riskScore} />,
    },
    {
      key: "recommendation",
      header: "Recommendation",
      cell: (r) => <RecommendationBadge value={r.recommendation} />,
    },
    {
      key: "latest",
      header: "Latest price",
      align: "right",
      hideOnMobile: true,
      sortValue: (r) => r.latestPrice,
      cell: (r) => (
        <span className="tnum">
          {fmtPrice(r.latestPrice, r.currency, r.unit)}
        </span>
      ),
    },
    {
      key: "target",
      header: "Target",
      align: "right",
      hideOnMobile: true,
      sortValue: (r) => r.targetPrice,
      cell: (r) => (
        <span className="tnum text-ink-secondary">
          {fmtPrice(r.targetPrice, r.currency, r.unit)}
        </span>
      ),
    },
    {
      key: "supplier",
      header: "Preferred supplier",
      hideOnMobile: true,
      sortValue: (r) => r.preferredSupplier,
      cell: (r) => (
        <span className="text-ink-secondary">{r.preferredSupplier ?? "—"}</span>
      ),
    },
  ];

  return (
    <DataTable
      rows={filtered}
      columns={columns}
      getRowKey={(r) => r.id}
      searchText={(r) => `${r.code} ${r.name} ${r.category}`}
      searchPlaceholder="Filter materials… (e.g. PMDI, CP52)"
      initialSort="coverage"
      rowHref={(r) => `/materials/${r.id}`}
      dimRow={(r) => !r.isActive}
      pageSize={20}
      toolbar={
        <>
          <Select
            aria-label="Filter by category"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            className="h-8 w-44 text-[13px]"
          >
            <option value="">All categories</option>
            {Object.entries(MATERIAL_CATEGORY_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
          <label className="flex items-center gap-1.5 text-[12.5px] text-ink-secondary">
            <input
              type="checkbox"
              checked={showInactive}
              onChange={(e) => setShowInactive(e.target.checked)}
            />
            Show inactive
          </label>
        </>
      }
    />
  );
}
