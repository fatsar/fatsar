"use client";

import { useActionState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { ActionButton } from "@/components/ui/action-feedback";
import { Field, FormMessage, Input, Select, Textarea } from "@/components/ui/form";
import {
  ACTION_PRIORITIES,
  ACTION_STATUS_LABELS,
  ACTION_STATUSES,
  ACTION_TYPE_LABELS,
  ACTION_TYPES,
} from "@/domain/enums";
import { titleCase } from "@/lib/format";
import {
  createActionItem,
  deleteActionItem,
  updateActionItem,
} from "@/server/actions/action-items";
import { initialFormState } from "@/server/actions/form-state";
import type { ActionOption } from "./actions-client";

export interface ActionFormInitial {
  id: string;
  title: string;
  materialId: string;
  supplierId: string;
  quotationId: string;
  actionType: string;
  owner: string;
  dueDate: string;
  priority: string;
  status: string;
  notes: string;
}

export function ActionItemForm({
  initial,
  materials,
  suppliers,
  quotations,
  onSaved,
}: {
  initial?: ActionFormInitial;
  materials: ActionOption[];
  suppliers: ActionOption[];
  quotations: ActionOption[];
  onSaved: () => void;
}) {
  const action = initial
    ? updateActionItem.bind(null, initial.id)
    : createActionItem;
  const [state, formAction, pending] = useActionState(action, initialFormState);
  const err = state.fieldErrors ?? {};

  useEffect(() => {
    if (state.ok) onSaved();
  }, [state.ok, onSaved]);

  return (
    <form action={formAction} className="space-y-4">
      <FormMessage ok={state.ok} message={state.ok ? undefined : state.message} />

      <Field label="Action title *" htmlFor="a-title" error={err.title}>
        <Input id="a-title" name="title" defaultValue={initial?.title ?? ""} required />
      </Field>

      <div className="grid gap-4 sm:grid-cols-3">
        <Field label="Type" htmlFor="a-type" error={err.actionType}>
          <Select id="a-type" name="actionType" defaultValue={initial?.actionType ?? "RFQ"}>
            {ACTION_TYPES.map((t) => (
              <option key={t} value={t}>
                {ACTION_TYPE_LABELS[t]}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Priority" htmlFor="a-priority" error={err.priority}>
          <Select id="a-priority" name="priority" defaultValue={initial?.priority ?? "MEDIUM"}>
            {ACTION_PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {titleCase(p)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Status" htmlFor="a-status" error={err.status}>
          <Select id="a-status" name="status" defaultValue={initial?.status ?? "OPEN"}>
            {ACTION_STATUSES.map((s) => (
              <option key={s} value={s}>
                {ACTION_STATUS_LABELS[s]}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Material" htmlFor="a-material" error={err.materialId}>
          <Select id="a-material" name="materialId" defaultValue={initial?.materialId ?? ""}>
            <option value="">—</option>
            {materials.map((m) => (
              <option key={m.id} value={m.id}>
                {m.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Supplier" htmlFor="a-supplier" error={err.supplierId}>
          <Select id="a-supplier" name="supplierId" defaultValue={initial?.supplierId ?? ""}>
            <option value="">—</option>
            {suppliers.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Related quotation" htmlFor="a-quotation" error={err.quotationId}>
          <Select id="a-quotation" name="quotationId" defaultValue={initial?.quotationId ?? ""}>
            <option value="">—</option>
            {quotations.map((q) => (
              <option key={q.id} value={q.id}>
                {q.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Owner" htmlFor="a-owner" error={err.owner}>
          <Input id="a-owner" name="owner" defaultValue={initial?.owner ?? ""} placeholder="Purchasing Manager" />
        </Field>
        <Field label="Due date" htmlFor="a-due" error={err.dueDate}>
          <Input id="a-due" name="dueDate" type="date" defaultValue={initial?.dueDate ?? ""} />
        </Field>
      </div>

      <Field label="Notes" htmlFor="a-notes" error={err.notes}>
        <Textarea id="a-notes" name="notes" defaultValue={initial?.notes ?? ""} />
      </Field>

      <div className="flex items-center justify-between gap-2">
        <Button type="submit" variant="primary" disabled={pending}>
          {pending ? "Saving…" : initial ? "Save changes" : "Create action"}
        </Button>
        {initial ? (
          <ActionButton
            variant="danger"
            size="sm"
            action={() => deleteActionItem(initial.id)}
            confirmMessage="Delete this action?"
            onDone={(s) => {
              if (s.ok) onSaved();
            }}
          >
            Delete
          </ActionButton>
        ) : null}
      </div>
    </form>
  );
}
