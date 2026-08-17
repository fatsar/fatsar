import type { QuantityUnit } from "./enums";

/**
 * Deterministic unit conversion for quantities and prices.
 *
 * Only mass units (kg ↔ MT) are interconvertible. Volume (L) and piece (pcs)
 * units never convert across dimensions — converting L→kg would require a
 * material density, which Phase 1 deliberately does not model.
 */
const KG_PER_MT = 1000;

export function canConvert(from: QuantityUnit, to: QuantityUnit): boolean {
  if (from === to) return true;
  return (from === "kg" && to === "MT") || (from === "MT" && to === "kg");
}

/** Convert a quantity between units. Throws on incompatible dimensions. */
export function convertQuantity(
  value: number,
  from: QuantityUnit,
  to: QuantityUnit
): number {
  if (from === to) return value;
  if (from === "MT" && to === "kg") return value * KG_PER_MT;
  if (from === "kg" && to === "MT") return value / KG_PER_MT;
  throw new Error(`Cannot convert quantity from ${from} to ${to}`);
}

/**
 * Convert a unit price (money per `from` unit) to money per `to` unit.
 * Example: 1790 USD/MT → 1.79 USD/kg.
 */
export function convertPrice(
  pricePerFrom: number,
  from: QuantityUnit,
  to: QuantityUnit
): number {
  if (from === to) return pricePerFrom;
  if (from === "MT" && to === "kg") return pricePerFrom / KG_PER_MT;
  if (from === "kg" && to === "MT") return pricePerFrom * KG_PER_MT;
  throw new Error(`Cannot convert price from per-${from} to per-${to}`);
}

/** Round to a sensible number of decimals for money-per-unit values. */
export function roundPrice(value: number, decimals = 4): number {
  const f = 10 ** decimals;
  return Math.round(value * f) / f;
}
