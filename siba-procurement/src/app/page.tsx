import Link from "next/link";
import { AlertTriangle, ArrowRight } from "lucide-react";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { KpiCard } from "@/components/ui/kpi-card";
import { PageHeader } from "@/components/ui/page-header";
import {
  ActionStatusBadge,
  PriorityBadge,
  RecommendationBadge,
  RiskBadge,
} from "@/components/domain-badges";
import { getDashboardData } from "@/server/dashboard";
import { prisma } from "@/lib/prisma";
import {
  fmtDate,
  fmtDays,
  fmtMoneyCompact,
  fmtPrice,
  titleCase,
} from "@/lib/format";
import type {
  ActionPriority,
  ActionStatus,
  Recommendation,
  RiskLevel,
} from "@/domain/enums";

export default async function DashboardPage() {
  const now = new Date();
  const [{ kpis, insights }, openActions, activeAlerts] = await Promise.all([
    getDashboardData(),
    prisma.actionItem.findMany({
      where: { status: { notIn: ["COMPLETED", "CANCELLED"] } },
      include: {
        material: { select: { name: true } },
        supplier: { select: { name: true } },
      },
      orderBy: [{ dueDate: "asc" }],
      take: 8,
    }),
    prisma.marketIntelligence.findMany({
      where: { isActive: true, riskLevel: { in: ["HIGH", "CRITICAL"] } },
      orderBy: { date: "desc" },
      take: 5,
    }),
  ]);

  const priority = insights
    .filter((i) =>
      ["URGENT_PURCHASE", "BUY_NOW", "SECURE_SUPPLY", "REQUEST_QUOTATION", "NEGOTIATE"].includes(
        i.recommendation.recommendation
      )
    )
    .slice(0, 10);

  const purchaseValue = kpis.purchaseValueUnderReview;

  return (
    <div className="space-y-5">
      <PageHeader
        title="Dashboard"
        description={`Decision overview for ${fmtDate(now)} — what needs attention, what to buy, what can wait.`}
      />

      {/* KPI grid */}
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <KpiCard
          label="Materials requiring action"
          value={kpis.materialsRequiringAction}
          hint="Urgent, buy, secure, RFQ or negotiate"
          href="/plan"
          tone={kpis.materialsRequiringAction > 0 ? "attention" : "default"}
        />
        <KpiCard
          label="High-risk materials"
          value={kpis.highRiskMaterials}
          hint="Risk level HIGH or CRITICAL"
          href="/plan"
          tone={kpis.highRiskMaterials > 0 ? "critical" : "positive"}
        />
        <KpiCard
          label="RFQs pending"
          value={kpis.rfqsPending}
          hint="Open RFQ actions"
          href="/actions"
        />
        <KpiCard
          label="Purchase value under review"
          value={
            purchaseValue.length === 0
              ? "—"
              : fmtMoneyCompact(purchaseValue[0].value, purchaseValue[0].currency)
          }
          hint={
            purchaseValue.length > 1
              ? `plus ${purchaseValue
                  .slice(1)
                  .map((v) => fmtMoneyCompact(v.value, v.currency))
                  .join(", ")} · valid quotations`
              : "Σ valid quotations (qty × price)"
          }
          href="/quotations"
        />
        <KpiCard
          label="Supplier responses pending"
          value={kpis.supplierResponsesPending}
          hint="Actions waiting on a supplier"
          href="/actions"
        />
        <KpiCard
          label="Average stock coverage"
          value={
            kpis.avgStockCoverageDays != null
              ? `${Math.round(kpis.avgStockCoverageDays)} d`
              : "—"
          }
          hint="Mean across active materials"
          href="/plan"
        />
        <KpiCard
          label="Price increase alerts"
          value={kpis.priceIncreaseAlerts}
          hint="Latest quotation above previous"
          href="/price-history"
          tone={kpis.priceIncreaseAlerts > 0 ? "attention" : "default"}
        />
        <KpiCard
          label="Overdue purchasing actions"
          value={kpis.overdueActions}
          hint="Past due date, not completed"
          href="/actions"
          tone={kpis.overdueActions > 0 ? "critical" : "positive"}
        />
      </div>

      {/* Priority purchasing actions */}
      <Card>
        <CardHeader
          title="Priority Purchasing Actions"
          description="Materials the recommendation engine says to act on now — ranked by urgency, then risk."
          actions={
            <Link
              href="/plan"
              className="inline-flex items-center gap-1 text-[13px] font-medium text-accent-strong hover:underline"
            >
              Full purchasing plan <ArrowRight size={14} />
            </Link>
          }
        />
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-[13.5px]">
            <thead>
              <tr className="border-b border-line bg-sunken/60 text-left text-[12px] font-semibold uppercase tracking-wide text-ink-muted">
                <th className="px-3 py-2">Material</th>
                <th className="px-3 py-2 text-right">Stock coverage</th>
                <th className="hidden px-3 py-2 text-right lg:table-cell">
                  Latest vs target
                </th>
                <th className="px-3 py-2">Risk</th>
                <th className="px-3 py-2">Action</th>
                <th className="hidden px-3 py-2 xl:table-cell">Key reason</th>
              </tr>
            </thead>
            <tbody>
              {priority.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-3 py-8 text-center text-ink-muted">
                    Nothing needs immediate purchasing action.
                  </td>
                </tr>
              ) : (
                priority.map((i) => (
                  <tr key={i.material.id} className="border-b border-line last:border-b-0 hover:bg-accent-soft/30">
                    <td className="px-3 py-2.5">
                      <Link
                        href={`/materials/${i.material.id}`}
                        className="font-semibold text-ink hover:text-accent-strong"
                      >
                        {i.material.name}
                      </Link>
                      <span className="block text-[12px] text-ink-muted">
                        {i.material.code}
                      </span>
                    </td>
                    <td className="tnum whitespace-nowrap px-3 py-2.5 text-right">
                      <span className="font-medium">{fmtDays(i.coverage.coverageDays)}</span>
                      {i.coverage.projectedCoverageDays != null &&
                      i.coverage.projectedCoverageDays !==
                        i.coverage.coverageDays ? (
                        <span className="block text-[12px] text-ink-muted">
                          proj. {fmtDays(i.coverage.projectedCoverageDays)}
                        </span>
                      ) : null}
                    </td>
                    <td className="tnum hidden whitespace-nowrap px-3 py-2.5 text-right lg:table-cell">
                      {i.priceStats.latest != null ? (
                        <>
                          <span>{fmtPrice(i.priceStats.latest, i.material.currency, i.material.unit)}</span>
                          {i.priceStats.diffToTargetPct != null ? (
                            <span
                              className={
                                "block text-[12px] " +
                                (i.priceStats.diffToTargetPct > 0
                                  ? "text-serious-ink"
                                  : "text-ok-ink")
                              }
                            >
                              {i.priceStats.diffToTargetPct > 0 ? "+" : ""}
                              {i.priceStats.diffToTargetPct.toFixed(1)}% vs target
                            </span>
                          ) : null}
                        </>
                      ) : (
                        "—"
                      )}
                    </td>
                    <td className="px-3 py-2.5">
                      <RiskBadge level={i.risk.level as RiskLevel} score={i.risk.score} />
                    </td>
                    <td className="px-3 py-2.5">
                      <RecommendationBadge
                        value={i.recommendation.recommendation as Recommendation}
                      />
                      <span className="block pt-0.5 text-[12px] text-ink-muted">
                        confidence {i.recommendation.confidence}%
                      </span>
                    </td>
                    <td className="hidden max-w-md px-3 py-2.5 text-[12.5px] text-ink-secondary xl:table-cell">
                      {i.recommendation.reasons.find((r) =>
                        r.includes("stockout") || r.includes("safety") || r.includes("reorder") || r.includes("Single approved")
                      ) ?? i.recommendation.reasons[0]}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Card>

      <div className="grid gap-4 xl:grid-cols-2">
        {/* Open actions */}
        <Card>
          <CardHeader
            title="Open Actions"
            description="Sorted by due date — overdue items are flagged."
            actions={
              <Link
                href="/actions"
                className="inline-flex items-center gap-1 text-[13px] font-medium text-accent-strong hover:underline"
              >
                Action Center <ArrowRight size={14} />
              </Link>
            }
          />
          <CardContent className="divide-y divide-line p-0">
            {openActions.length === 0 ? (
              <p className="px-4 py-6 text-center text-ink-muted">No open actions.</p>
            ) : (
              openActions.map((a) => {
                const overdue =
                  a.dueDate != null && a.dueDate.getTime() < now.getTime();
                return (
                  <div key={a.id} className="flex items-start gap-3 px-4 py-2.5">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-[13.5px] font-medium">{a.title}</p>
                      <p className="mt-0.5 text-[12px] text-ink-muted">
                        {[a.material?.name, a.supplier?.name, titleCase(a.actionType)]
                          .filter(Boolean)
                          .join(" · ")}
                      </p>
                    </div>
                    <div className="flex shrink-0 flex-col items-end gap-1">
                      <div className="flex items-center gap-1.5">
                        <PriorityBadge priority={a.priority as ActionPriority} />
                        <ActionStatusBadge status={a.status as ActionStatus} />
                      </div>
                      <span
                        className={
                          "text-[12px] " +
                          (overdue ? "font-semibold text-crit-ink" : "text-ink-muted")
                        }
                      >
                        {overdue ? "OVERDUE · " : "due "}
                        {fmtDate(a.dueDate)}
                      </span>
                    </div>
                  </div>
                );
              })
            )}
          </CardContent>
        </Card>

        {/* Market alerts */}
        <Card>
          <CardHeader
            title="Active Market Alerts"
            description="High and critical intelligence entries feeding the risk engine."
            actions={
              <Link
                href="/market-intelligence"
                className="inline-flex items-center gap-1 text-[13px] font-medium text-accent-strong hover:underline"
              >
                Market Intelligence <ArrowRight size={14} />
              </Link>
            }
          />
          <CardContent className="divide-y divide-line p-0">
            {activeAlerts.length === 0 ? (
              <p className="px-4 py-6 text-center text-ink-muted">
                No high-risk market alerts.
              </p>
            ) : (
              activeAlerts.map((e) => (
                <div key={e.id} className="flex items-start gap-3 px-4 py-2.5">
                  <AlertTriangle
                    size={15}
                    className={
                      "mt-0.5 shrink-0 " +
                      (e.riskLevel === "CRITICAL" ? "text-crit" : "text-serious")
                    }
                  />
                  <div className="min-w-0 flex-1">
                    <p className="text-[13.5px] font-medium">{e.title}</p>
                    <p className="mt-0.5 line-clamp-2 text-[12.5px] text-ink-secondary">
                      {e.summary}
                    </p>
                    <p className="mt-0.5 text-[12px] text-ink-muted">
                      {fmtDate(e.date)} · affects {e.affectedMaterials}
                    </p>
                  </div>
                  <RiskBadge level={e.riskLevel as RiskLevel} />
                </div>
              ))
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
