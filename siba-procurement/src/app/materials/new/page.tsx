import type { Metadata } from "next";
import { PageHeader } from "@/components/ui/page-header";
import { prisma } from "@/lib/prisma";
import { createMaterial } from "@/server/actions/materials";
import { MaterialForm } from "../material-form";

export const metadata: Metadata = { title: "New material" };

export default async function NewMaterialPage() {
  const suppliers = await prisma.supplier.findMany({
    where: { isActive: true },
    select: { id: true, name: true, code: true },
    orderBy: { name: "asc" },
  });

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        title="New material"
        description="Create a material master record. Coverage, risk and recommendations start working as soon as stock and consumption are entered."
      />
      <MaterialForm
        action={createMaterial}
        suppliers={suppliers}
        submitLabel="Create material"
      />
    </div>
  );
}
