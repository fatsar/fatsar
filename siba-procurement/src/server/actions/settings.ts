"use server";

import { revalidatePath } from "next/cache";
import {
  DEFAULT_OFFER_SCORE_WEIGHTS,
  DEFAULT_RECOMMENDATION_CONFIG,
  DEFAULT_SUPPLIER_SCORE_WEIGHTS,
  SETTING_KEYS,
} from "@/domain/config";
import {
  formDataToObject,
  offerWeightsSchema,
  parseForm,
  recommendationConfigSchema,
  supplierWeightsSchema,
} from "@/lib/validation";
import { getRecommendationConfig, saveSetting } from "@/server/settings";
import type { FormState } from "./form-state";

function revalidateAll() {
  // Weights/thresholds feed every computed page.
  revalidatePath("/", "layout");
}

export async function saveSupplierWeights(
  _prev: FormState,
  formData: FormData
): Promise<FormState> {
  const parsed = parseForm(supplierWeightsSchema, formDataToObject(formData));
  if (!parsed.success) {
    return { ok: false, message: "Weights must be 0–100 and sum to 100.", fieldErrors: parsed.fieldErrors };
  }
  await saveSetting(SETTING_KEYS.supplierScoreWeights, parsed.data);
  revalidateAll();
  return { ok: true, message: "Supplier score weights saved." };
}

export async function saveOfferWeights(
  _prev: FormState,
  formData: FormData
): Promise<FormState> {
  const parsed = parseForm(offerWeightsSchema, formDataToObject(formData));
  if (!parsed.success) {
    return { ok: false, message: "Weights must be 0–100 and sum to 100.", fieldErrors: parsed.fieldErrors };
  }
  await saveSetting(SETTING_KEYS.offerScoreWeights, parsed.data);
  revalidateAll();
  return { ok: true, message: "Offer scoring weights saved." };
}

export async function saveRecommendationThresholds(
  _prev: FormState,
  formData: FormData
): Promise<FormState> {
  const parsed = parseForm(
    recommendationConfigSchema,
    formDataToObject(formData)
  );
  if (!parsed.success) {
    return { ok: false, message: "Please fix the highlighted fields.", fieldErrors: parsed.fieldErrors };
  }
  // Keep rule points/levels from the current stored config — only thresholds
  // are editable in Phase 1.
  const current = await getRecommendationConfig();
  await saveSetting(SETTING_KEYS.recommendationConfig, {
    ...current,
    ...parsed.data,
  });
  revalidateAll();
  return { ok: true, message: "Recommendation thresholds saved." };
}

export async function resetSettingsToDefaults(): Promise<FormState> {
  await Promise.all([
    saveSetting(SETTING_KEYS.supplierScoreWeights, DEFAULT_SUPPLIER_SCORE_WEIGHTS),
    saveSetting(SETTING_KEYS.offerScoreWeights, DEFAULT_OFFER_SCORE_WEIGHTS),
    saveSetting(SETTING_KEYS.recommendationConfig, DEFAULT_RECOMMENDATION_CONFIG),
  ]);
  revalidateAll();
  return { ok: true, message: "All settings reset to defaults." };
}
