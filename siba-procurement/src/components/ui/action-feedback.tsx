"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition, type ReactNode } from "react";
import { Button, type ButtonProps } from "./button";
import type { FormState } from "@/server/actions/form-state";

/**
 * Button that runs a server action (optionally after a confirm prompt) and
 * surfaces the returned message inline. Used for row-level operations like
 * deactivate / delete / mark-status.
 */
export function ActionButton({
  action,
  confirmMessage,
  children,
  onDone,
  ...buttonProps
}: {
  action: () => Promise<FormState>;
  confirmMessage?: string;
  children: ReactNode;
  onDone?: (state: FormState) => void;
} & Omit<ButtonProps, "onClick" | "children">) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [message, setMessage] = useState<FormState | null>(null);

  return (
    <span className="inline-flex flex-col items-start gap-1">
      <Button
        {...buttonProps}
        disabled={pending || buttonProps.disabled}
        onClick={(e) => {
          e.stopPropagation();
          if (confirmMessage && !window.confirm(confirmMessage)) return;
          startTransition(async () => {
            const state = await action();
            setMessage(state);
            onDone?.(state);
            router.refresh();
            if (state.ok) {
              setTimeout(() => setMessage(null), 6000);
            }
          });
        }}
      >
        {pending ? "…" : children}
      </Button>
      {message?.message ? (
        <span
          role="status"
          className={
            message.ok
              ? "max-w-64 text-[12px] leading-4 text-ok-ink"
              : "max-w-64 text-[12px] leading-4 text-crit-ink"
          }
        >
          {message.message}
        </span>
      ) : null}
    </span>
  );
}
