"use client";

import { useMemo, useState } from "react";
import { DataTable, type ColumnDef } from "@/components/ui/data-table";
import { InactiveBadge, SampleBadge } from "@/components/domain-badges";

export interface SupplierRow {
  id: string;
  code: string;
  name: string;
  country: string;
  city: string | null;
  materials: string;
  materialCount: number;
  score: number | null;
  paymentTerms: string | null;
  normalLeadTimeDays: number | null;
  quotationCount: number;
  isActive: boolean;
  isSample: boolean;
}

export function SuppliersTable({ rows }: { rows: SupplierRow[] }) {
  const [showInactive, setShowInactive] = useState(false);
  const filtered = useMemo(
    () => rows.filter((r) => showInactive || r.isActive),
    [rows, showInactive]
  );

  const columns: ColumnDef<SupplierRow>[] = [
    {
      key: "name",
      header: "Supplier",
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
      key: "country",
      header: "Country",
      sortValue: (r) => r.country,
      cell: (r) => (
        <span className="text-ink-secondary">
          {r.country}
          {r.city ? `, ${r.city}` : ""}
        </span>
      ),
    },
    {
      key: "materials",
      header: "Materials supplied",
      hideOnMobile: true,
      sortValue: (r) => r.materialCount,
      cell: (r) => (
        <span className="block max-w-72 truncate text-ink-secondary" title={r.materials}>
          {r.materials || "—"}
        </span>
      ),
    },
    {
      key: "score",
      header: "Score",
      align: "right",
      sortValue: (r) => r.score,
      cell: (r) =>
        r.score != null ? (
          <span
            className={
              "tnum font-semibold " +
              (r.score >= 75
                ? "text-ok-ink"
                : r.score >= 60
                  ? "text-warn-ink"
                  : "text-crit-ink")
            }
          >
            {r.score.toFixed(0)}
            <span className="font-normal text-ink-muted">/100</span>
          </span>
        ) : (
          <span className="text-ink-muted">—</span>
        ),
    },
    {
      key: "terms",
      header: "Payment terms",
      hideOnMobile: true,
      sortValue: (r) => r.paymentTerms,
      cell: (r) => <span className="text-ink-secondary">{r.paymentTerms ?? "—"}</span>,
    },
    {
      key: "lead",
      header: "Lead time",
      align: "right",
      hideOnMobile: true,
      sortValue: (r) => r.normalLeadTimeDays,
      cell: (r) => (
        <span className="tnum">
          {r.normalLeadTimeDays != null ? `${r.normalLeadTimeDays} d` : "—"}
        </span>
      ),
    },
    {
      key: "quotes",
      header: "Quotations",
      align: "right",
      hideOnMobile: true,
      sortValue: (r) => r.quotationCount,
      cell: (r) => <span className="tnum">{r.quotationCount}</span>,
    },
  ];

  return (
    <DataTable
      rows={filtered}
      columns={columns}
      getRowKey={(r) => r.id}
      searchText={(r) => `${r.code} ${r.name} ${r.country} ${r.materials}`}
      searchPlaceholder="Filter suppliers… (e.g. Hoshine, China)"
      initialSort="score"
      initialSortDir="desc"
      rowHref={(r) => `/suppliers/${r.id}`}
      dimRow={(r) => !r.isActive}
      pageSize={20}
      toolbar={
        <label className="flex items-center gap-1.5 text-[12.5px] text-ink-secondary">
          <input
            type="checkbox"
            checked={showInactive}
            onChange={(e) => setShowInactive(e.target.checked)}
          />
          Show inactive
        </label>
      }
    />
  );
}
