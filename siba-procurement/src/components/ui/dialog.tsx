"use client";

import { cn } from "@/lib/cn";
import { X } from "lucide-react";
import { useEffect, useRef, type ReactNode } from "react";

/**
 * Modal dialog on the native <dialog> element — focus management, Escape and
 * backdrop handling come from the platform.
 */
export function Dialog({
  open,
  onClose,
  title,
  children,
  wide,
}: {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
  wide?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    if (open && !el.open) el.showModal();
    if (!open && el.open) el.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onClick={(e) => {
        // click on the backdrop (the dialog element itself) closes
        if (e.target === ref.current) onClose();
      }}
      className={cn(
        "m-auto w-[calc(100vw-2rem)] rounded-lg border border-line bg-surface p-0 text-ink shadow-xl",
        "backdrop:bg-ink/40",
        wide ? "max-w-3xl" : "max-w-xl"
      )}
    >
      <div className="flex items-center justify-between border-b border-line px-4 py-3">
        <h2 className="text-[15px] font-semibold">{title}</h2>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close"
          className="rounded p-1 text-ink-muted hover:bg-sunken hover:text-ink"
        >
          <X size={16} />
        </button>
      </div>
      <div className="max-h-[75vh] overflow-y-auto p-4">{open ? children : null}</div>
    </dialog>
  );
}
