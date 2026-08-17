import Link from "next/link";
import type { Metadata } from "next";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { DescriptionList } from "@/components/ui/description-list";
import { PageHeader } from "@/components/ui/page-header";
import { calculatePriceStats } from "@/domain/price-stats";
import { fmtDate, fmtPct, fmtPrice, fmtQty } from "@/lib/format";
import { prisma } from "@/lib/prisma";
import { PriceChart, type PricePointDto } from "./price-chart";
import { PriceHistoryFilterBar } from "./filter-bar";

export const metadata: Metadata = { title: "Price history" };

const RANGE_DAYS: Record<string, number | null> = {
  "3m": 90,
  "6m": 180,
  "12m": 365,
  all: null,
};

export default async function PriceHistoryPage({
  searchParams,
}: {
  searchParams: Promise<{
    materialId?: string;
    category?: string;
    supplierId?: string;
    range?: string;
  }>;
}) {
  const params = await searchParams;
  const range = params.range && params.range in RANGE_DAYS ? params.range : "12m";
  const category = params.category ?? "";
  const supplierId = params.supplierId ?? "";

  const materials = await prisma.material.findMany({
    where: { quotations: { some: {} } },
    select: {
      id: true,
      name: true,
      code: true,
      category: true,
      unit: true,
      currency: true,
      targetPrice: true,
      _count: { select: { quotations: true } },
    },
    orderBy: { name: "asc" },
  });

  // Default to the material with the richest history so the page is
  // immediately informative; an explicit selection always wins.
  const byHistory = [...materials].sort(
    (a, b) => b._count.quotations - a._count.quotations
  );
  const material =
    materials.find((m) => m.id === params.materialId) ??
    (category
      ? byHistory.find((m) => m.category === category)
      : byHistory[0]) ??
    null;

  const now = new Date();
  let chartPoints: PricePointDto[] = [];
  let supplierOrder: string[] = [];
  let supplierOptions: { id: string; name: string }[] = [];
  let stats = null;
  let excludedCurrencyCount = 0;
  let tableRows: {
    id: string;
    date: Date;
    supplier: string;
    supplierId: string;
    quotationNumber: string;
    price: number;
    priceUnit: string;
    normalizedPrice: number;
    currency: string;
    quantity: number;
    quantityUnit: string;
  }[] = [];

  if (material) {
    const quotations = await prisma.quotation.findMany({
      where: { materialId: material.id },
      include: { supplier: { select: { id: true, name: true } } },
      orderBy: { quotationDate: "desc" },
    });

    supplierOptions = [...new Map(quotations.map((q) => [q.supplier.id, q.supplier])).values()].sort(
      (a, b) => a.name.localeCompare(b.name)
    );
    // Fixed color order: every supplier that ever quoted, alphabetically —
    // stable regardless of the active filter.
    supplierOrder = supplierOptions.map((s) => s.name);

    const rangeDays = RANGE_DAYS[range];
    const inWindow = quotations.filter(
      (q) =>
        (rangeDays == null ||
          now.getTime() - q.quotationDate.getTime() <= rangeDays * 86_400_000) &&
        (supplierId === "" || q.supplier.id === supplierId)
    );
    const sameCurrency = inWindow.filter((q) => q.currency === material.currency);
    excludedCurrencyCount = inWindow.length - sameCurrency.length;

    chartPoints = sameCurrency.map((q) => ({
      ts: q.quotationDate.getTime(),
      supplier: q.supplier.name,
      price: q.normalizedPrice,
    }));

    stats = calculatePriceStats(
      sameCurrency.map((q) => ({
        date: q.quotationDate,
        price: q.normalizedPrice,
        supplierName: q.supplier.name,
      })),
      material.targetPrice,
      now
    );

    tableRows = sameCurrency.map((q) => ({
      id: q.id,
      date: q.quotationDate,
      supplier: q.supplier.name,
      supplierId: q.supplier.id,
      quotationNumber: q.quotationNumber,
      price: q.price,
      priceUnit: q.priceUnit,
      normalizedPrice: q.normalizedPrice,
      currency: q.currency,
      quantity: q.quantity,
      quantityUnit: q.quantityUnit,
    }));
  }

  return (
    <div className="space-y-4">
      <PageHeader
        title="Price History"
        description="Normalized quotation prices over time — deterministic averages, extremes and target comparison."
      />

      <Card>
        <CardContent className="py-3">
          <PriceHistoryFilterBar
            materials={materials}
            suppliers={supplierOptions}
            materialId={material?.id ?? ""}
            category={category}
            supplierId={supplierId}
            range={range}
          />
        </CardContent>
      </Card>

      {!material ? (
        <Card>
          <CardContent className="py-10 text-center text-ink-muted">
            No materials with quotations yet.
          </CardContent>
        </Card>
      ) : (
        <>
          {stats ? (
            <Card>
              <CardHeader
                title={`${material.name} — price metrics`}
                description={
                  excludedCurrencyCount > 0
                    ? `${material.currency} quotations only — ${excludedCurrencyCount} in other currencies excluded (no FX conversion in Phase 1).`
                    : `All prices normalized to ${material.currency} per ${material.unit}.`
                }
              />
              <CardContent>
                <DescriptionList
                  columns={4}
                  items={[
                    {
                      label: "Latest quotation",
                      value: (
                        <span className="tnum font-semibold">
                          {fmtPrice(stats.latest, material.currency, material.unit)}
                        </span>
                      ),
                    },
                    {
                      label: "Previous",
                      value: <span className="tnum">{fmtPrice(stats.previous, material.currency, material.unit)}</span>,
                    },
                    {
                      label: "Change",
                      value: (
                        <span
                          className={
                            "tnum font-medium " +
                            (stats.changePct == null
                              ? ""
                              : stats.changePct > 0
                                ? "text-serious-ink"
                                : "text-ok-ink")
                          }
                        >
                          {fmtPct(stats.changePct)}
                        </span>
                      ),
                    },
                    {
                      label: "Target / difference",
                      value: (
                        <span className="tnum">
                          {fmtPrice(material.targetPrice, material.currency, material.unit)}
                          {stats.diffToTargetPct != null ? (
                            <span
                              className={
                                "ml-1 font-medium " +
                                (stats.diffToTargetPct > 0 ? "text-serious-ink" : "text-ok-ink")
                              }
                            >
                              ({fmtPct(stats.diffToTargetPct)})
                            </span>
                          ) : null}
                        </span>
                      ),
                    },
                    {
                      label: "3-month average",
                      value: <span className="tnum">{fmtPrice(stats.avg3m, material.currency, material.unit)}</span>,
                    },
                    {
                      label: "6-month average",
                      value: <span className="tnum">{fmtPrice(stats.avg6m, material.currency, material.unit)}</span>,
                    },
                    {
                      label: "12-month average",
                      value: <span className="tnum">{fmtPrice(stats.avg12m, material.currency, material.unit)}</span>,
                    },
                    {
                      label: "12-month min / max",
                      value: (
                        <span className="tnum">
                          {stats.min12m != null
                            ? `${fmtPrice(stats.min12m, material.currency)} / ${fmtPrice(stats.max12m, material.currency)}`
                            : "—"}
                        </span>
                      ),
                    },
                  ]}
                />
              </CardContent>
            </Card>
          ) : null}

          <Card>
            <CardHeader
              title="Price trend"
              description={`Normalized ${material.currency}/${material.unit} · one line per supplier`}
            />
            <CardContent>
              <PriceChart
                points={chartPoints}
                supplierOrder={supplierOrder}
                targetPrice={material.targetPrice}
                currency={material.currency}
                unit={material.unit}
              />
            </CardContent>
          </Card>

          <Card>
            <CardHeader title={`Price points (${tableRows.length})`} />
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-[13.5px]">
                <thead>
                  <tr className="border-b border-line bg-sunken/60 text-left text-[12px] font-semibold uppercase tracking-wide text-ink-muted">
                    <th className="px-3 py-2">Date</th>
                    <th className="px-3 py-2">Supplier</th>
                    <th className="hidden px-3 py-2 lg:table-cell">Number</th>
                    <th className="px-3 py-2 text-right">As quoted</th>
                    <th className="px-3 py-2 text-right">Normalized</th>
                    <th className="hidden px-3 py-2 text-right lg:table-cell">Quantity</th>
                  </tr>
                </thead>
                <tbody>
                  {tableRows.map((r) => (
                    <tr key={r.id} className="border-b border-line last:border-b-0">
                      <td className="tnum whitespace-nowrap px-3 py-2">{fmtDate(r.date)}</td>
                      <td className="px-3 py-2">
                        <Link href={`/suppliers/${r.supplierId}`} className="font-medium hover:text-accent-strong">
                          {r.supplier}
                        </Link>
                      </td>
                      <td className="hidden px-3 py-2 text-ink-secondary lg:table-cell">
                        {r.quotationNumber}
                      </td>
                      <td className="tnum whitespace-nowrap px-3 py-2 text-right">
                        {fmtPrice(r.price, r.currency, r.priceUnit)}
                      </td>
                      <td className="tnum whitespace-nowrap px-3 py-2 text-right font-medium">
                        {fmtPrice(r.normalizedPrice, r.currency, material.unit)}
                      </td>
                      <td className="tnum hidden whitespace-nowrap px-3 py-2 text-right lg:table-cell">
                        {fmtQty(r.quantity, r.quantityUnit)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </>
      )}
    </div>
  );
}
