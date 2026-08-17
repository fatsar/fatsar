import Link from "next/link";
import { Plus, Scale } from "lucide-react";
import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { prisma } from "@/lib/prisma";
import { isQuotationExpired } from "@/server/material-insights";
import { QuotationsTable, type QuotationRow } from "./quotations-table";

export const metadata: Metadata = { title: "Quotations" };

export default async function QuotationsPage({
  searchParams,
}: {
  searchParams: Promise<{ materialId?: string }>;
}) {
  const { materialId } = await searchParams;
  const now = new Date();
  const [quotations, materials] = await Promise.all([
    prisma.quotation.findMany({
      include: {
        material: { select: { id: true, name: true, unit: true } },
        supplier: { select: { id: true, name: true } },
      },
      orderBy: { quotationDate: "desc" },
    }),
    prisma.material.findMany({
      where: { quotations: { some: {} } },
      select: { id: true, name: true },
      orderBy: { name: "asc" },
    }),
  ]);

  const rows: QuotationRow[] = quotations.map((q) => ({
    id: q.id,
    quotationNumber: q.quotationNumber,
    materialId: q.material.id,
    materialName: q.material.name,
    materialUnit: q.material.unit,
    supplierId: q.supplier.id,
    supplierName: q.supplier.name,
    quotationDate: q.quotationDate.toISOString(),
    quantity: q.quantity,
    quantityUnit: q.quantityUnit,
    containerCount: q.containerCount,
    price: q.price,
    currency: q.currency,
    priceUnit: q.priceUnit,
    normalizedPrice: q.normalizedPrice,
    incoterm: q.incoterm,
    paymentTerms: q.paymentTerms,
    leadTimeDays: q.leadTimeDays,
    validUntil: q.validUntil?.toISOString() ?? null,
    status: q.status,
    isExpired: isQuotationExpired(q, now),
    isSample: q.isSample,
  }));

  return (
    <div>
      <PageHeader
        title="Quotations"
        description="All supplier offers with normalized prices for like-for-like comparison."
        actions={
          <>
            <Link
              href="/quotations/compare"
              className="inline-flex h-9 items-center gap-1.5 rounded-md border border-line-strong bg-surface px-3.5 text-sm font-medium hover:bg-sunken"
            >
              <Scale size={15} /> Compare offers
            </Link>
            <Link
              href="/quotations/new"
              className="inline-flex h-9 items-center gap-1.5 rounded-md border border-accent bg-accent px-3.5 text-sm font-medium text-white hover:bg-accent-strong"
            >
              <Plus size={15} /> New quotation
            </Link>
          </>
        }
      />
      <Card>
        <QuotationsTable
          rows={rows}
          materials={materials}
          initialMaterialId={materialId}
        />
      </Card>
    </div>
  );
}
