import { cn } from "@/lib/cn";
import type { ReactNode } from "react";

export type BadgeTone =
  | "neutral"
  | "accent"
  | "ok"
  | "warn"
  | "serious"
  | "crit"
  | "outline";

const tones: Record<BadgeTone, string> = {
  neutral: "bg-sunken text-ink-secondary border-line",
  accent: "bg-accent-soft text-accent-strong border-accent/25",
  ok: "bg-ok-soft text-ok-ink border-ok/30",
  warn: "bg-warn-soft text-warn-ink border-warn/40",
  serious: "bg-serious-soft text-serious-ink border-serious/40",
  crit: "bg-crit-soft text-crit-ink border-crit/35",
  outline: "bg-transparent text-ink-muted border-line-strong",
};

export function Badge({
  tone = "neutral",
  className,
  children,
  title,
}: {
  tone?: BadgeTone;
  className?: string;
  children: ReactNode;
  title?: string;
}) {
  return (
    <span
      title={title}
      className={cn(
        "inline-flex items-center gap-1 whitespace-nowrap rounded border px-1.5 py-px text-[11.5px] font-semibold tracking-wide",
        tones[tone],
        className
      )}
    >
      {children}
    </span>
  );
}
