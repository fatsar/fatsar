import { notFound } from "next/navigation";
import type { Metadata } from "next";
import { PageHeader } from "@/components/ui/page-header";
import { prisma } from "@/lib/prisma";
import { fmtDateInput } from "@/lib/format";
import { updateMaterial } from "@/server/actions/materials";
import { MaterialForm, type MaterialFormInitial } from "../../material-form";

export const metadata: Metadata = { title: "Edit material" };

export default async function EditMaterialPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const [material, suppliers] = await Promise.all([
    prisma.material.findUnique({
      where: { id },
      include: { suppliers: { select: { supplierId: true } } },
    }),
    prisma.supplier.findMany({
      select: { id: true, name: true, code: true },
      orderBy: { name: "asc" },
    }),
  ]);
  if (!material) notFound();

  const initial: MaterialFormInitial = {
    code: material.code,
    name: material.name,
    category: material.category,
    unit: material.unit,
    specification: material.specification ?? "",
    preferredSupplierId: material.preferredSupplierId ?? "",
    avgMonthlyConsumption: material.avgMonthlyConsumption?.toString() ?? "",
    avgDailyConsumption: material.avgDailyConsumption?.toString() ?? "",
    forecastMonthlyDemand: material.forecastMonthlyDemand?.toString() ?? "",
    currentStock: material.currentStock.toString(),
    reservedStock: material.reservedStock.toString(),
    safetyStock: material.safetyStock.toString(),
    minimumStock: material.minimumStock.toString(),
    leadTimeDays: material.leadTimeDays?.toString() ?? "",
    openPurchaseQty: material.openPurchaseQty.toString(),
    inboundQty: material.inboundQty.toString(),
    inboundEta: fmtDateInput(material.inboundEta),
    lastPurchasePrice: material.lastPurchasePrice?.toString() ?? "",
    lastPurchaseDate: fmtDateInput(material.lastPurchaseDate),
    targetPrice: material.targetPrice?.toString() ?? "",
    currency: material.currency,
    incoterm: material.incoterm ?? "",
    notes: material.notes ?? "",
    isActive: material.isActive,
    approvedSupplierIds: material.suppliers.map((s) => s.supplierId),
  };

  const action = updateMaterial.bind(null, id);

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        title={`Edit ${material.name}`}
        description={`${material.code} — stock, consumption and pricing feed the recommendation engine directly.`}
      />
      <MaterialForm
        action={action}
        suppliers={suppliers}
        initial={initial}
        submitLabel="Save changes"
      />
    </div>
  );
}
