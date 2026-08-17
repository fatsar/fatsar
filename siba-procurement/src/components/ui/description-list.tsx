import { cn } from "@/lib/cn";
import type { ReactNode } from "react";

export interface DlItem {
  label: string;
  value: ReactNode;
  /** Span the full row (for long text). */
  wide?: boolean;
}

/** Compact key–value grid for detail pages. */
export function DescriptionList({
  items,
  columns = 3,
  className,
}: {
  items: DlItem[];
  columns?: 2 | 3 | 4;
  className?: string;
}) {
  const cols =
    columns === 2
      ? "sm:grid-cols-2"
      : columns === 4
        ? "sm:grid-cols-2 lg:grid-cols-4"
        : "sm:grid-cols-2 lg:grid-cols-3";
  return (
    <dl className={cn("grid grid-cols-1 gap-x-6 gap-y-3", cols, className)}>
      {items.map((item, i) => (
        <div key={i} className={cn(item.wide && "sm:col-span-full")}>
          <dt className="text-[12px] font-medium text-ink-muted">{item.label}</dt>
          <dd className="mt-0.5 text-[13.5px] text-ink">{item.value ?? "—"}</dd>
        </div>
      ))}
    </dl>
  );
}
