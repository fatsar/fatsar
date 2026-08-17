"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { DataTable, type ColumnDef } from "@/components/ui/data-table";
import { Dialog } from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { Select } from "@/components/ui/form";
import { RiskBadge, SampleBadge } from "@/components/domain-badges";
import {
  INTEL_CATEGORY_LABELS,
  type IntelCategory,
  type PriceDirection,
  type RiskLevel,
} from "@/domain/enums";
import { fmtDate, titleCase } from "@/lib/format";
import { IntelForm, type IntelFormInitial } from "./intel-form";

export interface IntelRow {
  id: string;
  date: string; // ISO
  title: string;
  category: IntelCategory;
  affectedMaterials: string;
  geography: string | null;
  summary: string;
  expectedImpact: string | null;
  priceDirection: PriceDirection;
  supplyImpact: string | null;
  riskLevel: RiskLevel;
  source: string | null;
  url: string | null;
  notes: string | null;
  isActive: boolean;
  isSample: boolean;
}

const DIRECTION_LABEL: Record<PriceDirection, string> = {
  UP: "▲ UP",
  DOWN: "▼ DOWN",
  NEUTRAL: "— NEUTRAL",
  UNCERTAIN: "? UNCERTAIN",
};

export function IntelClient({ rows }: { rows: IntelRow[] }) {
  const router = useRouter();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<IntelRow | null>(null);
  const [categoryFilter, setCategoryFilter] = useState("");
  const [activeOnly, setActiveOnly] = useState(true);

  const filtered = useMemo(
    () =>
      rows.filter(
        (r) =>
          (categoryFilter === "" || r.category === categoryFilter) &&
          (!activeOnly || r.isActive)
      ),
    [rows, categoryFilter, activeOnly]
  );

  const columns: ColumnDef<IntelRow>[] = [
    {
      key: "date",
      header: "Date",
      sortValue: (r) => r.date,
      cell: (r) => <span className="tnum whitespace-nowrap">{fmtDate(r.date)}</span>,
    },
    {
      key: "title",
      header: "Intelligence",
      sortValue: (r) => r.title,
      cell: (r) => (
        <div className="max-w-xl">
          <span className="font-semibold">{r.title}</span> {r.isSample ? <SampleBadge /> : null}
          {!r.isActive ? <Badge tone="outline">ARCHIVED</Badge> : null}
          <span className="mt-0.5 line-clamp-2 block text-[12.5px] text-ink-secondary">
            {r.summary}
          </span>
        </div>
      ),
    },
    {
      key: "category",
      header: "Category",
      hideOnMobile: true,
      sortValue: (r) => r.category,
      cell: (r) => (
        <span className="text-ink-secondary">{INTEL_CATEGORY_LABELS[r.category]}</span>
      ),
    },
    {
      key: "affected",
      header: "Affects",
      hideOnMobile: true,
      cell: (r) => (
        <span className="block max-w-48 truncate text-ink-secondary" title={r.affectedMaterials}>
          {r.affectedMaterials}
        </span>
      ),
    },
    {
      key: "direction",
      header: "Price",
      sortValue: (r) => r.priceDirection,
      cell: (r) => (
        <span
          className={
            "whitespace-nowrap text-[12.5px] font-semibold " +
            (r.priceDirection === "UP"
              ? "text-serious-ink"
              : r.priceDirection === "DOWN"
                ? "text-ok-ink"
                : "text-ink-secondary")
          }
        >
          {DIRECTION_LABEL[r.priceDirection]}
        </span>
      ),
    },
    {
      key: "supply",
      header: "Supply",
      hideOnMobile: true,
      sortValue: (r) => r.supplyImpact,
      cell: (r) => (
        <span className="text-ink-secondary">
          {r.supplyImpact ? titleCase(r.supplyImpact) : "—"}
        </span>
      ),
    },
    {
      key: "risk",
      header: "Risk",
      sortValue: (r) => ["CRITICAL", "HIGH", "MEDIUM", "LOW"].indexOf(r.riskLevel),
      cell: (r) => <RiskBadge level={r.riskLevel} />,
    },
  ];

  const initial: IntelFormInitial | undefined = editing
    ? {
        id: editing.id,
        date: editing.date.slice(0, 10),
        title: editing.title,
        category: editing.category,
        affectedMaterials: editing.affectedMaterials,
        geography: editing.geography ?? "",
        summary: editing.summary,
        expectedImpact: editing.expectedImpact ?? "",
        priceDirection: editing.priceDirection,
        supplyImpact: editing.supplyImpact ?? "",
        riskLevel: editing.riskLevel,
        source: editing.source ?? "",
        url: editing.url ?? "",
        notes: editing.notes ?? "",
        isActive: editing.isActive,
      }
    : undefined;

  return (
    <>
      <div className="mb-3 flex justify-end">
        <Button
          variant="primary"
          onClick={() => {
            setEditing(null);
            setDialogOpen(true);
          }}
        >
          <Plus size={15} /> New entry
        </Button>
      </div>
      <Card>
        <DataTable
          rows={filtered}
          columns={columns}
          getRowKey={(r) => r.id}
          searchText={(r) =>
            `${r.title} ${r.summary} ${r.affectedMaterials} ${r.geography ?? ""} ${r.category}`
          }
          searchPlaceholder="Filter intelligence… (e.g. Hormuz, freight)"
          initialSort="date"
          initialSortDir="desc"
          onRowClick={(row) => {
            setEditing(row);
            setDialogOpen(true);
          }}
          pageSize={15}
          dimRow={(r) => !r.isActive}
          toolbar={
            <>
              <Select
                aria-label="Filter by category"
                value={categoryFilter}
                onChange={(e) => setCategoryFilter(e.target.value)}
                className="h-8 w-44 text-[13px]"
              >
                <option value="">All categories</option>
                {Object.entries(INTEL_CATEGORY_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Select>
              <label className="flex items-center gap-1.5 text-[12.5px] text-ink-secondary">
                <input
                  type="checkbox"
                  checked={activeOnly}
                  onChange={(e) => setActiveOnly(e.target.checked)}
                />
                Active only
              </label>
            </>
          }
        />
      </Card>

      <Dialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        title={editing ? "Edit intelligence entry" : "New intelligence entry"}
        wide
      >
        <IntelForm
          key={editing?.id ?? "new"}
          initial={initial}
          onSaved={() => {
            setDialogOpen(false);
            router.refresh();
          }}
        />
      </Dialog>
    </>
  );
}
