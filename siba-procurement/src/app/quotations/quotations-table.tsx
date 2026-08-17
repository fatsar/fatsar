"use client";

import Link from "next/link";
import { Pencil } from "lucide-react";
import { useMemo, useState } from "react";
import { DataTable, type ColumnDef } from "@/components/ui/data-table";
import { Select } from "@/components/ui/form";
import { ExpiredBadge, SampleBadge } from "@/components/domain-badges";
import { fmtDate, fmtPrice, fmtQty, titleCase } from "@/lib/format";

export interface QuotationRow {
  id: string;
  quotationNumber: string;
  materialId: string;
  materialName: string;
  materialUnit: string;
  supplierId: string;
  supplierName: string;
  quotationDate: string; // ISO
  quantity: number;
  quantityUnit: string;
  containerCount: number | null;
  price: number;
  currency: string;
  priceUnit: string;
  normalizedPrice: number;
  incoterm: string | null;
  paymentTerms: string | null;
  leadTimeDays: number | null;
  validUntil: string | null; // ISO
  status: string;
  isExpired: boolean;
  isSample: boolean;
}

export function QuotationsTable({
  rows,
  materials,
  initialMaterialId,
}: {
  rows: QuotationRow[];
  materials: { id: string; name: string }[];
  initialMaterialId?: string;
}) {
  const [materialId, setMaterialId] = useState(initialMaterialId ?? "");
  const [validOnly, setValidOnly] = useState(false);

  const filtered = useMemo(
    () =>
      rows.filter(
        (r) =>
          (materialId === "" || r.materialId === materialId) &&
          (!validOnly || !r.isExpired)
      ),
    [rows, materialId, validOnly]
  );

  const columns: ColumnDef<QuotationRow>[] = [
    {
      key: "date",
      header: "Date",
      sortValue: (r) => r.quotationDate,
      cell: (r) => (
        <span className="tnum whitespace-nowrap">{fmtDate(r.quotationDate)}</span>
      ),
    },
    {
      key: "number",
      header: "Number",
      sortValue: (r) => r.quotationNumber,
      hideOnMobile: true,
      cell: (r) => (
        <span className="text-ink-secondary">
          {r.quotationNumber} {r.isSample ? <SampleBadge /> : null}
        </span>
      ),
    },
    {
      key: "material",
      header: "Material",
      sortValue: (r) => r.materialName,
      cell: (r) => (
        <Link
          href={`/materials/${r.materialId}`}
          onClick={(e) => e.stopPropagation()}
          className="font-medium hover:text-accent-strong"
        >
          {r.materialName}
        </Link>
      ),
    },
    {
      key: "supplier",
      header: "Supplier",
      sortValue: (r) => r.supplierName,
      cell: (r) => (
        <Link
          href={`/suppliers/${r.supplierId}`}
          onClick={(e) => e.stopPropagation()}
          className="hover:text-accent-strong"
        >
          {r.supplierName}
        </Link>
      ),
    },
    {
      key: "qty",
      header: "Quantity",
      align: "right",
      hideOnMobile: true,
      sortValue: (r) => r.quantity,
      cell: (r) => (
        <span className="tnum">
          {fmtQty(r.quantity, r.quantityUnit)}
          {r.containerCount ? ` · ${r.containerCount} FCL` : ""}
        </span>
      ),
    },
    {
      key: "price",
      header: "Price",
      align: "right",
      hideOnMobile: true,
      sortValue: (r) => r.price,
      cell: (r) => (
        <span className="tnum">{fmtPrice(r.price, r.currency, r.priceUnit)}</span>
      ),
    },
    {
      key: "normalized",
      header: "Normalized",
      align: "right",
      sortValue: (r) => r.normalizedPrice,
      cell: (r) => (
        <span className="tnum font-medium">
          {fmtPrice(r.normalizedPrice, r.currency, r.materialUnit)}
        </span>
      ),
    },
    {
      key: "terms",
      header: "Terms",
      hideOnMobile: true,
      cell: (r) => (
        <span className="whitespace-nowrap text-ink-secondary">
          {[r.incoterm, r.paymentTerms, r.leadTimeDays != null ? `${r.leadTimeDays}d` : null]
            .filter(Boolean)
            .join(" · ")}
        </span>
      ),
    },
    {
      key: "validity",
      header: "Validity",
      sortValue: (r) => r.validUntil,
      cell: (r) =>
        r.isExpired ? (
          <ExpiredBadge />
        ) : r.status !== "ACTIVE" ? (
          <span className="text-[12px] font-medium text-ink-muted">
            {titleCase(r.status)}
          </span>
        ) : (
          <span className="tnum text-[12.5px] text-ink-secondary">
            until {fmtDate(r.validUntil)}
          </span>
        ),
    },
    {
      key: "edit",
      header: "",
      cell: (r) => (
        <Link
          href={`/quotations/${r.id}/edit`}
          onClick={(e) => e.stopPropagation()}
          aria-label={`Edit ${r.quotationNumber}`}
          className="inline-flex rounded p-1 text-ink-muted hover:bg-sunken hover:text-ink"
        >
          <Pencil size={14} />
        </Link>
      ),
    },
  ];

  return (
    <DataTable
      rows={filtered}
      columns={columns}
      getRowKey={(r) => r.id}
      searchText={(r) => `${r.quotationNumber} ${r.materialName} ${r.supplierName}`}
      searchPlaceholder="Filter quotations…"
      initialSort="date"
      initialSortDir="desc"
      rowHref={(r) => `/quotations/${r.id}/edit`}
      dimRow={(r) => r.isExpired}
      pageSize={20}
      toolbar={
        <>
          <Select
            aria-label="Filter by material"
            value={materialId}
            onChange={(e) => setMaterialId(e.target.value)}
            className="h-8 w-52 text-[13px]"
          >
            <option value="">All materials</option>
            {materials.map((m) => (
              <option key={m.id} value={m.id}>
                {m.name}
              </option>
            ))}
          </Select>
          <label className="flex items-center gap-1.5 text-[12.5px] text-ink-secondary">
            <input
              type="checkbox"
              checked={validOnly}
              onChange={(e) => setValidOnly(e.target.checked)}
            />
            Valid only
          </label>
        </>
      }
    />
  );
}
