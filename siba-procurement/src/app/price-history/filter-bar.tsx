"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Select } from "@/components/ui/form";
import { MATERIAL_CATEGORY_LABELS } from "@/domain/enums";

export interface MaterialOption {
  id: string;
  name: string;
  category: string;
}

/** URL-driven filters: material, category, supplier, date range. */
export function PriceHistoryFilterBar({
  materials,
  suppliers,
  materialId,
  category,
  supplierId,
  range,
}: {
  materials: MaterialOption[];
  suppliers: { id: string; name: string }[];
  materialId: string;
  category: string;
  supplierId: string;
  range: string;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const setParams = (updates: Record<string, string>) => {
    const params = new URLSearchParams(searchParams.toString());
    for (const [key, value] of Object.entries(updates)) {
      if (value) params.set(key, value);
      else params.delete(key);
    }
    router.push(`${pathname}?${params.toString()}`);
  };

  const filteredMaterials = category
    ? materials.filter((m) => m.category === category)
    : materials;

  return (
    <div className="flex flex-wrap items-end gap-3">
      <div>
        <span className="mb-1 block text-[12px] font-medium text-ink-muted">Category</span>
        <Select
          aria-label="Category"
          value={category}
          onChange={(e) => setParams({ category: e.target.value, materialId: "" })}
          className="h-8 w-44 text-[13px]"
        >
          <option value="">All categories</option>
          {Object.entries(MATERIAL_CATEGORY_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
      </div>
      <div>
        <span className="mb-1 block text-[12px] font-medium text-ink-muted">Material</span>
        <Select
          aria-label="Material"
          value={materialId}
          onChange={(e) => setParams({ materialId: e.target.value, supplierId: "" })}
          className="h-8 w-56 text-[13px]"
        >
          <option value="">— select material —</option>
          {filteredMaterials.map((m) => (
            <option key={m.id} value={m.id}>
              {m.name}
            </option>
          ))}
        </Select>
      </div>
      <div>
        <span className="mb-1 block text-[12px] font-medium text-ink-muted">Supplier</span>
        <Select
          aria-label="Supplier"
          value={supplierId}
          onChange={(e) => setParams({ supplierId: e.target.value })}
          className="h-8 w-48 text-[13px]"
        >
          <option value="">All suppliers</option>
          {suppliers.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </Select>
      </div>
      <div>
        <span className="mb-1 block text-[12px] font-medium text-ink-muted">Range</span>
        <Select
          aria-label="Date range"
          value={range}
          onChange={(e) => setParams({ range: e.target.value })}
          className="h-8 w-36 text-[13px]"
        >
          <option value="3m">Last 3 months</option>
          <option value="6m">Last 6 months</option>
          <option value="12m">Last 12 months</option>
          <option value="all">All history</option>
        </Select>
      </div>
    </div>
  );
}
