"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { DataTable, type ColumnDef } from "@/components/ui/data-table";
import { Dialog } from "@/components/ui/dialog";
import { Select } from "@/components/ui/form";
import { ActionStatusBadge, PriorityBadge, SampleBadge } from "@/components/domain-badges";
import {
  ACTION_STATUS_LABELS,
  ACTION_STATUSES,
  ACTION_TYPE_LABELS,
  type ActionPriority,
  type ActionStatus,
  type ActionType,
} from "@/domain/enums";
import { fmtDate, titleCase } from "@/lib/format";
import { ActionItemForm, type ActionFormInitial } from "./action-form";

export interface ActionRow {
  id: string;
  title: string;
  materialId: string | null;
  materialName: string | null;
  supplierId: string | null;
  supplierName: string | null;
  quotationId: string | null;
  quotationNumber: string | null;
  actionType: ActionType;
  owner: string | null;
  dueDate: string | null; // ISO
  priority: ActionPriority;
  status: ActionStatus;
  notes: string | null;
  isOverdue: boolean;
  isSample: boolean;
}

export interface ActionOption {
  id: string;
  name: string;
}

export function ActionsClient({
  rows,
  materials,
  suppliers,
  quotations,
}: {
  rows: ActionRow[];
  materials: ActionOption[];
  suppliers: ActionOption[];
  quotations: ActionOption[];
}) {
  const router = useRouter();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<ActionRow | null>(null);
  const [statusFilter, setStatusFilter] = useState("");
  const [hideClosed, setHideClosed] = useState(true);

  const filtered = useMemo(
    () =>
      rows.filter(
        (r) =>
          (statusFilter === "" || r.status === statusFilter) &&
          (!hideClosed || !["COMPLETED", "CANCELLED"].includes(r.status))
      ),
    [rows, statusFilter, hideClosed]
  );

  const columns: ColumnDef<ActionRow>[] = [
    {
      key: "title",
      header: "Action",
      sortValue: (r) => r.title,
      cell: (r) => (
        <div>
          <span className="font-semibold">{r.title}</span> {r.isSample ? <SampleBadge /> : null}
          <span className="block text-[12px] text-ink-muted">
            {[r.materialName, r.supplierName, r.quotationNumber].filter(Boolean).join(" · ") || "—"}
          </span>
        </div>
      ),
    },
    {
      key: "type",
      header: "Type",
      hideOnMobile: true,
      sortValue: (r) => r.actionType,
      cell: (r) => <span className="text-ink-secondary">{ACTION_TYPE_LABELS[r.actionType]}</span>,
    },
    {
      key: "owner",
      header: "Owner",
      hideOnMobile: true,
      sortValue: (r) => r.owner,
      cell: (r) => <span className="text-ink-secondary">{r.owner ?? "—"}</span>,
    },
    {
      key: "due",
      header: "Due",
      sortValue: (r) => r.dueDate,
      cell: (r) => (
        <span
          className={
            "tnum whitespace-nowrap " + (r.isOverdue ? "font-semibold text-crit-ink" : "")
          }
        >
          {r.isOverdue ? "OVERDUE · " : ""}
          {fmtDate(r.dueDate)}
        </span>
      ),
    },
    {
      key: "priority",
      header: "Priority",
      sortValue: (r) => ["URGENT", "HIGH", "MEDIUM", "LOW"].indexOf(r.priority),
      cell: (r) => <PriorityBadge priority={r.priority} />,
    },
    {
      key: "status",
      header: "Status",
      sortValue: (r) => r.status,
      cell: (r) => <ActionStatusBadge status={r.status} />,
    },
  ];

  const initial: ActionFormInitial | undefined = editing
    ? {
        id: editing.id,
        title: editing.title,
        materialId: editing.materialId ?? "",
        supplierId: editing.supplierId ?? "",
        quotationId: editing.quotationId ?? "",
        actionType: editing.actionType,
        owner: editing.owner ?? "",
        dueDate: editing.dueDate ? editing.dueDate.slice(0, 10) : "",
        priority: editing.priority,
        status: editing.status,
        notes: editing.notes ?? "",
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
          <Plus size={15} /> New action
        </Button>
      </div>
      <Card>
        <DataTable
          rows={filtered}
          columns={columns}
          getRowKey={(r) => r.id}
          searchText={(r) =>
            `${r.title} ${r.materialName ?? ""} ${r.supplierName ?? ""} ${r.owner ?? ""} ${titleCase(r.actionType)}`
          }
          searchPlaceholder="Filter actions…"
          initialSort="due"
          onRowClick={(row) => {
            setEditing(row);
            setDialogOpen(true);
          }}
          pageSize={20}
          dimRow={(r) => ["COMPLETED", "CANCELLED"].includes(r.status)}
          toolbar={
            <>
              <Select
                aria-label="Filter by status"
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className="h-8 w-44 text-[13px]"
              >
                <option value="">All statuses</option>
                {ACTION_STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {ACTION_STATUS_LABELS[s]}
                  </option>
                ))}
              </Select>
              <label className="flex items-center gap-1.5 text-[12.5px] text-ink-secondary">
                <input
                  type="checkbox"
                  checked={hideClosed}
                  onChange={(e) => setHideClosed(e.target.checked)}
                />
                Hide completed
              </label>
              <span className="hidden text-[12px] text-ink-muted sm:inline">
                · click a row to edit
              </span>
            </>
          }
        />
      </Card>

      <Dialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        title={editing ? "Edit action" : "New action"}
        wide
      >
        <ActionItemForm
          key={editing?.id ?? "new"}
          initial={initial}
          materials={materials}
          suppliers={suppliers}
          quotations={quotations}
          onSaved={() => {
            setDialogOpen(false);
            router.refresh();
          }}
        />
      </Dialog>
    </>
  );
}
