"use client";

import { useActionState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { ActionButton } from "@/components/ui/action-feedback";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Field, FormMessage, Input } from "@/components/ui/form";
import type {
  OfferScoreWeights,
  RecommendationConfig,
  SupplierScoreWeights,
} from "@/domain/config";
import {
  resetSettingsToDefaults,
  saveOfferWeights,
  saveRecommendationThresholds,
  saveSupplierWeights,
} from "@/server/actions/settings";
import { initialFormState } from "@/server/actions/form-state";

function WeightInput({
  name,
  label,
  defaultValue,
  error,
}: {
  name: string;
  label: string;
  defaultValue: number;
  error?: string;
}) {
  return (
    <Field label={label} htmlFor={`w-${name}`} error={error}>
      <Input
        id={`w-${name}`}
        name={name}
        type="number"
        min="0"
        max="100"
        step="1"
        defaultValue={defaultValue}
        className="tnum"
      />
    </Field>
  );
}

export function SupplierWeightsForm({ weights }: { weights: SupplierScoreWeights }) {
  const [state, formAction, pending] = useActionState(saveSupplierWeights, initialFormState);
  const err = state.fieldErrors ?? {};
  return (
    <Card>
      <CardHeader
        title="Supplier score weights"
        description="Six 0–10 ratings combine into the 0–100 Supplier Score. Weights must sum to 100."
      />
      <CardContent>
        <form action={formAction} className="space-y-3">
          <FormMessage ok={state.ok} message={state.message} />
          {err._form ? <FormMessage ok={false} message={err._form} /> : null}
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
            <WeightInput name="quality" label="Quality" defaultValue={weights.quality} error={err.quality} />
            <WeightInput name="pricing" label="Pricing" defaultValue={weights.pricing} error={err.pricing} />
            <WeightInput name="delivery" label="Delivery reliability" defaultValue={weights.delivery} error={err.delivery} />
            <WeightInput name="responsiveness" label="Responsiveness" defaultValue={weights.responsiveness} error={err.responsiveness} />
            <WeightInput name="technical" label="Technical support" defaultValue={weights.technical} error={err.technical} />
            <WeightInput name="commercial" label="Commercial relationship" defaultValue={weights.commercial} error={err.commercial} />
          </div>
          <Button type="submit" variant="primary" disabled={pending}>
            {pending ? "Saving…" : "Save supplier weights"}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

export function OfferWeightsForm({ weights }: { weights: OfferScoreWeights }) {
  const [state, formAction, pending] = useActionState(saveOfferWeights, initialFormState);
  const err = state.fieldErrors ?? {};
  return (
    <Card>
      <CardHeader
        title="Best Overall Offer weights"
        description="Weighting of the quotation comparison dimensions. Must sum to 100."
      />
      <CardContent>
        <form action={formAction} className="space-y-3">
          <FormMessage ok={state.ok} message={state.message} />
          {err._form ? <FormMessage ok={false} message={err._form} /> : null}
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <WeightInput name="price" label="Price" defaultValue={weights.price} error={err.price} />
            <WeightInput name="paymentTerms" label="Payment terms" defaultValue={weights.paymentTerms} error={err.paymentTerms} />
            <WeightInput name="leadTime" label="Lead time" defaultValue={weights.leadTime} error={err.leadTime} />
            <WeightInput name="supplierScore" label="Supplier score" defaultValue={weights.supplierScore} error={err.supplierScore} />
          </div>
          <Button type="submit" variant="primary" disabled={pending}>
            {pending ? "Saving…" : "Save offer weights"}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

export function ThresholdsForm({ config }: { config: RecommendationConfig }) {
  const [state, formAction, pending] = useActionState(
    saveRecommendationThresholds,
    initialFormState
  );
  const err = state.fieldErrors ?? {};
  return (
    <Card>
      <CardHeader
        title="Recommendation & risk thresholds"
        description="Tuning knobs of the deterministic engines — documented in docs/procurement-rules.md."
      />
      <CardContent>
        <form action={formAction} className="space-y-3">
          <FormMessage ok={state.ok} message={state.message} />
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
            <Field
              label="Urgent stockout factor"
              htmlFor="t-urgent"
              error={err.urgentStockoutFactor}
              hint="Stockout earlier than lead time × factor ⇒ URGENT PURCHASE (0–1)."
            >
              <Input id="t-urgent" name="urgentStockoutFactor" type="number" min="0" max="1" step="0.05" defaultValue={config.urgentStockoutFactor} />
            </Field>
            <Field
              label="Monitor factor"
              htmlFor="t-monitor"
              error={err.monitorFactor}
              hint="Comfort zone = position above reorder point × factor."
            >
              <Input id="t-monitor" name="monitorFactor" type="number" min="1" max="10" step="0.1" defaultValue={config.monitorFactor} />
            </Field>
            <Field
              label="Negotiate threshold (%)"
              htmlFor="t-negotiate"
              error={err.negotiateThresholdPct}
              hint="Price above target by more than this ⇒ negotiate / wait."
            >
              <Input id="t-negotiate" name="negotiateThresholdPct" type="number" min="0" max="100" step="0.5" defaultValue={config.negotiateThresholdPct} />
            </Field>
            <Field
              label="Price spike threshold (%)"
              htmlFor="t-spike"
              error={err.priceSpikeThresholdPct}
              hint="Latest vs 3-month average above this ⇒ risk factor."
            >
              <Input id="t-spike" name="priceSpikeThresholdPct" type="number" min="0" max="100" step="0.5" defaultValue={config.priceSpikeThresholdPct} />
            </Field>
            <Field
              label="Quotation stale after (days)"
              htmlFor="t-stale"
              error={err.quotationStaleDays}
            >
              <Input id="t-stale" name="quotationStaleDays" type="number" min="1" max="365" step="1" defaultValue={config.quotationStaleDays} />
            </Field>
            <Field
              label="Forecast surge threshold (%)"
              htmlFor="t-surge"
              error={err.forecastSurgePct}
              hint="Forecast above average consumption by this ⇒ risk factor."
            >
              <Input id="t-surge" name="forecastSurgePct" type="number" min="0" max="500" step="1" defaultValue={config.forecastSurgePct} />
            </Field>
          </div>
          <Button type="submit" variant="primary" disabled={pending}>
            {pending ? "Saving…" : "Save thresholds"}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

export function ResetDefaults() {
  const router = useRouter();
  return (
    <Card>
      <CardHeader
        title="Reset"
        description="Restore all weights and thresholds to the documented defaults."
      />
      <CardContent>
        <ActionButton
          variant="danger"
          action={resetSettingsToDefaults}
          confirmMessage="Reset ALL scoring weights and thresholds to defaults?"
          onDone={() => router.refresh()}
        >
          Reset to defaults
        </ActionButton>
      </CardContent>
    </Card>
  );
}
