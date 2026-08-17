import Link from "next/link";
import type { Metadata } from "next";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { Badge } from "@/components/ui/badge";
import { ExpiredBadge } from "@/components/domain-badges";
import {
  compareOffers,
  type ComparableOffer,
  type OfferBadge,
  type ScoredOffer,
} from "@/domain/offer-comparison";
import { calculateSupplierScore } from "@/domain/supplier-score";
import { fmtDate, fmtPrice, fmtQty } from "@/lib/format";
import { prisma } from "@/lib/prisma";
import { isQuotationExpired } from "@/server/material-insights";
import { getOfferScoreWeights, getSupplierScoreWeights } from "@/server/settings";

export const metadata: Metadata = { title: "Compare offers" };

const BADGE_LABELS: Record<OfferBadge, string> = {
  LOWEST_PRICE: "LOWEST PRICE",
  BEST_PAYMENT_TERMS: "BEST PAYMENT TERMS",
  SHORTEST_LEAD_TIME: "SHORTEST LEAD TIME",
  BEST_SUPPLIER_SCORE: "BEST SUPPLIER SCORE",
  BEST_OVERALL: "BEST OVERALL OFFER",
};

export default async function ComparePage({
  searchParams,
}: {
  searchParams: Promise<{ materialId?: string }>;
}) {
  const { materialId } = await searchParams;
  const materials = await prisma.material.findMany({
    where: { quotations: { some: {} } },
    select: { id: true, name: true, code: true, unit: true, currency: true, targetPrice: true },
    orderBy: { name: "asc" },
  });
  const selected = materials.find((m) => m.id === materialId) ?? null;

  let comparison = null;
  let offerRows: {
    offer: ScoredOffer;
    quotation: {
      id: string;
      quotationNumber: string;
      quotationDate: Date;
      validUntil: Date | null;
      quantity: number;
      quantityUnit: string;
      price: number;
      priceUnit: string;
      paymentTerms: string | null;
      incoterm: string | null;
      supplierId: string;
    };
  }[] = [];

  if (selected) {
    const now = new Date();
    const [quotations, supplierWeights, offerWeights] = await Promise.all([
      prisma.quotation.findMany({
        where: { materialId: selected.id },
        include: { supplier: true },
        orderBy: { quotationDate: "desc" },
      }),
      getSupplierScoreWeights(),
      getOfferScoreWeights(),
    ]);

    // Latest quotation per supplier = the comparison set.
    const latestBySupplier = new Map<string, (typeof quotations)[number]>();
    for (const q of quotations) {
      if (!latestBySupplier.has(q.supplierId)) latestBySupplier.set(q.supplierId, q);
    }
    const latest = [...latestBySupplier.values()];

    const offers: ComparableOffer[] = latest.map((q) => ({
      id: q.id,
      supplierName: q.supplier.name,
      supplierScore: calculateSupplierScore(q.supplier, supplierWeights).score,
      normalizedPrice: q.normalizedPrice,
      currency: q.currency,
      paymentTermDays: q.paymentTermDays,
      leadTimeDays: q.leadTimeDays,
      isExpired: isQuotationExpired(q, now),
    }));

    comparison = compareOffers(offers, offerWeights);
    offerRows = comparison.offers.map((offer) => {
      const q = latest.find((x) => x.id === offer.id)!;
      return {
        offer,
        quotation: {
          id: q.id,
          quotationNumber: q.quotationNumber,
          quotationDate: q.quotationDate,
          validUntil: q.validUntil,
          quantity: q.quantity,
          quantityUnit: q.quantityUnit,
          price: q.price,
          priceUnit: q.priceUnit,
          paymentTerms: q.paymentTerms,
          incoterm: q.incoterm,
          supplierId: q.supplierId,
        },
      };
    });
  }

  return (
    <div className="space-y-4">
      <PageHeader
        title="Compare offers"
        description="Latest quotation per supplier, scored on price, payment terms, lead time and supplier score. The Best Overall calculation is fully shown — nothing is hidden."
      />

      {/* Material picker */}
      <Card>
        <CardContent className="flex flex-wrap items-center gap-2 py-3">
          <span className="text-[13px] font-medium text-ink-secondary">Material:</span>
          {materials.map((m) => (
            <Link
              key={m.id}
              href={`/quotations/compare?materialId=${m.id}`}
              className={
                "rounded-md border px-2.5 py-1 text-[13px] font-medium " +
                (m.id === selected?.id
                  ? "border-accent bg-accent-soft text-accent-strong"
                  : "border-line bg-surface text-ink-secondary hover:bg-sunken")
              }
            >
              {m.name}
            </Link>
          ))}
        </CardContent>
      </Card>

      {!selected ? (
        <Card>
          <CardContent className="py-10 text-center text-ink-muted">
            Select a material above to compare its offers.
          </CardContent>
        </Card>
      ) : comparison && comparison.offers.length === 0 ? (
        <Card>
          <CardContent className="py-10 text-center text-ink-muted">
            No quotations recorded for {selected.name} yet.{" "}
            <Link
              href={`/quotations/new?materialId=${selected.id}`}
              className="font-medium text-accent-strong hover:underline"
            >
              Add the first one.
            </Link>
          </CardContent>
        </Card>
      ) : comparison ? (
        <>
          {comparison.warnings.length > 0 ? (
            <div className="space-y-1">
              {comparison.warnings.map((w, i) => (
                <p
                  key={i}
                  className="rounded-md border border-warn/40 bg-warn-soft px-3 py-2 text-[13px] font-medium text-warn-ink"
                >
                  {w}
                </p>
              ))}
            </div>
          ) : null}

          <Card>
            <CardHeader
              title={`${selected.name} — offer comparison`}
              description={
                selected.targetPrice != null
                  ? `Target price: ${fmtPrice(selected.targetPrice, selected.currency, selected.unit)}`
                  : "No target price set."
              }
            />
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-[13.5px]">
                <thead>
                  <tr className="border-b border-line bg-sunken/60 text-left text-[12px] font-semibold uppercase tracking-wide text-ink-muted">
                    <th className="px-3 py-2">Supplier</th>
                    <th className="px-3 py-2 text-right">Price</th>
                    <th className="px-3 py-2 text-right">Normalized</th>
                    <th className="hidden px-3 py-2 lg:table-cell">Payment</th>
                    <th className="hidden px-3 py-2 lg:table-cell">Incoterm</th>
                    <th className="px-3 py-2 text-right">Lead time</th>
                    <th className="hidden px-3 py-2 text-right lg:table-cell">Quantity</th>
                    <th className="hidden px-3 py-2 lg:table-cell">Date</th>
                    <th className="px-3 py-2 text-right">Supplier score</th>
                    <th className="px-3 py-2 text-right">Overall</th>
                  </tr>
                </thead>
                <tbody>
                  {offerRows.map(({ offer, quotation }) => (
                    <tr
                      key={offer.id}
                      className={
                        "border-b border-line align-top last:border-b-0 " +
                        (offer.badges.includes("BEST_OVERALL") ? "bg-ok-soft/40" : "")
                      }
                    >
                      <td className="px-3 py-2.5">
                        <Link
                          href={`/suppliers/${quotation.supplierId}`}
                          className="font-semibold hover:text-accent-strong"
                        >
                          {offer.supplierName}
                        </Link>
                        <span className="block text-[12px] text-ink-muted">
                          {quotation.quotationNumber}
                        </span>
                        <span className="mt-1 flex flex-wrap gap-1">
                          {offer.isExpired ? <ExpiredBadge /> : null}
                          {offer.badges.map((b) => (
                            <Badge
                              key={b}
                              tone={b === "BEST_OVERALL" ? "ok" : "accent"}
                            >
                              {BADGE_LABELS[b]}
                            </Badge>
                          ))}
                        </span>
                      </td>
                      <td className="tnum whitespace-nowrap px-3 py-2.5 text-right">
                        {fmtPrice(quotation.price, offer.currency, quotation.priceUnit)}
                      </td>
                      <td className="tnum whitespace-nowrap px-3 py-2.5 text-right font-semibold">
                        {fmtPrice(offer.normalizedPrice, offer.currency, selected.unit)}
                      </td>
                      <td className="hidden whitespace-nowrap px-3 py-2.5 text-ink-secondary lg:table-cell">
                        {quotation.paymentTerms ??
                          (offer.paymentTermDays != null ? `${offer.paymentTermDays} days` : "—")}
                      </td>
                      <td className="hidden px-3 py-2.5 text-ink-secondary lg:table-cell">
                        {quotation.incoterm ?? "—"}
                      </td>
                      <td className="tnum whitespace-nowrap px-3 py-2.5 text-right">
                        {offer.leadTimeDays != null ? `${offer.leadTimeDays} d` : "—"}
                      </td>
                      <td className="tnum hidden whitespace-nowrap px-3 py-2.5 text-right lg:table-cell">
                        {fmtQty(quotation.quantity, quotation.quantityUnit)}
                      </td>
                      <td className="tnum hidden whitespace-nowrap px-3 py-2.5 lg:table-cell">
                        {fmtDate(quotation.quotationDate)}
                      </td>
                      <td className="tnum px-3 py-2.5 text-right">
                        {offer.supplierScore != null ? offer.supplierScore.toFixed(0) : "—"}
                      </td>
                      <td className="tnum px-3 py-2.5 text-right text-[15px] font-semibold">
                        {offer.overallScore != null ? offer.overallScore.toFixed(1) : "—"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          <Card>
            <CardHeader
              title="How the overall score is calculated"
              description="Per dimension: score 0–100 (best offer = 100), multiplied by its weight. Weights are configurable in Settings. Missing data scores 0 and is marked."
            />
            <CardContent className="grid gap-4 lg:grid-cols-2">
              {offerRows.map(({ offer }) => (
                <div key={offer.id} className="rounded-md border border-line p-3">
                  <p className="mb-2 text-[13.5px] font-semibold">
                    {offer.supplierName}
                    {offer.isExpired ? (
                      <span className="ml-2 align-middle">
                        <ExpiredBadge />
                      </span>
                    ) : null}
                    <span className="float-right tnum">
                      {offer.overallScore != null ? `${offer.overallScore.toFixed(1)} pts` : "—"}
                    </span>
                  </p>
                  <table className="w-full text-[12.5px]">
                    <thead>
                      <tr className="text-left text-[11px] font-semibold uppercase tracking-wide text-ink-muted">
                        <th className="pb-1">Dimension</th>
                        <th className="pb-1 text-right">Value</th>
                        <th className="pb-1 text-right">Score</th>
                        <th className="pb-1 text-right">Weight</th>
                        <th className="pb-1 text-right">Points</th>
                      </tr>
                    </thead>
                    <tbody>
                      {offer.dimensions.map((d) => (
                        <tr key={d.dimension} className="border-t border-line">
                          <td className="py-1">{d.label}</td>
                          <td className="tnum py-1 text-right">
                            {d.rawValue == null
                              ? "missing"
                              : d.dimension === "price"
                                ? fmtPrice(d.rawValue, offer.currency, selected.unit)
                                : d.dimension === "supplierScore"
                                  ? d.rawValue.toFixed(0)
                                  : `${d.rawValue} d`}
                          </td>
                          <td className="tnum py-1 text-right">
                            {d.score != null ? d.score.toFixed(1) : "—"}
                          </td>
                          <td className="tnum py-1 text-right text-ink-muted">{d.weight}%</td>
                          <td className="tnum py-1 text-right font-medium">
                            {d.weightedPoints.toFixed(1)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ))}
            </CardContent>
          </Card>
        </>
      ) : null}
    </div>
  );
}
