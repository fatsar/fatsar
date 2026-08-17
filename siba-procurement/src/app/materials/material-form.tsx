"use client";

import { useActionState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Field, FormMessage, Input, Select, Textarea } from "@/components/ui/form";
import {
  CURRENCIES,
  INCOTERMS,
  MATERIAL_CATEGORY_LABELS,
  QUANTITY_UNITS,
} from "@/domain/enums";
import { initialFormState, type FormState } from "@/server/actions/form-state";

export interface MaterialFormInitial {
  code: string;
  name: string;
  category: string;
  unit: string;
  specification: string;
  preferredSupplierId: string;
  avgMonthlyConsumption: string;
  avgDailyConsumption: string;
  forecastMonthlyDemand: string;
  currentStock: string;
  reservedStock: string;
  safetyStock: string;
  minimumStock: string;
  leadTimeDays: string;
  openPurchaseQty: string;
  inboundQty: string;
  inboundEta: string;
  lastPurchasePrice: string;
  lastPurchaseDate: string;
  targetPrice: string;
  currency: string;
  incoterm: string;
  notes: string;
  isActive: boolean;
  approvedSupplierIds: string[];
}

const EMPTY: MaterialFormInitial = {
  code: "",
  name: "",
  category: "OTHER",
  unit: "kg",
  specification: "",
  preferredSupplierId: "",
  avgMonthlyConsumption: "",
  avgDailyConsumption: "",
  forecastMonthlyDemand: "",
  currentStock: "0",
  reservedStock: "0",
  safetyStock: "0",
  minimumStock: "0",
  leadTimeDays: "",
  openPurchaseQty: "0",
  inboundQty: "0",
  inboundEta: "",
  lastPurchasePrice: "",
  lastPurchaseDate: "",
  targetPrice: "",
  currency: "USD",
  incoterm: "",
  notes: "",
  isActive: true,
  approvedSupplierIds: [],
};

export function MaterialForm({
  action,
  suppliers,
  initial,
  submitLabel,
}: {
  action: (prev: FormState, formData: FormData) => Promise<FormState>;
  suppliers: { id: string; name: string; code: string }[];
  initial?: MaterialFormInitial;
  submitLabel: string;
}) {
  const router = useRouter();
  const [state, formAction, pending] = useActionState(action, initialFormState);
  const v = initial ?? EMPTY;
  const err = state.fieldErrors ?? {};

  useEffect(() => {
    if (state.ok && state.createdId) {
      router.push(`/materials/${state.createdId}`);
    }
  }, [state.ok, state.createdId, router]);

  return (
    <form action={formAction} className="space-y-4">
      <FormMessage ok={state.ok} message={state.message} />

      <Card>
        <CardHeader title="Identity" />
        <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Material code *" htmlFor="code" error={err.code}>
            <Input id="code" name="code" defaultValue={v.code} required placeholder="RM-PMDI-001" />
          </Field>
          <Field label="Name *" htmlFor="name" error={err.name}>
            <Input id="name" name="name" defaultValue={v.name} required placeholder="PMDI" />
          </Field>
          <Field label="Category *" htmlFor="category" error={err.category}>
            <Select id="category" name="category" defaultValue={v.category}>
              {Object.entries(MATERIAL_CATEGORY_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </Field>
          <Field
            label="Base unit *"
            htmlFor="unit"
            error={err.unit}
            hint="Stock, consumption and normalized prices use this unit."
          >
            <Select id="unit" name="unit" defaultValue={v.unit}>
              {QUANTITY_UNITS.map((u) => (
                <option key={u} value={u}>
                  {u}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Specification" htmlFor="specification" error={err.specification} className="sm:col-span-2">
            <Input
              id="specification"
              name="specification"
              defaultValue={v.specification}
              placeholder="e.g. NCO 30.5–32.0%, viscosity 150–250 mPa·s"
            />
          </Field>
          <Field label="Status" htmlFor="isActive">
            <label className="flex h-9 items-center gap-2 text-sm">
              <input
                type="checkbox"
                id="isActive"
                name="isActive"
                defaultChecked={v.isActive}
              />
              Active material
            </label>
          </Field>
        </CardContent>
      </Card>

      <Card>
        <CardHeader
          title="Suppliers"
          description="Approved suppliers qualify for quotations; the preferred supplier drives lead-time expectations."
        />
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <Field label="Preferred supplier" htmlFor="preferredSupplierId" error={err.preferredSupplierId}>
            <Select
              id="preferredSupplierId"
              name="preferredSupplierId"
              defaultValue={v.preferredSupplierId}
            >
              <option value="">— none —</option>
              {suppliers.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name} ({s.code})
                </option>
              ))}
            </Select>
          </Field>
          <div>
            <span className="mb-1 block text-[12.5px] font-medium text-ink-secondary">
              Approved suppliers
            </span>
            <div className="grid max-h-40 gap-1 overflow-y-auto rounded-md border border-line p-2 sm:grid-cols-2">
              {suppliers.length === 0 ? (
                <span className="text-[13px] text-ink-muted">No suppliers yet.</span>
              ) : (
                suppliers.map((s) => (
                  <label key={s.id} className="flex items-center gap-2 text-[13px]">
                    <input
                      type="checkbox"
                      name="approvedSupplierIds"
                      value={s.id}
                      defaultChecked={v.approvedSupplierIds.includes(s.id)}
                    />
                    {s.name}
                  </label>
                ))
              )}
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader title="Consumption & demand" />
        <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Field
            label="Avg monthly consumption"
            htmlFor="avgMonthlyConsumption"
            error={err.avgMonthlyConsumption}
          >
            <Input id="avgMonthlyConsumption" name="avgMonthlyConsumption" type="number" step="any" min="0" defaultValue={v.avgMonthlyConsumption} />
          </Field>
          <Field
            label="Avg daily consumption"
            htmlFor="avgDailyConsumption"
            error={err.avgDailyConsumption}
            hint="Leave empty to derive monthly ÷ 30."
          >
            <Input id="avgDailyConsumption" name="avgDailyConsumption" type="number" step="any" min="0" defaultValue={v.avgDailyConsumption} />
          </Field>
          <Field
            label="Forecast monthly demand"
            htmlFor="forecastMonthlyDemand"
            error={err.forecastMonthlyDemand}
            hint="Used by the projection when above average."
          >
            <Input id="forecastMonthlyDemand" name="forecastMonthlyDemand" type="number" step="any" min="0" defaultValue={v.forecastMonthlyDemand} />
          </Field>
        </CardContent>
      </Card>

      <Card>
        <CardHeader title="Stock" />
        <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Current stock" htmlFor="currentStock" error={err.currentStock}>
            <Input id="currentStock" name="currentStock" type="number" step="any" min="0" defaultValue={v.currentStock} />
          </Field>
          <Field label="Reserved stock" htmlFor="reservedStock" error={err.reservedStock}>
            <Input id="reservedStock" name="reservedStock" type="number" step="any" min="0" defaultValue={v.reservedStock} />
          </Field>
          <Field label="Safety stock" htmlFor="safetyStock" error={err.safetyStock}>
            <Input id="safetyStock" name="safetyStock" type="number" step="any" min="0" defaultValue={v.safetyStock} />
          </Field>
          <Field label="Minimum stock" htmlFor="minimumStock" error={err.minimumStock}>
            <Input id="minimumStock" name="minimumStock" type="number" step="any" min="0" defaultValue={v.minimumStock} />
          </Field>
        </CardContent>
      </Card>

      <Card>
        <CardHeader title="Replenishment" />
        <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Lead time (days)" htmlFor="leadTimeDays" error={err.leadTimeDays}>
            <Input id="leadTimeDays" name="leadTimeDays" type="number" min="0" step="1" defaultValue={v.leadTimeDays} />
          </Field>
          <Field
            label="Open purchase qty"
            htmlFor="openPurchaseQty"
            error={err.openPurchaseQty}
            hint="Ordered, ETA not yet confirmed."
          >
            <Input id="openPurchaseQty" name="openPurchaseQty" type="number" step="any" min="0" defaultValue={v.openPurchaseQty} />
          </Field>
          <Field
            label="Inbound qty"
            htmlFor="inboundQty"
            error={err.inboundQty}
            hint="Shipped / ETA confirmed."
          >
            <Input id="inboundQty" name="inboundQty" type="number" step="any" min="0" defaultValue={v.inboundQty} />
          </Field>
          <Field label="Inbound ETA" htmlFor="inboundEta" error={err.inboundEta}>
            <Input id="inboundEta" name="inboundEta" type="date" defaultValue={v.inboundEta} />
          </Field>
        </CardContent>
      </Card>

      <Card>
        <CardHeader title="Pricing" />
        <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Field label="Target price (per base unit)" htmlFor="targetPrice" error={err.targetPrice}>
            <Input id="targetPrice" name="targetPrice" type="number" step="any" min="0" defaultValue={v.targetPrice} />
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
          <Field label="Usual Incoterm" htmlFor="incoterm" error={err.incoterm}>
            <Select id="incoterm" name="incoterm" defaultValue={v.incoterm}>
              <option value="">—</option>
              {INCOTERMS.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Last purchase price" htmlFor="lastPurchasePrice" error={err.lastPurchasePrice}>
            <Input id="lastPurchasePrice" name="lastPurchasePrice" type="number" step="any" min="0" defaultValue={v.lastPurchasePrice} />
          </Field>
          <Field label="Last purchase date" htmlFor="lastPurchaseDate" error={err.lastPurchaseDate}>
            <Input id="lastPurchaseDate" name="lastPurchaseDate" type="date" defaultValue={v.lastPurchaseDate} />
          </Field>
        </CardContent>
      </Card>

      <Card>
        <CardHeader title="Notes" />
        <CardContent>
          <Textarea name="notes" defaultValue={v.notes} placeholder="Internal notes…" />
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
