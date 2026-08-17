import { prisma } from "@/lib/prisma";
import {
  DEFAULT_OFFER_SCORE_WEIGHTS,
  DEFAULT_RECOMMENDATION_CONFIG,
  DEFAULT_SUPPLIER_SCORE_WEIGHTS,
  SETTING_KEYS,
  type OfferScoreWeights,
  type RecommendationConfig,
  type SupplierScoreWeights,
} from "@/domain/config";

/**
 * Typed accessors for configurable business parameters. Stored JSON is merged
 * over code defaults so that adding a new parameter never breaks an existing
 * database. Invalid JSON falls back to defaults (and should be fixed via the
 * Settings page).
 */

async function readJson(key: string): Promise<unknown | null> {
  const row = await prisma.setting.findUnique({ where: { key } });
  if (!row) return null;
  try {
    return JSON.parse(row.value);
  } catch {
    return null;
  }
}

export async function getSupplierScoreWeights(): Promise<SupplierScoreWeights> {
  const stored = (await readJson(
    SETTING_KEYS.supplierScoreWeights
  )) as Partial<SupplierScoreWeights> | null;
  return { ...DEFAULT_SUPPLIER_SCORE_WEIGHTS, ...(stored ?? {}) };
}

export async function getOfferScoreWeights(): Promise<OfferScoreWeights> {
  const stored = (await readJson(
    SETTING_KEYS.offerScoreWeights
  )) as Partial<OfferScoreWeights> | null;
  return { ...DEFAULT_OFFER_SCORE_WEIGHTS, ...(stored ?? {}) };
}

export async function getRecommendationConfig(): Promise<RecommendationConfig> {
  const stored = (await readJson(SETTING_KEYS.recommendationConfig)) as
    | (Partial<RecommendationConfig> & {
        riskPoints?: Partial<RecommendationConfig["riskPoints"]>;
        riskLevels?: Partial<RecommendationConfig["riskLevels"]>;
      })
    | null;
  const d = DEFAULT_RECOMMENDATION_CONFIG;
  if (!stored) return d;
  return {
    ...d,
    ...stored,
    riskPoints: { ...d.riskPoints, ...(stored.riskPoints ?? {}) },
    riskLevels: { ...d.riskLevels, ...(stored.riskLevels ?? {}) },
  };
}

export async function saveSetting(key: string, value: unknown): Promise<void> {
  const json = JSON.stringify(value);
  await prisma.setting.upsert({
    where: { key },
    create: { key, value: json },
    update: { value: json },
  });
}
