import type { Metadata } from "next";
import { PageHeader } from "@/components/ui/page-header";
import { prisma } from "@/lib/prisma";
import { createQuotation } from "@/server/actions/quotations";
import { QuotationForm } from "../quotation-form";

export const metadata: Metadata = { title: "New quotation" };

export default async function NewQuotationPage({
  searchParams,
}: {
  searchParams: Promise<{ materialId?: string; supplierId?: string }>;
}) {
  const { materialId, supplierId } = await searchParams;
  const [materials, suppliers] = await Promise.all([
    prisma.material.findMany({
      where: { isActive: true },
      select: { id: true, name: true, unit: true },
      orderBy: { name: "asc" },
    }),
    prisma.supplier.findMany({
      where: { isActive: true },
      select: { id: true, name: true },
      orderBy: { name: "asc" },
    }),
  ]);

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        title="New quotation"
        description="Record a supplier offer. Multiple offers per material are compared on the comparison screen."
      />
      <QuotationForm
        action={createQuotation}
        materials={materials}
        suppliers={suppliers}
        initial={{ materialId: materialId ?? "", supplierId: supplierId ?? "" }}
        submitLabel="Save quotation"
      />
    </div>
  );
}
