import type { Metadata } from "next";
import { PageHeader } from "@/components/ui/page-header";
import { prisma } from "@/lib/prisma";
import { ActionsClient, type ActionRow } from "./actions-client";
import type { ActionPriority, ActionStatus, ActionType } from "@/domain/enums";

export const metadata: Metadata = { title: "Action Center" };

export default async function ActionsPage() {
  const now = new Date();
  const [actions, materials, suppliers, quotations] = await Promise.all([
    prisma.actionItem.findMany({
      include: {
        material: { select: { id: true, name: true } },
        supplier: { select: { id: true, name: true } },
        quotation: { select: { id: true, quotationNumber: true } },
      },
      orderBy: [{ dueDate: "asc" }, { createdAt: "desc" }],
    }),
    prisma.material.findMany({
      select: { id: true, name: true },
      orderBy: { name: "asc" },
    }),
    prisma.supplier.findMany({
      select: { id: true, name: true },
      orderBy: { name: "asc" },
    }),
    prisma.quotation.findMany({
      select: { id: true, quotationNumber: true, material: { select: { name: true } } },
      orderBy: { quotationDate: "desc" },
      take: 100,
    }),
  ]);

  const rows: ActionRow[] = actions.map((a) => ({
    id: a.id,
    title: a.title,
    materialId: a.material?.id ?? null,
    materialName: a.material?.name ?? null,
    supplierId: a.supplier?.id ?? null,
    supplierName: a.supplier?.name ?? null,
    quotationId: a.quotation?.id ?? null,
    quotationNumber: a.quotation?.quotationNumber ?? null,
    actionType: a.actionType as ActionType,
    owner: a.owner,
    dueDate: a.dueDate?.toISOString() ?? null,
    priority: a.priority as ActionPriority,
    status: a.status as ActionStatus,
    notes: a.notes,
    isOverdue:
      a.dueDate != null &&
      a.dueDate.getTime() < now.getTime() &&
      !["COMPLETED", "CANCELLED"].includes(a.status),
    isSample: a.isSample,
  }));

  return (
    <div>
      <PageHeader
        title="Action Center"
        description="Procurement tasks — RFQs, negotiations, follow-ups. Overdue items are flagged."
      />
      <ActionsClient
        rows={rows}
        materials={materials}
        suppliers={suppliers}
        quotations={quotations.map((q) => ({
          id: q.id,
          name: `${q.quotationNumber} (${q.material.name})`,
        }))}
      />
    </div>
  );
}
