import { cn } from "@/lib/cn";
import type {
  InputHTMLAttributes,
  LabelHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from "react";

/** Form primitives with consistent industrial styling + error display. */

export function Label({
  className,
  ...props
}: LabelHTMLAttributes<HTMLLabelElement>) {
  return (
    <label
      className={cn("mb-1 block text-[12.5px] font-medium text-ink-secondary", className)}
      {...props}
    />
  );
}

const fieldBase =
  "w-full rounded-md border border-line-strong bg-surface px-2.5 text-sm text-ink " +
  "placeholder:text-ink-muted focus:border-accent focus:outline-2 focus:outline-accent/30 " +
  "disabled:opacity-60 disabled:bg-sunken";

export function Input({
  className,
  invalid,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }) {
  return (
    <input
      className={cn(fieldBase, "h-9", invalid && "border-crit", className)}
      {...props}
    />
  );
}

export function Select({
  className,
  invalid,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean }) {
  return (
    <select
      className={cn(fieldBase, "h-9 pr-8", invalid && "border-crit", className)}
      {...props}
    >
      {children}
    </select>
  );
}

export function Textarea({
  className,
  invalid,
  ...props
}: TextareaHTMLAttributes<HTMLTextAreaElement> & { invalid?: boolean }) {
  return (
    <textarea
      className={cn(fieldBase, "min-h-20 py-2", invalid && "border-crit", className)}
      {...props}
    />
  );
}

export function FieldError({ message }: { message?: string }) {
  if (!message) return null;
  return <p className="mt-1 text-[12px] font-medium text-crit-ink">{message}</p>;
}

/** Label + control + error in one block. */
export function Field({
  label,
  htmlFor,
  error,
  hint,
  children,
  className,
}: {
  label: ReactNode;
  htmlFor?: string;
  error?: string;
  hint?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={className}>
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
      {hint && !error ? (
        <p className="mt-1 text-[12px] text-ink-muted">{hint}</p>
      ) : null}
      <FieldError message={error} />
    </div>
  );
}

export function FormMessage({
  ok,
  message,
}: {
  ok?: boolean;
  message?: string;
}) {
  if (!message) return null;
  return (
    <p
      role="status"
      className={cn(
        "rounded-md border px-3 py-2 text-[13px] font-medium",
        ok
          ? "border-ok/30 bg-ok-soft text-ok-ink"
          : "border-crit/30 bg-crit-soft text-crit-ink"
      )}
    >
      {message}
    </p>
  );
}
