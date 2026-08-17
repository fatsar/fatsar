/**
 * Single source of truth for every classification value in the system.
 *
 * SQLite cannot enforce Prisma enums, so these unions are enforced by zod in
 * every server action (src/lib/validation) and rendered through the label
 * maps below. When the database moves to PostgreSQL these become DB enums.
 */

export const MATERIAL_CATEGORIES = [
  "PMDI",
  "POLYETHER_POLYOLS",
  "SILICONE",
  "OH_POLYMER",
  "CHLORINATED_PARAFFIN",
  "DME",
  "METHANOL",
  "HYDROCARBONS",
  "ADDITIVES",
  "PACKAGING",
  "OTHER",
] as const;
export type MaterialCategory = (typeof MATERIAL_CATEGORIES)[number];

export const MATERIAL_CATEGORY_LABELS: Record<MaterialCategory, string> = {
  PMDI: "PMDI",
  POLYETHER_POLYOLS: "Polyether Polyols",
  SILICONE: "Silicone",
  OH_POLYMER: "OH Polymer",
  CHLORINATED_PARAFFIN: "Chlorinated Paraffin",
  DME: "DME",
  METHANOL: "Methanol",
  HYDROCARBONS: "Hydrocarbons",
  ADDITIVES: "Additives",
  PACKAGING: "Packaging",
  OTHER: "Other",
};

export const QUANTITY_UNITS = ["kg", "MT", "L", "pcs"] as const;
export type QuantityUnit = (typeof QUANTITY_UNITS)[number];

export const RISK_LEVELS = ["LOW", "MEDIUM", "HIGH", "CRITICAL"] as const;
export type RiskLevel = (typeof RISK_LEVELS)[number];

export const RECOMMENDATIONS = [
  "URGENT_PURCHASE",
  "BUY_NOW",
  "SECURE_SUPPLY",
  "REQUEST_QUOTATION",
  "NEGOTIATE",
  "WAIT",
  "MONITOR",
] as const;
export type Recommendation = (typeof RECOMMENDATIONS)[number];

export const RECOMMENDATION_LABELS: Record<Recommendation, string> = {
  URGENT_PURCHASE: "URGENT PURCHASE",
  BUY_NOW: "BUY NOW",
  SECURE_SUPPLY: "SECURE SUPPLY",
  REQUEST_QUOTATION: "REQUEST QUOTATION",
  NEGOTIATE: "NEGOTIATE",
  WAIT: "WAIT",
  MONITOR: "MONITOR",
};

export const QUOTATION_STATUSES = [
  "ACTIVE",
  "ACCEPTED",
  "REJECTED",
  "SUPERSEDED",
] as const;
export type QuotationStatus = (typeof QUOTATION_STATUSES)[number];

export const ACTION_TYPES = [
  "RFQ",
  "NEGOTIATION",
  "PURCHASE",
  "SUPPLIER_FOLLOW_UP",
  "SAMPLE",
  "QUALITY",
  "LOGISTICS",
  "DOCUMENTATION",
  "MARKET_CHECK",
  "OTHER",
] as const;
export type ActionType = (typeof ACTION_TYPES)[number];

export const ACTION_TYPE_LABELS: Record<ActionType, string> = {
  RFQ: "RFQ",
  NEGOTIATION: "Negotiation",
  PURCHASE: "Purchase",
  SUPPLIER_FOLLOW_UP: "Supplier Follow-up",
  SAMPLE: "Sample",
  QUALITY: "Quality",
  LOGISTICS: "Logistics",
  DOCUMENTATION: "Documentation",
  MARKET_CHECK: "Market Check",
  OTHER: "Other",
};

export const ACTION_STATUSES = [
  "OPEN",
  "IN_PROGRESS",
  "WAITING_SUPPLIER",
  "WAITING_INTERNAL",
  "COMPLETED",
  "CANCELLED",
] as const;
export type ActionStatus = (typeof ACTION_STATUSES)[number];

export const ACTION_STATUS_LABELS: Record<ActionStatus, string> = {
  OPEN: "Open",
  IN_PROGRESS: "In Progress",
  WAITING_SUPPLIER: "Waiting Supplier",
  WAITING_INTERNAL: "Waiting Internal",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
};

export const ACTION_PRIORITIES = ["LOW", "MEDIUM", "HIGH", "URGENT"] as const;
export type ActionPriority = (typeof ACTION_PRIORITIES)[number];

export const PRICE_DIRECTIONS = ["UP", "DOWN", "NEUTRAL", "UNCERTAIN"] as const;
export type PriceDirection = (typeof PRICE_DIRECTIONS)[number];

export const SUPPLY_IMPACTS = ["NONE", "MINOR", "MODERATE", "SEVERE"] as const;
export type SupplyImpact = (typeof SUPPLY_IMPACTS)[number];

export const INTEL_CATEGORIES = [
  "MARKET",
  "MACRO",
  "GEOPOLITICAL",
  "FREIGHT",
  "SUPPLIER",
  "REGULATORY",
] as const;
export type IntelCategory = (typeof INTEL_CATEGORIES)[number];

export const INTEL_CATEGORY_LABELS: Record<IntelCategory, string> = {
  MARKET: "Market / Price",
  MACRO: "Macro / FX",
  GEOPOLITICAL: "Geopolitical",
  FREIGHT: "Freight / Logistics",
  SUPPLIER: "Supplier",
  REGULATORY: "Regulatory",
};

export const INCOTERMS = [
  "EXW",
  "FCA",
  "FOB",
  "CFR",
  "CIF",
  "CPT",
  "CIP",
  "DAP",
  "DPU",
  "DDP",
] as const;
export type Incoterm = (typeof INCOTERMS)[number];

export const CURRENCIES = ["USD", "EUR", "TRY", "CNY", "GBP"] as const;
export type Currency = (typeof CURRENCIES)[number];
