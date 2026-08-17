"use client";

import { cn } from "@/lib/cn";
import { ArrowDown, ArrowUp, ChevronLeft, ChevronRight, Search } from "lucide-react";
import { useRouter } from "next/navigation";
import { useMemo, useState, type ReactNode } from "react";
import { Input } from "./form";
import { Button } from "./button";

export interface ColumnDef<T> {
  key: string;
  header: ReactNode;
  cell: (row: T) => ReactNode;
  /** Value used for sorting; omit to disable sorting on this column. */
  sortValue?: (row: T) => string | number | null;
  align?: "left" | "right" | "center";
  /** Hide below the lg breakpoint to keep mobile tables focused. */
  hideOnMobile?: boolean;
  headerClassName?: string;
}

export function DataTable<T>({
  rows,
  columns,
  getRowKey,
  searchText,
  searchPlaceholder = "Filter…",
  initialSort,
  initialSortDir = "asc",
  pageSize = 15,
  emptyMessage = "No records.",
  rowHref,
  onRowClick,
  toolbar,
  dimRow,
}: {
  rows: T[];
  columns: ColumnDef<T>[];
  getRowKey: (row: T) => string;
  /** Text blob searched by the filter box; omit to hide the box. */
  searchText?: (row: T) => string;
  searchPlaceholder?: string;
  initialSort?: string;
  initialSortDir?: "asc" | "desc";
  pageSize?: number;
  emptyMessage?: string;
  rowHref?: (row: T) => string | null;
  /** Click handler alternative to rowHref (e.g. open an edit dialog). */
  onRowClick?: (row: T) => void;
  /** Extra controls rendered next to the search box. */
  toolbar?: ReactNode;
  /** Render the row at reduced opacity (e.g. inactive records). */
  dimRow?: (row: T) => boolean;
}) {
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [sortKey, setSortKey] = useState<string | null>(initialSort ?? null);
  const [sortDir, setSortDir] = useState<"asc" | "desc">(initialSortDir);
  const [page, setPage] = useState(0);

  const filtered = useMemo(() => {
    if (!query.trim() || !searchText) return rows;
    const q = query.trim().toLowerCase();
    return rows.filter((r) => searchText(r).toLowerCase().includes(q));
  }, [rows, query, searchText]);

  const sorted = useMemo(() => {
    if (!sortKey) return filtered;
    const col = columns.find((c) => c.key === sortKey);
    if (!col?.sortValue) return filtered;
    const dir = sortDir === "asc" ? 1 : -1;
    return [...filtered].sort((a, b) => {
      const va = col.sortValue!(a);
      const vb = col.sortValue!(b);
      if (va == null && vb == null) return 0;
      if (va == null) return 1; // nulls last
      if (vb == null) return -1;
      if (typeof va === "number" && typeof vb === "number") {
        return (va - vb) * dir;
      }
      return String(va).localeCompare(String(vb)) * dir;
    });
  }, [filtered, sortKey, sortDir, columns]);

  const pageCount = Math.max(1, Math.ceil(sorted.length / pageSize));
  const clampedPage = Math.min(page, pageCount - 1);
  const pageRows = sorted.slice(
    clampedPage * pageSize,
    (clampedPage + 1) * pageSize
  );

  const toggleSort = (key: string) => {
    if (sortKey === key) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortDir("asc");
    }
    setPage(0);
  };

  const alignClass = (a?: "left" | "right" | "center") =>
    a === "right" ? "text-right" : a === "center" ? "text-center" : "text-left";

  return (
    <div>
      {(searchText || toolbar) && (
        <div className="flex flex-wrap items-center gap-2 border-b border-line px-3 py-2.5">
          {searchText ? (
            <div className="relative">
              <Search
                size={14}
                className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-ink-muted"
              />
              <Input
                value={query}
                onChange={(e) => {
                  setQuery(e.target.value);
                  setPage(0);
                }}
                placeholder={searchPlaceholder}
                className="h-8 w-56 pl-8 text-[13px]"
                aria-label="Filter table"
              />
            </div>
          ) : null}
          {toolbar}
          <span className="ml-auto text-[12px] text-ink-muted">
            {sorted.length} of {rows.length}
          </span>
        </div>
      )}
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-[13.5px]">
          <thead>
            <tr className="border-b border-line bg-sunken/60">
              {columns.map((col) => (
                <th
                  key={col.key}
                  className={cn(
                    "whitespace-nowrap px-3 py-2 text-[12px] font-semibold uppercase tracking-wide text-ink-muted",
                    alignClass(col.align),
                    col.hideOnMobile && "hidden lg:table-cell",
                    col.headerClassName
                  )}
                >
                  {col.sortValue ? (
                    <button
                      type="button"
                      onClick={() => toggleSort(col.key)}
                      className={cn(
                        "inline-flex items-center gap-1 hover:text-ink",
                        sortKey === col.key && "text-ink"
                      )}
                    >
                      {col.header}
                      {sortKey === col.key ? (
                        sortDir === "asc" ? (
                          <ArrowUp size={12} />
                        ) : (
                          <ArrowDown size={12} />
                        )
                      ) : null}
                    </button>
                  ) : (
                    col.header
                  )}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {pageRows.length === 0 ? (
              <tr>
                <td
                  colSpan={columns.length}
                  className="px-3 py-8 text-center text-ink-muted"
                >
                  {emptyMessage}
                </td>
              </tr>
            ) : (
              pageRows.map((row) => {
                const href = rowHref?.(row) ?? null;
                const clickable = href != null || onRowClick != null;
                return (
                  <tr
                    key={getRowKey(row)}
                    onClick={
                      href
                        ? () => router.push(href)
                        : onRowClick
                          ? () => onRowClick(row)
                          : undefined
                    }
                    className={cn(
                      "border-b border-line last:border-b-0",
                      clickable && "cursor-pointer hover:bg-accent-soft/40",
                      dimRow?.(row) && "opacity-55"
                    )}
                  >
                    {columns.map((col) => (
                      <td
                        key={col.key}
                        className={cn(
                          "px-3 py-2 align-middle",
                          alignClass(col.align),
                          col.hideOnMobile && "hidden lg:table-cell"
                        )}
                      >
                        {col.cell(row)}
                      </td>
                    ))}
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
      {pageCount > 1 && (
        <div className="flex items-center justify-between border-t border-line px-3 py-2">
          <span className="text-[12px] text-ink-muted">
            Page {clampedPage + 1} of {pageCount}
          </span>
          <div className="flex gap-1">
            <Button
              size="sm"
              variant="ghost"
              disabled={clampedPage === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              aria-label="Previous page"
            >
              <ChevronLeft size={15} />
            </Button>
            <Button
              size="sm"
              variant="ghost"
              disabled={clampedPage >= pageCount - 1}
              onClick={() => setPage((p) => Math.min(pageCount - 1, p + 1))}
              aria-label="Next page"
            >
              <ChevronRight size={15} />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
