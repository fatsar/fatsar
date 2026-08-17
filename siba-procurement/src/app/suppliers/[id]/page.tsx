import Link from "next/link";
import { notFound } from "next/navigation";
import type { Metadata } from "next";
import { ExternalLink, Pencil } from "lucide-react";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { DescriptionList } from "@/components/ui/description-list";
import { PageHeader } from "@/components/ui/page-header";
import { ActionButton } from "@/components/ui/action-feedback";
import {
  ExpiredBadge,
  InactiveBadge,
  SampleBadge,
} from "@/components/domain-badges";
import { calculateSupplierScore } from "@/domain/supplier-score";
import { fmtDate, fmtPrice, fmtQty } from "@/lib/format";
import { prisma } from "@/lib/prisma";
import { setSupplierActive } from "@/server/actions/suppliers";
import { isQuotationExpired } from "@/server/material-insights";
import { getSupplierScoreWeights } from "@/server/settings";

export const metadata: Metadata = { title: "Supplier" };

export default async function SupplierDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const [supplier, weights] = await Promise.all([
    prisma.supplier.findUnique({
      where: { id },
      include: {
        materials: {
          include: {
            material: { select: { id: true, name: true, code: true } },
          },
        },
        quotations: {
          include: { material: { select: { id: true, name: true, unit: true } } },
          orderBy: { quotationDate: "desc" },
          take: 15,
        },
      },
    }),
    getSupplierScoreWeights(),
  ]);
  if (!supplier) notFound();

  const score = calculateSupplierScore(supplier, weights);
  const now = new Date();
  const deactivate = setSupplierActive.bind(null, id, false);
  const reactivate = setSupplierActive.bind(null, id, true);

  return (
    <div className="space-y-4">
      <PageHeader
        title={supplier.name}
        badges={
          <>
            {supplier.isSample ? <SampleBadge /> : null}
            {!supplier.isActive ? <InactiveBadge /> : null}
          </>
        }
        description={`${supplier.code} · ${supplier.country}${supplier.city ? `, ${supplier.city}` : ""}`}
        actions={
          <>
            <Link
              href={`/suppliers/${supplier.id}/edit`}
              className="inline-flex h-9 items-center gap-1.5 rounded-md border border-line-strong bg-surface px-3.5 text-sm font-medium hover:bg-sunken"
            >
              <Pencil size={14} /> Edit
            </Link>
            {supplier.isActive ? (
              <ActionButton
                action={deactivate}
                confirmMessage={`Deactivate ${supplier.name}? Existing quotations and links are kept.`}
              >
                Deactivate
              </ActionButton>
            ) : (
              <ActionButton action={reactivate}>Reactivate</ActionButton>
            )}
          </>
        }
      />

      <div className="grid gap-4 lg:grid-cols-3">
        {/* Score card */}
        <Card>
          <CardHeader
            title="Supplier score"
            description="Weighted 0–100 — weights are configurable in Settings."
          />
          <CardContent>
            <p className="mb-3">
              <span className="text-[32px] font-semibold leading-9">
                {score.score != null ? score.score.toFixed(1) : "—"}
              </span>
              <span className="text-ink-muted"> /100</span>
            </p>
            <table className="w-full text-[13px]">
              <thead>
                <tr className="text-left text-[11.5px] font-semibold uppercase tracking-wide text-ink-muted">
                  <th className="pb-1">Dimension</th>
                  <th className="pb-1 text-right">Rating</th>
                  <th className="pb-1 text-right">Weight</th>
                  <th className="pb-1 text-right">Points</th>
                </tr>
              </thead>
              <tbody>
                {score.breakdown.map((b) => (
                  <tr key={b.dimension} className="border-t border-line">
                    <td className="py-1.5">{b.label}</td>
                    <td className="tnum py-1.5 text-right">
                      {b.rating != null ? `${b.rating}/10` : "—"}
                    </td>
                    <td className="tnum py-1.5 text-right text-ink-muted">{b.weight}</td>
                    <td className="tnum py-1.5 text-right font-medium">
                      {b.contribution != null ? b.contribution.toFixed(1) : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {score.ratedWeightSum > 0 && score.ratedWeightSum < score.totalWeightSum ? (
              <p className="mt-2 text-[12px] text-ink-muted">
                Unrated dimensions are excluded and remaining weights renormalized.
              </p>
            ) : null}
          </CardContent>
        </Card>

        {/* Master data */}
        <Card className="lg:col-span-2">
          <CardHeader title="Master data" />
          <CardContent>
            <DescriptionList
              columns={3}
              items={[
                {
                  label: "Website",
                  value: supplier.website ? (
                    <a
                      href={supplier.website}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex items-center gap-1 text-accent-strong hover:underline"
                    >
                      {supplier.website.replace(/^https?:\/\//, "")}
                      <ExternalLink size={12} />
                    </a>
                  ) : (
                    "—"
                  ),
                },
                { label: "Contact person", value: supplier.contactName ?? "—" },
                {
                  label: "Email",
                  value: supplier.contactEmail ? (
                    <a href={`mailto:${supplier.contactEmail}`} className="text-accent-strong hover:underline">
                      {supplier.contactEmail}
                    </a>
                  ) : (
                    "—"
                  ),
                },
                { label: "Phone", value: supplier.contactPhone ?? "—" },
                { label: "Preferred currency", value: supplier.preferredCurrency },
                { label: "Normal Incoterm", value: supplier.defaultIncoterm ?? "—" },
                {
                  label: "Payment terms",
                  value:
                    supplier.paymentTerms ??
                    (supplier.paymentTermDays != null ? `${supplier.paymentTermDays} days` : "—"),
                },
                {
                  label: "Normal lead time",
                  value:
                    supplier.normalLeadTimeDays != null
                      ? `${supplier.normalLeadTimeDays} days`
                      : "—",
                },
                { label: "Notes", value: supplier.notes ?? "—", wide: true },
              ]}
            />
            <div className="mt-4 border-t border-line pt-3">
              <p className="mb-1 text-[12px] font-medium text-ink-muted">
                Approved materials ({supplier.materials.length})
              </p>
              {supplier.materials.length === 0 ? (
                <p className="text-[13px] text-ink-muted">No materials linked.</p>
              ) : (
                <div className="flex flex-wrap gap-1.5">
                  {supplier.materials.map((ms) => (
                    <Link
                      key={ms.id}
                      href={`/materials/${ms.material.id}`}
                      className="rounded border border-line bg-sunken px-2 py-0.5 text-[12.5px] font-medium hover:border-accent hover:text-accent-strong"
                    >
                      {ms.material.name}
                    </Link>
                  ))}
                </div>
              )}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Quotations */}
      <Card>
        <CardHeader title={`Recent quotations (${supplier.quotations.length})`} />
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-[13.5px]">
            <thead>
              <tr className="border-b border-line bg-sunken/60 text-left text-[12px] font-semibold uppercase tracking-wide text-ink-muted">
                <th className="px-3 py-2">Date</th>
                <th className="px-3 py-2">Material</th>
                <th className="px-3 py-2">Number</th>
                <th className="px-3 py-2 text-right">Price</th>
                <th className="hidden px-3 py-2 text-right lg:table-cell">Quantity</th>
                <th className="px-3 py-2">Validity</th>
              </tr>
            </thead>
            <tbody>
              {supplier.quotations.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-3 py-8 text-center text-ink-muted">
                    No quotations recorded.
                  </td>
                </tr>
              ) : (
                supplier.quotations.map((q) => (
                  <tr key={q.id} className="border-b border-line last:border-b-0">
                    <td className="tnum whitespace-nowrap px-3 py-2">{fmtDate(q.quotationDate)}</td>
                    <td className="px-3 py-2">
                      <Link href={`/materials/${q.material.id}`} className="font-medium hover:text-accent-strong">
                        {q.material.name}
                      </Link>
                    </td>
                    <td className="px-3 py-2 text-ink-secondary">{q.quotationNumber}</td>
                    <td className="tnum whitespace-nowrap px-3 py-2 text-right font-medium">
                      {fmtPrice(q.normalizedPrice, q.currency, q.material.unit)}
                    </td>
                    <td className="tnum hidden whitespace-nowrap px-3 py-2 text-right lg:table-cell">
                      {fmtQty(q.quantity, q.quantityUnit)}
                    </td>
                    <td className="whitespace-nowrap px-3 py-2">
                      {isQuotationExpired(q, now) ? (
                        <ExpiredBadge />
                      ) : (
                        <span className="tnum text-[12.5px] text-ink-secondary">
                          until {fmtDate(q.validUntil)}
                        </span>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}
