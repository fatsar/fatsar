"use client";

import { cn } from "@/lib/cn";
import { Search } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";

interface SearchItem {
  id: string;
  title: string;
  subtitle: string;
  href: string;
}
interface SearchGroup {
  label: string;
  items: SearchItem[];
}

/** Debounced global search across materials, suppliers, quotations, actions, intel. */
export function GlobalSearch({ className }: { className?: string }) {
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [groups, setGroups] = useState<SearchGroup[]>([]);
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const [loading, setLoading] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  const flat = groups.flatMap((g) => g.items);

  // Debounced fetch — all state updates happen inside the timeout callback,
  // never synchronously in the effect body.
  useEffect(() => {
    if (query.trim().length < 2) return;
    const t = setTimeout(async () => {
      setLoading(true);
      try {
        const res = await fetch(`/api/search?q=${encodeURIComponent(query)}`);
        const data = (await res.json()) as { groups: SearchGroup[] };
        setGroups(data.groups);
        setOpen(true);
        setActiveIndex(0);
      } catch {
        setGroups([]);
      } finally {
        setLoading(false);
      }
    }, 220);
    return () => clearTimeout(t);
  }, [query]);

  const handleQueryChange = (value: string) => {
    setQuery(value);
    if (value.trim().length < 2) {
      setGroups([]);
      setOpen(false);
    }
  };

  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onClick);
    return () => document.removeEventListener("mousedown", onClick);
  }, []);

  const go = (item: SearchItem) => {
    setOpen(false);
    setQuery("");
    router.push(item.href);
  };

  return (
    <div ref={rootRef} className={cn("relative", className)}>
      <Search
        size={15}
        className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-muted"
      />
      <input
        value={query}
        onChange={(e) => handleQueryChange(e.target.value)}
        onFocus={() => flat.length > 0 && setOpen(true)}
        onKeyDown={(e) => {
          if (e.key === "Escape") setOpen(false);
          if (!open || flat.length === 0) return;
          if (e.key === "ArrowDown") {
            e.preventDefault();
            setActiveIndex((i) => Math.min(i + 1, flat.length - 1));
          } else if (e.key === "ArrowUp") {
            e.preventDefault();
            setActiveIndex((i) => Math.max(i - 1, 0));
          } else if (e.key === "Enter" && flat[activeIndex]) {
            e.preventDefault();
            go(flat[activeIndex]);
          }
        }}
        placeholder="Search materials, suppliers, quotations…  (e.g. HS3130, Wanhua)"
        aria-label="Global search"
        className="h-9 w-full rounded-md border border-line-strong bg-surface pl-9 pr-3 text-[13.5px] placeholder:text-ink-muted focus:border-accent focus:outline-2 focus:outline-accent/30"
      />
      {open && (
        <div className="absolute left-0 right-0 top-10 z-50 max-h-96 overflow-y-auto rounded-md border border-line bg-surface shadow-lg">
          {flat.length === 0 ? (
            <div className="px-3 py-3 text-[13px] text-ink-muted">
              {loading ? "Searching…" : `No results for “${query}”.`}
            </div>
          ) : (
            groups.map((group) => (
              <div key={group.label} className="py-1">
                <div className="px-3 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wide text-ink-muted">
                  {group.label}
                </div>
                {group.items.map((item) => {
                  const idx = flat.indexOf(item);
                  return (
                    <button
                      key={item.id}
                      type="button"
                      onMouseEnter={() => setActiveIndex(idx)}
                      onClick={() => go(item)}
                      className={cn(
                        "block w-full px-3 py-1.5 text-left",
                        idx === activeIndex && "bg-accent-soft"
                      )}
                    >
                      <span className="block truncate text-[13.5px] font-medium text-ink">
                        {item.title}
                      </span>
                      <span className="block truncate text-[12px] text-ink-muted">
                        {item.subtitle}
                      </span>
                    </button>
                  );
                })}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
