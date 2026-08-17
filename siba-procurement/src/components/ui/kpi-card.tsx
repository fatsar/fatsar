import { cn } from "@/lib/cn";
import Link from "next/link";
import type { ReactNode } from "react";

/**
 * Stat tile: label · value · optional hint line. Tone tints the value when the
 * number itself is a signal (e.g. overdue count > 0) — the label text always
 * carries the meaning, color is secondary.
 */
export function KpiCard({
  label,
  value,
  hint,
  href,
  tone = "default",
}: {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  href?: string;
  tone?: "default" | "attention" | "critical" | "positive";
}) {
  const valueColor =
    tone === "critical"
      ? "text-crit-ink"
      : tone === "attention"
        ? "text-serious-ink"
        : tone === "positive"
          ? "text-ok-ink"
          : "text-ink";

  const inner = (
    <div
      className={cn(
        "h-full rounded-lg border border-line bg-surface px-3.5 py-3 transition-colors",
        href && "hover:border-accent/50 hover:bg-accent-soft/30"
      )}
    >
      <div className="text-[12px] font-medium leading-4 text-ink-secondary">
        {label}
      </div>
      <div className={cn("mt-1.5 text-[26px] font-semibold leading-8", valueColor)}>
        {value}
      </div>
      {hint ? (
        <div className="mt-1 truncate text-[12px] leading-4 text-ink-muted">{hint}</div>
      ) : null}
    </div>
  );

  return href ? (
    <Link href={href} className="block h-full">
      {inner}
    </Link>
  ) : (
    inner
  );
}
