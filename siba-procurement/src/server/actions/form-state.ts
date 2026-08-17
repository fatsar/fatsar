import type { FieldErrors } from "@/lib/validation";

/** Result contract for every server action driven by useActionState. */
export interface FormState {
  ok: boolean;
  message?: string;
  fieldErrors?: FieldErrors;
  /** Set on successful creates so the client can navigate to the record. */
  createdId?: string;
  /** Extra values for post-submit navigation (forms reset after actions). */
  meta?: Record<string, string>;
}

export const initialFormState: FormState = { ok: false };
