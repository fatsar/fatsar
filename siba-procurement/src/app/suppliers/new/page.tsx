import type { Metadata } from "next";
import { PageHeader } from "@/components/ui/page-header";
import { createSupplier } from "@/server/actions/suppliers";
import { SupplierForm } from "../supplier-form";

export const metadata: Metadata = { title: "New supplier" };

export default function NewSupplierPage() {
  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        title="New supplier"
        description="Create a supplier master record. Ratings feed the weighted Supplier Score."
      />
      <SupplierForm action={createSupplier} submitLabel="Create supplier" />
    </div>
  );
}
