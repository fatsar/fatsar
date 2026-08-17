"use client";

import { useActionState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Field, FormMessage, Input, Select, Textarea } from "@/components/ui/form";
import {
  CURRENCIES,
  INCOTERMS,
  QUANTITY_UNITS,
  QUOTATION_STATUSES,
} from "@/domain/enums";
import { titleCase } from "@/lib/format";
import { initialFormState, type FormState } from "@/server/actions/form-state";

export interface QuotationFormInitial {
  quotationNumber: string;
  supplierId: string;
  materialId: string;
  quotationDate: string;
  quantity: string;
  quantityUnit: string;
  containerCount: string;
  qtyPerContainer: string;
  price: string;
  currency: string;
  priceUnit: string;
  incoterm: string;
  destinationPort: string;
  paymentTerms: string;
  paymentTermDays: string;
  leadTimeDays: string;
  productionLeadTimeDays: string;
  validUntil: string;
  freightIncluded: boolean;
  status: string;
  remarks: string;
  attachmentRef: string;
}

const EMPTY: QuotationFormInitial = {
  quotationNumber: "",
  supplierId: "",
  materialId: "",
  quotationDate: new Date().toISOString().slice(0, 10),
  quantity: "",
  quantityUnit: "kg",
  containerCount: "",
  qtyPerContainer: "",
  price: "",
  currency: "USD",
  priceUnit: "kg",
  incoterm: "CIF",
  destinationPort: "",
  paymentTerms: "",
  paymentTermDays: "",
  leadTimeDays: "",
  productionLeadTimeDays: "",
  validUntil: "",
  freightIncluded: true,
  status: "ACTIVE",
  remarks: "",
  attachmentRef: "",
};

export function QuotationForm({
  action,
  materials,
  suppliers,
  initial,
  submitLabel,
  redirectToMaterial = true,
}: {
  action: (prev: FormState, formData: FormData) => Promise<FormState>;
  materials: { id: string; name: string; unit: string }[];
  suppliers: { id: string; name: string }[];
  initial?: Partial<QuotationFormInitial>;
  submitLabel: string;
  redirectToMaterial?: boolean;
}) {
  const router = useRouter();
  const [state, formAction, pending] = useActionState(action, initialFormState);
  const v: QuotationFormInitial = { ...EMPTY, ...initial };
  const err = state.fieldErrors ?? {};

  useEffect(() => {
    if (state.ok && state.createdId && redirectToMaterial) {
      // materialId comes back from the server action — the form control has
      // already been reset by the time this effect runs.
      const materialId = state.meta?.materialId;
      router.push(
        materialId ? `/quotations/compare?materialId=${materialId}` : "/quotations"
      );
    }
  }, [state.ok, state.createdId, state.meta, redirectToMaterial, router]);

  return (
    <form action={formAction} className="space-y-4">
      <FormMessage ok={state.ok} message={state.message} />

      <Card>
        <CardHeader title="Offer" />
        <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Field label="Quotation number *" htmlFor="quotationNumber" error={err.quotationNumber}>
            <Input id="quotationNumber" name="quotationNumber" defaultValue={v.quotationNumber} required placeholder="WANHUA-Q1234" />
          </Field>
          <Field label="Material *" htmlFor="materialId" error={err.materialId}>
            <Select id="materialId" name="materialId" defaultValue={v.materialId} required>
              <option value="">— select —</option>
              {materials.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.name} (base: {m.unit})
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Supplier *" htmlFor="supplierId" error={err.supplierId}>
            <Select id="supplierId" name="supplierId" defaultValue={v.supplierId} required>
              <option value="">— select —</option>
              {suppliers.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Quotation date *" htmlFor="quotationDate" error={err.quotationDate}>
            <Input id="quotationDate" name="quotationDate" type="date" defaultValue={v.quotationDate} required />
          </Field>
          <Field label="Valid until" htmlFor="validUntil" error={err.validUntil}>
            <Input id="validUntil" name="validUntil" type="date" defaultValue={v.validUntil} />
          </Field>
          <Field label="Status" htmlFor="status" error={err.status}>
            <Select id="status" name="status" defaultValue={v.status}>
              {QUOTATION_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {titleCase(s)}
                </option>
              ))}
            </Select>
          </Field>
        </CardContent>
      </Card>

      <Card>
        <CardHeader
          title="Price & quantity"
          description="The price is normalized to the material's base unit automatically (e.g. $/MT → $/kg)."
        />
        <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Price *" htmlFor="price" error={err.price}>
            <Input id="price" name="price" type="number" step="any" min="0" defaultValue={v.price} required />
          </Field>
          <Field label="Currency" htmlFor="currency" error={err.currency}>
            <Select id="currency" name="currency" defaultValue={v.currency}>
              {CURRENCIES.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Price per unit" htmlFor="priceUnit" error={err.priceUnit}>
            <Select id="priceUnit" name="priceUnit" defaultValue={v.priceUnit}>
              {QUANTITY_UNITS.map((u) => (
                <option key={u} value={u}>
                  per {u}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Quoted quantity *" htmlFor="quantity" error={err.quantity}>
            <Input id="quantity" name="quantity" type="number" step="any" min="0" defaultValue={v.quantity} required />
          </Field>
          <Field label="Quantity unit" htmlFor="quantityUnit" error={err.quantityUnit}>
            <Select id="quantityUnit" name="quantityUnit" defaultValue={v.quantityUnit}>
              {QUANTITY_UNITS.map((u) => (
                <option key={u} value={u}>
                  {u}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Containers (FCL)" htmlFor="containerCount" error={err.containerCount}>
            <Input id="containerCount" name="containerCount" type="number" min="0" step="1" defaultValue={v.containerCount} />
          </Field>
          <Field label="Qty per container" htmlFor="qtyPerContainer" error={err.qtyPerContainer}>
            <Input id="qtyPerContainer" name="qtyPerContainer" type="number" step="any" min="0" defaultValue={v.qtyPerContainer} />
          </Field>
        </CardContent>
      </Card>

      <Card>
        <CardHeader title="Terms & logistics" />
        <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Incoterm" htmlFor="incoterm" error={err.incoterm}>
            <Select id="incoterm" name="incoterm" defaultValue={v.incoterm}>
              <option value="">—</option>
              {INCOTERMS.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Destination port" htmlFor="destinationPort" error={err.destinationPort}>
            <Input id="destinationPort" name="destinationPort" defaultValue={v.destinationPort} placeholder="Gemlik, Türkiye" />
          </Field>
          <Field label="Payment terms (text)" htmlFor="paymentTerms" error={err.paymentTerms}>
            <Input id="paymentTerms" name="paymentTerms" defaultValue={v.paymentTerms} placeholder="LC 60 days" />
          </Field>
          <Field label="Payment term days" htmlFor="paymentTermDays" error={err.paymentTermDays}>
            <Input id="paymentTermDays" name="paymentTermDays" type="number" min="0" step="1" defaultValue={v.paymentTermDays} />
          </Field>
          <Field label="Lead time (days)" htmlFor="leadTimeDays" error={err.leadTimeDays}>
            <Input id="leadTimeDays" name="leadTimeDays" type="number" min="0" step="1" defaultValue={v.leadTimeDays} />
          </Field>
          <Field label="Production lead time (days)" htmlFor="productionLeadTimeDays" error={err.productionLeadTimeDays}>
            <Input id="productionLeadTimeDays" name="productionLeadTimeDays" type="number" min="0" step="1" defaultValue={v.productionLeadTimeDays} />
          </Field>
          <Field label="Freight included" htmlFor="freightIncluded">
            <label className="flex h-9 items-center gap-2 text-sm">
              <input
                type="checkbox"
                id="freightIncluded"
                name="freightIncluded"
                defaultChecked={v.freightIncluded}
              />
              Freight included in price
            </label>
          </Field>
          <Field
            label="Attachment reference"
            htmlFor="attachmentRef"
            error={err.attachmentRef}
            hint="File name or link — uploads come in a later phase."
          >
            <Input id="attachmentRef" name="attachmentRef" defaultValue={v.attachmentRef} />
          </Field>
        </CardContent>
      </Card>

      <Card>
        <CardHeader title="Remarks" />
        <CardContent>
          <Textarea name="remarks" defaultValue={v.remarks} placeholder="Offer remarks…" />
        </CardContent>
      </Card>

      <div className="flex items-center gap-2">
        <Button type="submit" variant="primary" disabled={pending}>
          {pending ? "Saving…" : submitLabel}
        </Button>
        <Button type="button" variant="ghost" onClick={() => router.back()}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
