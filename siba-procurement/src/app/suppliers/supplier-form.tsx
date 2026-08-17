"use client";

import { useActionState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Field, FormMessage, Input, Select, Textarea } from "@/components/ui/form";
import { CURRENCIES, INCOTERMS } from "@/domain/enums";
import { initialFormState, type FormState } from "@/server/actions/form-state";

export interface SupplierFormInitial {
  code: string;
  name: string;
  country: string;
  city: string;
  website: string;
  contactName: string;
  contactEmail: string;
  contactPhone: string;
  preferredCurrency: string;
  defaultIncoterm: string;
  paymentTerms: string;
  paymentTermDays: string;
  normalLeadTimeDays: string;
  qualityRating: string;
  pricingRating: string;
  deliveryRating: string;
  responsivenessRating: string;
  technicalRating: string;
  commercialRating: string;
  notes: string;
  isActive: boolean;
}

const EMPTY: SupplierFormInitial = {
  code: "",
  name: "",
  country: "",
  city: "",
  website: "",
  contactName: "",
  contactEmail: "",
  contactPhone: "",
  preferredCurrency: "USD",
  defaultIncoterm: "",
  paymentTerms: "",
  paymentTermDays: "",
  normalLeadTimeDays: "",
  qualityRating: "",
  pricingRating: "",
  deliveryRating: "",
  responsivenessRating: "",
  technicalRating: "",
  commercialRating: "",
  notes: "",
  isActive: true,
};

const RATING_FIELDS: { name: keyof SupplierFormInitial; label: string }[] = [
  { name: "qualityRating", label: "Quality" },
  { name: "pricingRating", label: "Pricing" },
  { name: "deliveryRating", label: "Delivery reliability" },
  { name: "responsivenessRating", label: "Responsiveness" },
  { name: "technicalRating", label: "Technical support" },
  { name: "commercialRating", label: "Commercial relationship" },
];

export function SupplierForm({
  action,
  initial,
  submitLabel,
}: {
  action: (prev: FormState, formData: FormData) => Promise<FormState>;
  initial?: SupplierFormInitial;
  submitLabel: string;
}) {
  const router = useRouter();
  const [state, formAction, pending] = useActionState(action, initialFormState);
  const v = initial ?? EMPTY;
  const err = state.fieldErrors ?? {};

  useEffect(() => {
    if (state.ok && state.createdId) {
      router.push(`/suppliers/${state.createdId}`);
    }
  }, [state.ok, state.createdId, router]);

  return (
    <form action={formAction} className="space-y-4">
      <FormMessage ok={state.ok} message={state.message} />

      <Card>
        <CardHeader title="Company" />
        <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Field label="Supplier code *" htmlFor="code" error={err.code}>
            <Input id="code" name="code" defaultValue={v.code} required placeholder="WANHUA" />
          </Field>
          <Field label="Company name *" htmlFor="name" error={err.name}>
            <Input id="name" name="name" defaultValue={v.name} required />
          </Field>
          <Field label="Country *" htmlFor="country" error={err.country}>
            <Input id="country" name="country" defaultValue={v.country} required />
          </Field>
          <Field label="City" htmlFor="city" error={err.city}>
            <Input id="city" name="city" defaultValue={v.city} />
          </Field>
          <Field label="Website" htmlFor="website" error={err.website}>
            <Input id="website" name="website" defaultValue={v.website} placeholder="https://…" />
          </Field>
          <Field label="Status" htmlFor="isActive">
            <label className="flex h-9 items-center gap-2 text-sm">
              <input type="checkbox" id="isActive" name="isActive" defaultChecked={v.isActive} />
              Active supplier
            </label>
          </Field>
        </CardContent>
      </Card>

      <Card>
        <CardHeader title="Contact" />
        <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Field label="Contact person" htmlFor="contactName" error={err.contactName}>
            <Input id="contactName" name="contactName" defaultValue={v.contactName} />
          </Field>
          <Field label="Email" htmlFor="contactEmail" error={err.contactEmail}>
            <Input id="contactEmail" name="contactEmail" type="email" defaultValue={v.contactEmail} />
          </Field>
          <Field label="Phone" htmlFor="contactPhone" error={err.contactPhone}>
            <Input id="contactPhone" name="contactPhone" defaultValue={v.contactPhone} />
          </Field>
        </CardContent>
      </Card>

      <Card>
        <CardHeader title="Commercial terms" />
        <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Preferred currency" htmlFor="preferredCurrency" error={err.preferredCurrency}>
            <Select id="preferredCurrency" name="preferredCurrency" defaultValue={v.preferredCurrency}>
              {CURRENCIES.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Normal Incoterm" htmlFor="defaultIncoterm" error={err.defaultIncoterm}>
            <Select id="defaultIncoterm" name="defaultIncoterm" defaultValue={v.defaultIncoterm}>
              <option value="">—</option>
              {INCOTERMS.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Payment terms (text)" htmlFor="paymentTerms" error={err.paymentTerms}>
            <Input id="paymentTerms" name="paymentTerms" defaultValue={v.paymentTerms} placeholder="LC 60 days" />
          </Field>
          <Field
            label="Payment term days"
            htmlFor="paymentTermDays"
            error={err.paymentTermDays}
            hint="Numeric days — used to compare offers."
          >
            <Input id="paymentTermDays" name="paymentTermDays" type="number" min="0" step="1" defaultValue={v.paymentTermDays} />
          </Field>
          <Field label="Normal lead time (days)" htmlFor="normalLeadTimeDays" error={err.normalLeadTimeDays}>
            <Input id="normalLeadTimeDays" name="normalLeadTimeDays" type="number" min="0" step="1" defaultValue={v.normalLeadTimeDays} />
          </Field>
        </CardContent>
      </Card>

      <Card>
        <CardHeader
          title="Performance ratings (0–10)"
          description="Combined into the 0–100 Supplier Score using the weights configured in Settings."
        />
        <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {RATING_FIELDS.map((f) => (
            <Field key={f.name} label={f.label} htmlFor={f.name} error={err[f.name]}>
              <Input
                id={f.name}
                name={f.name}
                type="number"
                min="0"
                max="10"
                step="0.5"
                defaultValue={v[f.name] as string}
              />
            </Field>
          ))}
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
