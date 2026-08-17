import { z } from "zod";
import {
  ACTION_PRIORITIES,
  ACTION_STATUSES,
  ACTION_TYPES,
  CURRENCIES,
  INCOTERMS,
  INTEL_CATEGORIES,
  MATERIAL_CATEGORIES,
  PRICE_DIRECTIONS,
  QUANTITY_UNITS,
  QUOTATION_STATUSES,
  RISK_LEVELS,
  SUPPLY_IMPACTS,
} from "@/domain/enums";

/**
 * Centralized validation for every mutation. Server actions parse FormData
 * through these schemas — no route may write to the database without them.
 */

// ---- shared coercion helpers (FormData sends strings; "" means empty) ------
const emptyToNull = (v: unknown) =>
  v === "" || v == null || (typeof v === "string" && v.trim() === "") ? null : v;

const optionalString = z.preprocess(
  emptyToNull,
  z.string().trim().max(2000).nullable()
);
const requiredString = z
  .string()
  .trim()
  .min(1, "Required")
  .max(500);
const optionalNonNegative = z.preprocess(
  emptyToNull,
  z.coerce.number().finite().min(0, "Must be ≥ 0").nullable()
);
const nonNegativeDefaultZero = z.preprocess(
  (v) => (v === "" || v == null ? 0 : v),
  z.coerce.number().finite().min(0, "Must be ≥ 0")
);
const optionalPositiveInt = z.preprocess(
  emptyToNull,
  z.coerce.number().int("Whole number").min(0, "Must be ≥ 0").nullable()
);
const optionalRating = z.preprocess(
  emptyToNull,
  z.coerce
    .number()
    .min(0, "0–10")
    .max(10, "0–10")
    .nullable()
);
const optionalDate = z.preprocess(emptyToNull, z.coerce.date().nullable());
const checkbox = z.preprocess((v) => v === "on" || v === "true", z.boolean());

const incotermField = z.preprocess(
  emptyToNull,
  z.enum(INCOTERMS).nullable()
);

// --------------------------------------------------------------- materials
export const materialSchema = z.object({
  code: requiredString.max(50),
  name: requiredString.max(200),
  category: z.enum(MATERIAL_CATEGORIES),
  unit: z.enum(QUANTITY_UNITS),
  specification: optionalString,
  preferredSupplierId: z.preprocess(emptyToNull, z.string().nullable()),
  avgMonthlyConsumption: optionalNonNegative,
  avgDailyConsumption: optionalNonNegative,
  forecastMonthlyDemand: optionalNonNegative,
  currentStock: nonNegativeDefaultZero,
  reservedStock: nonNegativeDefaultZero,
  safetyStock: nonNegativeDefaultZero,
  minimumStock: nonNegativeDefaultZero,
  leadTimeDays: optionalPositiveInt,
  openPurchaseQty: nonNegativeDefaultZero,
  inboundQty: nonNegativeDefaultZero,
  inboundEta: optionalDate,
  lastPurchasePrice: optionalNonNegative,
  lastPurchaseDate: optionalDate,
  targetPrice: optionalNonNegative,
  currency: z.enum(CURRENCIES),
  incoterm: incotermField,
  notes: optionalString,
  isActive: checkbox,
  // FormData yields a single string when one box is checked — normalize.
  approvedSupplierIds: z.preprocess(
    (v) => (v == null || v === "" ? [] : Array.isArray(v) ? v : [v]),
    z.array(z.string())
  ),
});
export type MaterialInput = z.infer<typeof materialSchema>;

// --------------------------------------------------------------- suppliers
export const supplierSchema = z.object({
  code: requiredString.max(50),
  name: requiredString.max(200),
  country: requiredString.max(100),
  city: optionalString,
  website: optionalString,
  contactName: optionalString,
  contactEmail: z.preprocess(
    emptyToNull,
    z.string().trim().email("Invalid email").nullable()
  ),
  contactPhone: optionalString,
  preferredCurrency: z.enum(CURRENCIES),
  defaultIncoterm: incotermField,
  paymentTerms: optionalString,
  paymentTermDays: optionalPositiveInt,
  normalLeadTimeDays: optionalPositiveInt,
  qualityRating: optionalRating,
  pricingRating: optionalRating,
  deliveryRating: optionalRating,
  responsivenessRating: optionalRating,
  technicalRating: optionalRating,
  commercialRating: optionalRating,
  notes: optionalString,
  isActive: checkbox,
});
export type SupplierInput = z.infer<typeof supplierSchema>;

// -------------------------------------------------------------- quotations
export const quotationSchema = z.object({
  quotationNumber: requiredString.max(100),
  supplierId: requiredString,
  materialId: requiredString,
  quotationDate: z.coerce.date(),
  quantity: z.coerce.number().positive("Must be > 0"),
  quantityUnit: z.enum(QUANTITY_UNITS),
  containerCount: optionalPositiveInt,
  qtyPerContainer: optionalNonNegative,
  price: z.coerce.number().positive("Must be > 0"),
  currency: z.enum(CURRENCIES),
  priceUnit: z.enum(QUANTITY_UNITS),
  incoterm: incotermField,
  destinationPort: optionalString,
  paymentTerms: optionalString,
  paymentTermDays: optionalPositiveInt,
  leadTimeDays: optionalPositiveInt,
  productionLeadTimeDays: optionalPositiveInt,
  validUntil: optionalDate,
  freightIncluded: checkbox,
  status: z.enum(QUOTATION_STATUSES).default("ACTIVE"),
  remarks: optionalString,
  attachmentRef: optionalString,
});
export type QuotationInput = z.infer<typeof quotationSchema>;

// ------------------------------------------------------------ action items
export const actionItemSchema = z.object({
  title: requiredString.max(300),
  materialId: z.preprocess(emptyToNull, z.string().nullable()),
  supplierId: z.preprocess(emptyToNull, z.string().nullable()),
  quotationId: z.preprocess(emptyToNull, z.string().nullable()),
  actionType: z.enum(ACTION_TYPES),
  owner: optionalString,
  dueDate: optionalDate,
  priority: z.enum(ACTION_PRIORITIES),
  status: z.enum(ACTION_STATUSES),
  notes: optionalString,
});
export type ActionItemInput = z.infer<typeof actionItemSchema>;

// ------------------------------------------------------- market intelligence
export const intelSchema = z.object({
  date: z.coerce.date(),
  title: requiredString.max(300),
  category: z.enum(INTEL_CATEGORIES),
  affectedMaterials: requiredString.max(500),
  geography: optionalString,
  summary: requiredString.max(4000),
  expectedImpact: optionalString,
  priceDirection: z.enum(PRICE_DIRECTIONS),
  supplyImpact: z.preprocess(emptyToNull, z.enum(SUPPLY_IMPACTS).nullable()),
  riskLevel: z.enum(RISK_LEVELS),
  source: optionalString,
  url: optionalString,
  notes: optionalString,
  isActive: checkbox,
});
export type IntelInput = z.infer<typeof intelSchema>;

// ----------------------------------------------------------------- settings
const weightValue = z.coerce.number().min(0).max(100);

export const supplierWeightsSchema = z
  .object({
    quality: weightValue,
    pricing: weightValue,
    delivery: weightValue,
    responsiveness: weightValue,
    technical: weightValue,
    commercial: weightValue,
  })
  .refine(
    (w) =>
      Math.abs(
        w.quality +
          w.pricing +
          w.delivery +
          w.responsiveness +
          w.technical +
          w.commercial -
          100
      ) < 0.01,
    { message: "Weights must sum to 100" }
  );

export const offerWeightsSchema = z
  .object({
    price: weightValue,
    paymentTerms: weightValue,
    leadTime: weightValue,
    supplierScore: weightValue,
  })
  .refine(
    (w) =>
      Math.abs(w.price + w.paymentTerms + w.leadTime + w.supplierScore - 100) <
      0.01,
    { message: "Weights must sum to 100" }
  );

export const recommendationConfigSchema = z.object({
  urgentStockoutFactor: z.coerce.number().min(0).max(1),
  monitorFactor: z.coerce.number().min(1).max(10),
  negotiateThresholdPct: z.coerce.number().min(0).max(100),
  priceSpikeThresholdPct: z.coerce.number().min(0).max(100),
  quotationStaleDays: z.coerce.number().int().min(1).max(365),
  forecastSurgePct: z.coerce.number().min(0).max(500),
});

// ----------------------------------------------------------- form utilities
export type FieldErrors = Record<string, string>;

export function parseForm<T>(
  schema: z.ZodType<T>,
  data: Record<string, unknown>
): { success: true; data: T } | { success: false; fieldErrors: FieldErrors } {
  const result = schema.safeParse(data);
  if (result.success) return { success: true, data: result.data };
  const fieldErrors: FieldErrors = {};
  for (const issue of result.error.issues) {
    const key = issue.path.join(".") || "_form";
    if (!fieldErrors[key]) fieldErrors[key] = issue.message;
  }
  return { success: false, fieldErrors };
}

/** Convert FormData into a plain object; repeated keys become arrays. */
export function formDataToObject(formData: FormData): Record<string, unknown> {
  const obj: Record<string, unknown> = {};
  for (const key of new Set(formData.keys())) {
    if (key.startsWith("$")) continue; // Next.js internal fields
    const values = formData.getAll(key);
    obj[key] = values.length > 1 ? values : values[0];
  }
  return obj;
}
