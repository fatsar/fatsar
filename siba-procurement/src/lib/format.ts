/** Deterministic display formatting — shared by tables, cards and charts. */

export function fmtNumber(value: number | null | undefined, decimals = 0): string {
  if (value == null || Number.isNaN(value)) return "—";
  return value.toLocaleString("en-US", {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

/** Quantities: whole numbers unless small. */
export function fmtQty(value: number | null | undefined, unit?: string): string {
  if (value == null || Number.isNaN(value)) return "—";
  const decimals = Math.abs(value) < 10 && value !== 0 ? 1 : 0;
  return `${fmtNumber(value, decimals)}${unit ? ` ${unit}` : ""}`;
}

/** Unit prices keep enough precision to compare offers (e.g. 1.79 vs 1.795). */
export function fmtPrice(
  value: number | null | undefined,
  currency?: string,
  perUnit?: string
): string {
  if (value == null || Number.isNaN(value)) return "—";
  const decimals = Math.abs(value) >= 100 ? 0 : Math.abs(value) >= 10 ? 2 : 3;
  const num = value.toLocaleString("en-US", {
    minimumFractionDigits: Math.min(2, decimals),
    maximumFractionDigits: decimals,
  });
  const cur = currency ? `${currencySymbol(currency)}` : "";
  return `${cur}${num}${perUnit ? `/${perUnit}` : ""}`;
}

export function currencySymbol(currency: string): string {
  switch (currency) {
    case "USD":
      return "$";
    case "EUR":
      return "€";
    case "TRY":
      return "₺";
    case "GBP":
      return "£";
    case "CNY":
      return "¥";
    default:
      return `${currency} `;
  }
}

/** Large money amounts, compact: $1.28M */
export function fmtMoneyCompact(value: number, currency: string): string {
  const abs = Math.abs(value);
  const sym = currencySymbol(currency);
  if (abs >= 1_000_000) return `${sym}${(value / 1_000_000).toFixed(2)}M`;
  if (abs >= 10_000) return `${sym}${Math.round(value / 1_000)}K`;
  return `${sym}${fmtNumber(value)}`;
}

export function fmtPct(value: number | null | undefined, signed = true): string {
  if (value == null || Number.isNaN(value)) return "—";
  const sign = signed && value > 0 ? "+" : "";
  return `${sign}${value.toFixed(2)}%`;
}

export function fmtDays(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return "—";
  return `${fmtNumber(value, value < 10 ? 1 : 0)} d`;
}

export function fmtDate(value: Date | string | null | undefined): string {
  if (!value) return "—";
  const d = typeof value === "string" ? new Date(value) : value;
  return d.toLocaleDateString("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
}

export function fmtDateInput(value: Date | string | null | undefined): string {
  if (!value) return "";
  const d = typeof value === "string" ? new Date(value) : value;
  return d.toISOString().slice(0, 10);
}

export function daysUntil(value: Date | null | undefined): number | null {
  if (!value) return null;
  return Math.ceil((value.getTime() - Date.now()) / 86_400_000);
}

export function titleCase(token: string): string {
  return token
    .toLowerCase()
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}
