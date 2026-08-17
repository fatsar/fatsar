"use client";

import { useActionState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Field, FormMessage, Input, Select, Textarea } from "@/components/ui/form";
import {
  INTEL_CATEGORY_LABELS,
  PRICE_DIRECTIONS,
  RISK_LEVELS,
  SUPPLY_IMPACTS,
} from "@/domain/enums";
import { titleCase } from "@/lib/format";
import { createIntel, updateIntel } from "@/server/actions/intel";
import { initialFormState } from "@/server/actions/form-state";

export interface IntelFormInitial {
  id: string;
  date: string;
  title: string;
  category: string;
  affectedMaterials: string;
  geography: string;
  summary: string;
  expectedImpact: string;
  priceDirection: string;
  supplyImpact: string;
  riskLevel: string;
  source: string;
  url: string;
  notes: string;
  isActive: boolean;
}

export function IntelForm({
  initial,
  onSaved,
}: {
  initial?: IntelFormInitial;
  onSaved: () => void;
}) {
  const action = initial ? updateIntel.bind(null, initial.id) : createIntel;
  const [state, formAction, pending] = useActionState(action, initialFormState);
  const err = state.fieldErrors ?? {};

  useEffect(() => {
    if (state.ok) onSaved();
  }, [state.ok, onSaved]);

  return (
    <form action={formAction} className="space-y-4">
      <FormMessage ok={state.ok} message={state.ok ? undefined : state.message} />

      <div className="grid gap-4 sm:grid-cols-3">
        <Field label="Date *" htmlFor="i-date" error={err.date}>
          <Input
            id="i-date"
            name="date"
            type="date"
            defaultValue={initial?.date ?? new Date().toISOString().slice(0, 10)}
            required
          />
        </Field>
        <Field label="Category" htmlFor="i-category" error={err.category}>
          <Select id="i-category" name="category" defaultValue={initial?.category ?? "MARKET"}>
            {Object.entries(INTEL_CATEGORY_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Risk level" htmlFor="i-risk" error={err.riskLevel}>
          <Select id="i-risk" name="riskLevel" defaultValue={initial?.riskLevel ?? "MEDIUM"}>
            {RISK_LEVELS.map((l) => (
              <option key={l} value={l}>
                {l}
              </option>
            ))}
          </Select>
        </Field>
      </div>

      <Field label="Title *" htmlFor="i-title" error={err.title}>
        <Input id="i-title" name="title" defaultValue={initial?.title ?? ""} required />
      </Field>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field
          label="Affected materials *"
          htmlFor="i-affected"
          error={err.affectedMaterials}
          hint="Comma-separated material codes and/or categories, or ALL (e.g. RM-PMDI-001, SILICONE)."
        >
          <Input
            id="i-affected"
            name="affectedMaterials"
            defaultValue={initial?.affectedMaterials ?? ""}
            required
          />
        </Field>
        <Field label="Geography" htmlFor="i-geo" error={err.geography}>
          <Input id="i-geo" name="geography" defaultValue={initial?.geography ?? ""} placeholder="China / Red Sea / Global…" />
        </Field>
      </div>

      <Field label="Summary *" htmlFor="i-summary" error={err.summary}>
        <Textarea id="i-summary" name="summary" defaultValue={initial?.summary ?? ""} required />
      </Field>

      <div className="grid gap-4 sm:grid-cols-3">
        <Field label="Price direction" htmlFor="i-direction" error={err.priceDirection}>
          <Select id="i-direction" name="priceDirection" defaultValue={initial?.priceDirection ?? "UNCERTAIN"}>
            {PRICE_DIRECTIONS.map((d) => (
              <option key={d} value={d}>
                {d}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Supply impact" htmlFor="i-supply" error={err.supplyImpact}>
          <Select id="i-supply" name="supplyImpact" defaultValue={initial?.supplyImpact ?? ""}>
            <option value="">—</option>
            {SUPPLY_IMPACTS.map((s) => (
              <option key={s} value={s}>
                {titleCase(s)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Expected impact" htmlFor="i-impact" error={err.expectedImpact}>
          <Input id="i-impact" name="expectedImpact" defaultValue={initial?.expectedImpact ?? ""} />
        </Field>
        <Field label="Source" htmlFor="i-source" error={err.source}>
          <Input id="i-source" name="source" defaultValue={initial?.source ?? "Manual entry"} />
        </Field>
        <Field label="URL" htmlFor="i-url" error={err.url}>
          <Input id="i-url" name="url" defaultValue={initial?.url ?? ""} placeholder="https://…" />
        </Field>
        <Field label="Status" htmlFor="i-active">
          <label className="flex h-9 items-center gap-2 text-sm">
            <input
              type="checkbox"
              id="i-active"
              name="isActive"
              defaultChecked={initial?.isActive ?? true}
            />
            Active (feeds the risk engine)
          </label>
        </Field>
      </div>

      <Field label="Notes" htmlFor="i-notes" error={err.notes}>
        <Textarea id="i-notes" name="notes" defaultValue={initial?.notes ?? ""} />
      </Field>

      <Button type="submit" variant="primary" disabled={pending}>
        {pending ? "Saving…" : initial ? "Save changes" : "Create entry"}
      </Button>
    </form>
  );
}
