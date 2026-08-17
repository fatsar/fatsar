import Link from "next/link";
import { Plus } from "lucide-react";
import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { prisma } from "@/lib/prisma";
import { calculateSupplierScore } from "@/domain/supplier-score";
import { getSupplierScoreWeights } from "@/server/settings";
import { SuppliersTable, type SupplierRow } from "./suppliers-table";

export const metadata: Metadata = { title: "Suppliers" };

export default async function SuppliersPage() {
  const [suppliers, weights] = await Promise.all([
    prisma.supplier.findMany({
      include: {
        materials: { include: { material: { select: { name: true } } } },
        _count: { select: { quotations: true } },
      },
      orderBy: { name: "asc" },
    }),
    getSupplierScoreWeights(),
  ]);

  const rows: SupplierRow[] = suppliers.map((s) => ({
    id: s.id,
    code: s.code,
    name: s.name,
    country: s.country,
    city: s.city,
    materials: s.materials.map((m) => m.material.name).join(", "),
    materialCount: s.materials.length,
    score: calculateSupplierScore(s, weights).score,
    paymentTerms: s.paymentTerms,
    normalLeadTimeDays: s.normalLeadTimeDays,
    quotationCount: s._count.quotations,
    isActive: s.isActive,
    isSample: s.isSample,
  }));

  return (
    <div>
      <PageHeader
        title="Suppliers"
        description="Supplier master with weighted 0–100 scores (weights configurable in Settings)."
        actions={
          <Link
            href="/suppliers/new"
            className="inline-flex h-9 items-center gap-1.5 rounded-md border border-accent bg-accent px-3.5 text-sm font-medium text-white hover:bg-accent-strong"
          >
            <Plus size={15} /> New supplier
          </Link>
        }
      />
      <Card>
        <SuppliersTable rows={rows} />
      </Card>
    </div>
  );
}
