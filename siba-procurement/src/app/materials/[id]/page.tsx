import Link from "next/link";
import { notFound } from "next/navigation";
import type { Metadata } from "next";
import { ArrowRight, Pencil, Plus, Scale } from "lucide-react";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { DescriptionList } from "@/components/ui/description-list";
import { PageHeader } from "@/components/ui/page-header";
import { ActionButton } from "@/components/ui/action-feedback";
import {
  ExpiredBadge,
  InactiveBadge,
  RecommendationBadge,
  RiskBadge,
  SampleBadge,
} from "@/components/domain-badges";
import {
  MATERIAL_CATEGORY_LABELS,
  type MaterialCategory,
  type Recommendation,
  type RiskLevel,
} from "@/domain/enums";
import {
  fmtDate,
  fmtDays,
  fmtPct,
  fmtPrice,
  fmtQty,
  titleCase,
} from "@/lib/format";
import { prisma } from "@/lib/prisma";
import {
  deleteMaterial,
  setMaterialActive,
} from "@/server/actions/materials";
import { getMaterialInsight, isQuotationExpired } from "@/server/material-insights";

export const metadata: Metadata = { title: "Material" };

export default async function MaterialDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const insight = await getMaterialInsight(id);
  if (!insight) notFound();

  const { material, coverage, risk, recommendation, priceStats } = insight;
  const now = new Date();

  const quotations = await prisma.quotation.findMany({
    where: { materialId: id },
    include: { supplier: { select: { id: true, name: true } } },
    orderBy: { quotationDate: "desc" },
    take: 15,
  });

  const deactivate = setMaterialActive.bind(null, id, false);
  const reactivate = setMaterialActive.bind(null, id, true);
  const remove = deleteMaterial.bind(null, id);

  return (
    <div className="space-y-4">
      <PageHeader
        title={material.name}
        badges={
          <>
            {material.isSample ? <SampleBadge /> : null}
            {!material.isActive ? <InactiveBadge /> : null}
          </>
        }
        description={
          <>
            {material.code} · {MATERIAL_CATEGORY_LABELS[material.category as MaterialCategory]}
            {material.specification ? <> · {material.specification}</> : null}
          </>
        }
        actions={
          <>
            <Link
              href={`/quotations/new?materialId=${material.id}`}
              className="inline-flex h-9 items-center gap-1.5 rounded-md border border-accent bg-accent px-3.5 text-sm font-medium text-white hover:bg-accent-strong"
            >
              <Plus size={15} /> New quotation
            </Link>
            <Link
              href={`/quotations/compare?materialId=${material.id}`}
              className="inline-flex h-9 items-center gap-1.5 rounded-md border border-line-strong bg-surface px-3.5 text-sm font-medium hover:bg-sunken"
            >
              <Scale size={15} /> Compare offers
            </Link>
            <Link
              href={`/materials/${material.id}/edit`}
              className="inline-flex h-9 items-center gap-1.5 rounded-md border border-line-strong bg-surface px-3.5 text-sm font-medium hover:bg-sunken"
            >
              <Pencil size={14} /> Edit
            </Link>
            {material.isActive ? (
              <ActionButton
                action={deactivate}
                confirmMessage={`Deactivate ${material.name}? It disappears from active planning but keeps all history.`}
              >
                Deactivate
              </ActionButton>
            ) : (
              <>
                <ActionButton action={reactivate}>Reactivate</ActionButton>
                <ActionButton
                  variant="danger"
                  action={remove}
                  confirmMessage={`Delete ${material.name}? Materials with quotations or actions are deactivated instead — nothing with history is ever hard-deleted.`}
                >
                  Delete
                </ActionButton>
              </>
            )}
          </>
        }
      />

      {/* Decision row */}
      <div className="grid gap-4 lg:grid-cols-3">
        <Card>
          <CardHeader
            title="Recommendation"
            actions={<RecommendationBadge value={recommendation.recommendation as Recommendation} />}
          />
          <CardContent>
            <p className="mb-2 text-[13px] text-ink-secondary">
              Confidence:{" "}
              <span className="font-semibold text-ink">{recommendation.confidence}%</span>
              <span className="text-ink-muted"> (deterministic data-quality measure)</span>
            </p>
            <ul className="list-disc space-y-1 pl-4 text-[13px] leading-5">
              {recommendation.reasons.map((r, i) => (
                <li key={i}>{r}</li>
              ))}
            </ul>
            {recommendation.confidenceDeductions.length > 0 ? (
              <details className="mt-3 text-[12.5px] text-ink-secondary">
                <summary className="cursor-pointer font-medium text-ink-muted hover:text-ink">
                  How confidence was calculated
                </summary>
                <p className="mt-1.5">Starts at 100, capped to 25–95. Deductions:</p>
                <ul className="mt-1 list-disc space-y-0.5 pl-4">
                  {recommendation.confidenceDeductions.map((d, i) => (
                    <li key={i}>
                      −{d.points}: {d.reason}
                    </li>
                  ))}
                </ul>
              </details>
            ) : (
              <p className="mt-3 text-[12.5px] text-ink-muted">
                Confidence starts at 100 (capped at 95) — no data-quality deductions applied.
              </p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader
            title="Risk assessment"
            actions={<RiskBadge level={risk.level as RiskLevel} />}
          />
          <CardContent>
            <p className="mb-2 text-[13px] text-ink-secondary">
              Risk score: <span className="font-semibold text-ink">{risk.score}</span>
              <span className="text-ink-muted"> (LOW &lt;20 · MEDIUM ≥20 · HIGH ≥40 · CRITICAL ≥65)</span>
            </p>
            {risk.factors.length === 0 ? (
              <p className="text-[13px] text-ink-secondary">
                No risk rules triggered — position is comfortable.
              </p>
            ) : (
              <ul className="space-y-1.5 text-[13px] leading-5">
                {risk.factors.map((f, i) => (
                  <li key={i} className="flex gap-2">
                    <span className="tnum mt-0.5 shrink-0 rounded bg-sunken px-1 text-[11.5px] font-semibold text-ink-secondary">
                      +{f.points}
                    </span>
                    {f.reason}
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader title="Stock & coverage" />
          <CardContent>
            <DescriptionList
              columns={2}
              items={[
                {
                  label: "Usable stock",
                  value: (
                    <span className="tnum font-semibold">
                      {fmtQty(coverage.usableStock, material.unit)}
                    </span>
                  ),
                },
                {
                  label: "Coverage (basic)",
                  value: <span className="tnum font-semibold">{fmtDays(coverage.coverageDays)}</span>,
                },
                {
                  label: "Projected coverage",
                  value: (
                    <span className="tnum">
                      {coverage.projectedCoverageDays != null
                        ? fmtDays(coverage.projectedCoverageDays)
                        : coverage.dailyDemand != null
                          ? `> ${coverage.horizonDays} d`
                          : "—"}
                    </span>
                  ),
                },
                {
                  label: "Below safety in",
                  value: (
                    <span className="tnum">
                      {coverage.projectedBelowSafetyDay != null
                        ? fmtDays(coverage.projectedBelowSafetyDay)
                        : "—"}
                    </span>
                  ),
                },
                {
                  label: "Safety stock",
                  value: (
                    <span className="tnum">
                      {fmtQty(material.safetyStock, material.unit)}
                      {coverage.safetyStockDays != null
                        ? ` (${fmtDays(coverage.safetyStockDays)})`
                        : ""}
                    </span>
                  ),
                },
                {
                  label: "Reorder point",
                  value: (
                    <span className="tnum">
                      {coverage.reorderPointQty != null
                        ? `${fmtQty(coverage.reorderPointQty, material.unit)} (${fmtDays(coverage.reorderPointDays)})`
                        : "—"}
                    </span>
                  ),
                },
                {
                  label: "Inventory position",
                  value: (
                    <span className="tnum">
                      {fmtQty(coverage.inventoryPosition, material.unit)}
                    </span>
                  ),
                },
                {
                  label: "Daily demand",
                  value: (
                    <span className="tnum">
                      {fmtQty(coverage.dailyDemand, `${material.unit}/day`)}
                      {coverage.demandSource !== "explicit"
                        ? ` (${coverage.demandSource})`
                        : ""}
                    </span>
                  ),
                },
                {
                  label: "Open PO",
                  value: <span className="tnum">{fmtQty(material.openPurchaseQty, material.unit)}</span>,
                },
                {
                  label: "Inbound",
                  value: (
                    <span className="tnum">
                      {material.inboundQty > 0
                        ? `${fmtQty(material.inboundQty, material.unit)} · ETA ${fmtDate(material.inboundEta)}`
                        : "—"}
                    </span>
                  ),
                },
              ]}
            />
          </CardContent>
        </Card>
      </div>

      {/* Price snapshot */}
      <Card>
        <CardHeader
          title="Price snapshot"
          description={
            insight.excludedQuotationCount > 0
              ? `Statistics use ${material.currency} quotations only — ${insight.excludedQuotationCount} quotation(s) in other currencies excluded.`
              : "Deterministic statistics over quotation history."
          }
          actions={
            <Link
              href={`/price-history?materialId=${material.id}`}
              className="inline-flex items-center gap-1 text-[13px] font-medium text-accent-strong hover:underline"
            >
              Price history <ArrowRight size={14} />
            </Link>
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
                    {fmtPrice(priceStats.latest, material.currency, material.unit)}
                  </span>
                ),
              },
              {
                label: "Previous",
                value: (
                  <span className="tnum">
                    {fmtPrice(priceStats.previous, material.currency, material.unit)}
                  </span>
                ),
              },
              {
                label: "Change",
                value: (
                  <span
                    className={
                      "tnum font-medium " +
                      (priceStats.changePct == null
                        ? ""
                        : priceStats.changePct > 0
                          ? "text-serious-ink"
                          : "text-ok-ink")
                    }
                  >
                    {fmtPct(priceStats.changePct)}
                  </span>
                ),
              },
              {
                label: "Target price",
                value: (
                  <span className="tnum">
                    {fmtPrice(material.targetPrice, material.currency, material.unit)}
                  </span>
                ),
              },
              {
                label: "Difference to target",
                value: (
                  <span
                    className={
                      "tnum font-medium " +
                      (priceStats.diffToTargetPct == null
                        ? ""
                        : priceStats.diffToTargetPct > 0
                          ? "text-serious-ink"
                          : "text-ok-ink")
                    }
                  >
                    {fmtPct(priceStats.diffToTargetPct)}
                  </span>
                ),
              },
              {
                label: "3-month average",
                value: <span className="tnum">{fmtPrice(priceStats.avg3m, material.currency, material.unit)}</span>,
              },
              {
                label: "6-month average",
                value: <span className="tnum">{fmtPrice(priceStats.avg6m, material.currency, material.unit)}</span>,
              },
              {
                label: "12-month average",
                value: <span className="tnum">{fmtPrice(priceStats.avg12m, material.currency, material.unit)}</span>,
              },
              {
                label: "12-month range",
                value: (
                  <span className="tnum">
                    {priceStats.min12m != null
                      ? `${fmtPrice(priceStats.min12m, material.currency)} – ${fmtPrice(priceStats.max12m, material.currency)}`
                      : "—"}
                  </span>
                ),
              },
              {
                label: "Last purchase",
                value: (
                  <span className="tnum">
                    {material.lastPurchasePrice != null
                      ? `${fmtPrice(material.lastPurchasePrice, material.currency, material.unit)} · ${fmtDate(material.lastPurchaseDate)}`
                      : "—"}
                  </span>
                ),
              },
            ]}
          />
        </CardContent>
      </Card>

      {/* Quotation history */}
      <Card>
        <CardHeader
          title={`Quotations (${quotations.length} most recent)`}
          actions={
            <Link
              href={`/quotations?materialId=${material.id}`}
              className="inline-flex items-center gap-1 text-[13px] font-medium text-accent-strong hover:underline"
            >
              All quotations <ArrowRight size={14} />
            </Link>
          }
        />
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-[13.5px]">
            <thead>
              <tr className="border-b border-line bg-sunken/60 text-left text-[12px] font-semibold uppercase tracking-wide text-ink-muted">
                <th className="px-3 py-2">Date</th>
                <th className="px-3 py-2">Supplier</th>
                <th className="px-3 py-2">Number</th>
                <th className="px-3 py-2 text-right">Price</th>
                <th className="px-3 py-2 text-right">Normalized</th>
                <th className="hidden px-3 py-2 text-right lg:table-cell">Quantity</th>
                <th className="hidden px-3 py-2 lg:table-cell">Terms</th>
                <th className="px-3 py-2">Validity</th>
              </tr>
            </thead>
            <tbody>
              {quotations.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-3 py-8 text-center text-ink-muted">
                    No quotations recorded yet.
                  </td>
                </tr>
              ) : (
                quotations.map((q) => {
                  const expired = isQuotationExpired(q, now);
                  return (
                    <tr key={q.id} className="border-b border-line last:border-b-0">
                      <td className="tnum whitespace-nowrap px-3 py-2">{fmtDate(q.quotationDate)}</td>
                      <td className="px-3 py-2">
                        <Link href={`/suppliers/${q.supplier.id}`} className="font-medium hover:text-accent-strong">
                          {q.supplier.name}
                        </Link>
                      </td>
                      <td className="px-3 py-2 text-ink-secondary">{q.quotationNumber}</td>
                      <td className="tnum whitespace-nowrap px-3 py-2 text-right">
                        {fmtPrice(q.price, q.currency, q.priceUnit)}
                      </td>
                      <td className="tnum whitespace-nowrap px-3 py-2 text-right font-medium">
                        {fmtPrice(q.normalizedPrice, q.currency, material.unit)}
                      </td>
                      <td className="tnum hidden whitespace-nowrap px-3 py-2 text-right lg:table-cell">
                        {fmtQty(q.quantity, q.quantityUnit)}
                        {q.containerCount ? ` · ${q.containerCount} FCL` : ""}
                      </td>
                      <td className="hidden whitespace-nowrap px-3 py-2 text-ink-secondary lg:table-cell">
                        {[q.incoterm, q.paymentTerms, q.leadTimeDays != null ? `${q.leadTimeDays}d` : null]
                          .filter(Boolean)
                          .join(" · ")}
                      </td>
                      <td className="whitespace-nowrap px-3 py-2">
                        {expired ? (
                          <ExpiredBadge />
                        ) : q.status !== "ACTIVE" ? (
                          <span className="text-[12px] font-medium text-ink-muted">{titleCase(q.status)}</span>
                        ) : (
                          <span className="tnum text-[12.5px] text-ink-secondary">
                            until {fmtDate(q.validUntil)}
                          </span>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </Card>

      {/* Master data */}
      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader title="Master data" />
          <CardContent>
            <DescriptionList
              columns={2}
              items={[
                { label: "Material code", value: material.code },
                {
                  label: "Category",
                  value: MATERIAL_CATEGORY_LABELS[material.category as MaterialCategory],
                },
                { label: "Base unit", value: material.unit },
                { label: "Currency", value: material.currency },
                { label: "Incoterm", value: material.incoterm ?? "—" },
                { label: "Lead time", value: material.leadTimeDays != null ? `${material.leadTimeDays} days` : "—" },
                {
                  label: "Avg monthly consumption",
                  value: <span className="tnum">{fmtQty(material.avgMonthlyConsumption, material.unit)}</span>,
                },
                {
                  label: "Forecast monthly demand",
                  value: <span className="tnum">{fmtQty(material.forecastMonthlyDemand, material.unit)}</span>,
                },
                {
                  label: "Current / reserved stock",
                  value: (
                    <span className="tnum">
                      {fmtQty(material.currentStock, material.unit)} / {fmtQty(material.reservedStock, material.unit)}
                    </span>
                  ),
                },
                {
                  label: "Minimum stock",
                  value: <span className="tnum">{fmtQty(material.minimumStock, material.unit)}</span>,
                },
                { label: "Notes", value: material.notes ?? "—", wide: true },
              ]}
            />
          </CardContent>
        </Card>

        <Card>
          <CardHeader title="Suppliers" />
          <CardContent className="space-y-2">
            {material.preferredSupplier ? (
              <p className="text-[13px]">
                Preferred:{" "}
                <Link
                  href={`/suppliers/${material.preferredSupplier.id}`}
                  className="font-semibold text-accent-strong hover:underline"
                >
                  {material.preferredSupplier.name}
                </Link>
              </p>
            ) : (
              <p className="text-[13px] text-ink-muted">No preferred supplier set.</p>
            )}
            <div>
              <p className="mb-1 text-[12px] font-medium text-ink-muted">
                Approved suppliers ({insight.approvedSupplierCount})
              </p>
              {material.suppliers.length === 0 ? (
                <p className="text-[13px] text-ink-muted">None linked.</p>
              ) : (
                <ul className="space-y-1 text-[13.5px]">
                  {material.suppliers.map((ms) => (
                    <li key={ms.id}>
                      <Link
                        href={`/suppliers/${ms.supplier.id}`}
                        className="font-medium hover:text-accent-strong"
                      >
                        {ms.supplier.name}
                      </Link>{" "}
                      <span className="text-ink-muted">
                        ({ms.supplier.code} · {ms.supplier.country})
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
            {insight.matchedIntel.length > 0 ? (
              <div className="pt-1">
                <p className="mb-1 text-[12px] font-medium text-ink-muted">
                  Market alerts affecting this material
                </p>
                <ul className="space-y-1 text-[13px]">
                  {insight.matchedIntel.map((e) => (
                    <li key={e.id} className="flex items-center gap-2">
                      <RiskBadge level={e.riskLevel as RiskLevel} />
                      <Link href="/market-intelligence" className="hover:text-accent-strong">
                        {e.title}
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
