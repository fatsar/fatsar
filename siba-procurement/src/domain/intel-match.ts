/**
 * Matching market-intelligence entries to materials.
 *
 * `affectedMaterials` is a comma-separated list of material codes and/or
 * category names, or "ALL". Matching is case-insensitive on exact tokens —
 * deterministic, no fuzzy logic.
 */

export interface IntelEntryLike {
  affectedMaterials: string;
  isActive: boolean;
}

export function intelMatchesMaterial(
  entry: IntelEntryLike,
  material: { code: string; category: string }
): boolean {
  if (!entry.isActive) return false;
  const tokens = entry.affectedMaterials
    .split(",")
    .map((t) => t.trim().toUpperCase())
    .filter(Boolean);
  if (tokens.includes("ALL")) return true;
  return (
    tokens.includes(material.code.toUpperCase()) ||
    tokens.includes(material.category.toUpperCase())
  );
}
