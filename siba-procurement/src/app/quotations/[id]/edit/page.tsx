import { notFound } from "next/navigation";
import type { Metadata } from "next";
import { PageHeader } from "@/components/ui/page-header";
import { ActionButton } from "@/components/ui/action-feedback";
import { SampleBadge } from "@/components/domain-badges";
import { fmtDateInput } from "@/lib/format";
import { prisma } from "@/lib/prisma";
import { deleteQuotation, updateQuotation } from "@/server/actions/quotations";
import { QuotationForm, type QuotationFormInitial } from "../../quotation-form";

export const metadata: Metadata = { title: "Edit quotation" };

export default async function EditQuotationPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const [quotation, materials, suppliers] = await Promise.all([
    prisma.quotation.findUnique({ where: { id } }),
    prisma.material.findMany({
      select: { id: true, name: true, unit: true },
      orderBy: { name: "asc" },
    }),
    prisma.supplier.findMany({
      select: { id: true, name: true },
      orderBy: { name: "asc" },
    }),
  ]);
  if (!quotation) notFound();

  const initial: QuotationFormInitial = {
    quotationNumber: quotation.quotationNumber,
    supplierId: quotation.supplierId,
    materialId: quotation.materialId,
    quotationDate: fmtDateInput(quotation.quotationDate),
    quantity: quotation.quantity.toString(),
    quantityUnit: quotation.quantityUnit,
    containerCount: quotation.containerCount?.toString() ?? "",
    qtyPerContainer: quotation.qtyPerContainer?.toString() ?? "",
    price: quotation.price.toString(),
    currency: quotation.currency,
    priceUnit: quotation.priceUnit,
    incoterm: quotation.incoterm ?? "",
    destinationPort: quotation.destinationPort ?? "",
    paymentTerms: quotation.paymentTerms ?? "",
    paymentTermDays: quotation.paymentTermDays?.toString() ?? "",
    leadTimeDays: quotation.leadTimeDays?.toString() ?? "",
    productionLeadTimeDays: quotation.productionLeadTimeDays?.toString() ?? "",
    validUntil: fmtDateInput(quotation.validUntil),
    freightIncluded: quotation.freightIncluded,
    status: quotation.status,
    remarks: quotation.remarks ?? "",
    attachmentRef: quotation.attachmentRef ?? "",
  };

  const action = updateQuotation.bind(null, id);
  const remove = deleteQuotation.bind(null, id);

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        title={`Edit quotation ${quotation.quotationNumber}`}
        badges={quotation.isSample ? <SampleBadge /> : undefined}
        description="Correcting an offer recalculates its normalized price."
        actions={
          <ActionButton
            variant="danger"
            action={remove}
            confirmMessage={`Delete quotation ${quotation.quotationNumber}? This removes it from price history. Prefer marking it REJECTED or SUPERSEDED.`}
          >
            Delete
          </ActionButton>
        }
      />
      <QuotationForm
        action={action}
        materials={materials}
        suppliers={suppliers}
        initial={initial}
        submitLabel="Save changes"
        redirectToMaterial={false}
      />
    </div>
  );
}
