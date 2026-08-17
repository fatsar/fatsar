"use client";

import { useMemo, useState } from "react";
import { DataTable, type ColumnDef } from "@/components/ui/data-table";
import { Select } from "@/components/ui/form";
import { RecommendationBadge, RiskBadge } from "@/components/domain-badges";
import {
  RECOMMENDATION_LABELS,
  RECOMMENDATIONS,
  RISK_LEVELS,
  type Recommendation,
  type RiskLevel,
} from "@/domain/enums";
import { fmtDays, fmtQty } from "@/lib/format";

export interface PlanRow {
  id: string;
  name: string;
  code: string;
  unit: string;
  usableStock: number;
  dailyDemand: number | null;
  coverageDays: number | null;
  projectedCoverageDays: number | null;
  belowSafetyDay: number | null;
  reorderPointDays: number | null;
  inventoryPosition: number;
  reorderPointQty: number | null;
  leadTimeDays: number | null;
  risk: RiskLevel;
  riskScore: number;
  recommendation: Recommendation;
  confidence: number;
  topReason: string;
  priorityIndex: number;
}

export function PlanTable({ rows }: { rows: PlanRow[] }) {
  const [risk, setRisk] = useState("");
  const [rec, setRec] = useState("");

  const filtered = useMemo(
    () =>
      rows.filter(
        (r) => (risk === "" || r.risk === risk) && (rec === "" || r.recommendation === rec)
      ),
    [rows, risk, rec]
  );

  const columns: ColumnDef<PlanRow>[] = [
    {
      key: "priority",
      header: "#",
      align: "right",
      sortValue: (r) => r.priorityIndex,
      cell: (r) => <span className="tnum text-ink-muted">{r.priorityIndex + 1}</span>,
    },
    {
      key: "material",
      header: "Material",
      sortValue: (r) => r.name,
      cell: (r) => (
        <div title={r.topReason}>
          <span className="font-semibold">{r.name}</span>
          <span className="block text-[12px] text-ink-muted">{r.code}</span>
        </div>
      ),
    },
    {
      key: "stock",
      header: "Usable",
      align: "right",
      hideOnMobile: true,
      sortValue: (r) => r.usableStock,
      cell: (r) => <span className="tnum">{fmtQty(r.usableStock, r.unit)}</span>,
    },
    {
      key: "daily",
      header: "Daily use",
      align: "right",
      hideOnMobile: true,
      sortValue: (r) => r.dailyDemand,
      cell: (r) => <span className="tnum">{fmtQty(r.dailyDemand, `${r.unit}/d`)}</span>,
    },
    {
      key: "coverage",
      header: "Coverage",
      align: "right",
      sortValue: (r) => r.coverageDays,
      cell: (r) => <span className="tnum font-medium">{fmtDays(r.coverageDays)}</span>,
    },
    {
      key: "projected",
      header: "Projected",
      align: "right",
      hideOnMobile: true,
      sortValue: (r) => r.projectedCoverageDays,
      cell: (r) => (
        <span className="tnum">
          {r.projectedCoverageDays != null
            ? fmtDays(r.projectedCoverageDays)
            : r.dailyDemand != null
              ? "> 365 d"
              : "—"}
        </span>
      ),
    },
    {
      key: "reorder",
      header: "Reorder point",
      align: "right",
      hideOnMobile: true,
      sortValue: (r) => r.reorderPointQty,
      cell: (r) => (
        <span className="tnum">
          {r.reorderPointQty != null ? (
            <>
              {fmtQty(r.reorderPointQty, r.unit)}
              <span className="block text-[12px] text-ink-muted">
                pos. {fmtQty(r.inventoryPosition, r.unit)}
              </span>
            </>
          ) : (
            "—"
          )}
        </span>
      ),
    },
    {
      key: "lead",
      header: "Lead",
      align: "right",
      hideOnMobile: true,
      sortValue: (r) => r.leadTimeDays,
      cell: (r) => (
        <span className="tnum">{r.leadTimeDays != null ? `${r.leadTimeDays} d` : "—"}</span>
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
      sortValue: (r) => r.priorityIndex,
      cell: (r) => (
        <div>
          <RecommendationBadge value={r.recommendation} />
          <span className="block pt-0.5 text-[12px] text-ink-muted">
            confidence {r.confidence}%
          </span>
        </div>
      ),
    },
  ];

  return (
    <DataTable
      rows={filtered}
      columns={columns}
      getRowKey={(r) => r.id}
      searchText={(r) => `${r.name} ${r.code}`}
      searchPlaceholder="Filter materials…"
      initialSort="priority"
      rowHref={(r) => `/materials/${r.id}`}
      pageSize={25}
      toolbar={
        <>
          <Select
            aria-label="Filter by risk"
            value={risk}
            onChange={(e) => setRisk(e.target.value)}
            className="h-8 w-36 text-[13px]"
          >
            <option value="">All risk levels</option>
            {RISK_LEVELS.map((l) => (
              <option key={l} value={l}>
                {l}
              </option>
            ))}
          </Select>
          <Select
            aria-label="Filter by recommendation"
            value={rec}
            onChange={(e) => setRec(e.target.value)}
            className="h-8 w-48 text-[13px]"
          >
            <option value="">All recommendations</option>
            {RECOMMENDATIONS.map((r) => (
              <option key={r} value={r}>
                {RECOMMENDATION_LABELS[r]}
              </option>
            ))}
          </Select>
        </>
      }
    />
  );
}
