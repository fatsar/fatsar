import { notFound } from "next/navigation";
import type { Metadata } from "next";
import { PageHeader } from "@/components/ui/page-header";
import { prisma } from "@/lib/prisma";
import { updateSupplier } from "@/server/actions/suppliers";
import { SupplierForm, type SupplierFormInitial } from "../../supplier-form";

export const metadata: Metadata = { title: "Edit supplier" };

export default async function EditSupplierPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const supplier = await prisma.supplier.findUnique({ where: { id } });
  if (!supplier) notFound();

  const initial: SupplierFormInitial = {
    code: supplier.code,
    name: supplier.name,
    country: supplier.country,
    city: supplier.city ?? "",
    website: supplier.website ?? "",
    contactName: supplier.contactName ?? "",
    contactEmail: supplier.contactEmail ?? "",
    contactPhone: supplier.contactPhone ?? "",
    preferredCurrency: supplier.preferredCurrency,
    defaultIncoterm: supplier.defaultIncoterm ?? "",
    paymentTerms: supplier.paymentTerms ?? "",
    paymentTermDays: supplier.paymentTermDays?.toString() ?? "",
    normalLeadTimeDays: supplier.normalLeadTimeDays?.toString() ?? "",
    qualityRating: supplier.qualityRating?.toString() ?? "",
    pricingRating: supplier.pricingRating?.toString() ?? "",
    deliveryRating: supplier.deliveryRating?.toString() ?? "",
    responsivenessRating: supplier.responsivenessRating?.toString() ?? "",
    technicalRating: supplier.technicalRating?.toString() ?? "",
    commercialRating: supplier.commercialRating?.toString() ?? "",
    notes: supplier.notes ?? "",
    isActive: supplier.isActive,
  };

  const action = updateSupplier.bind(null, id);

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        title={`Edit ${supplier.name}`}
        description={`${supplier.code} — ratings feed the weighted Supplier Score.`}
      />
      <SupplierForm action={action} initial={initial} submitLabel="Save changes" />
    </div>
  );
}
