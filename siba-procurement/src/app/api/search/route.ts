import { NextRequest, NextResponse } from "next/server";
import { prisma } from "@/lib/prisma";

/**
 * Global search across materials, suppliers, quotations, actions and market
 * intelligence. SQLite LIKE is case-insensitive for ASCII, which fits the
 * code/name tokens searched here (e.g. "Hoshine", "HS3130", "PMDI", "CP52").
 */
export async function GET(request: NextRequest) {
  const q = request.nextUrl.searchParams.get("q")?.trim() ?? "";
  if (q.length < 2) {
    return NextResponse.json({ query: q, groups: [] });
  }
  const take = 6;

  const [materials, suppliers, quotations, actions, intel] = await Promise.all([
    prisma.material.findMany({
      where: {
        OR: [{ code: { contains: q } }, { name: { contains: q } }],
      },
      select: { id: true, code: true, name: true, category: true, isActive: true },
      take,
      orderBy: { name: "asc" },
    }),
    prisma.supplier.findMany({
      where: {
        OR: [
          { code: { contains: q } },
          { name: { contains: q } },
          { country: { contains: q } },
        ],
      },
      select: { id: true, code: true, name: true, country: true, isActive: true },
      take,
      orderBy: { name: "asc" },
    }),
    prisma.quotation.findMany({
      where: { quotationNumber: { contains: q } },
      select: {
        id: true,
        quotationNumber: true,
        materialId: true,
        material: { select: { name: true } },
        supplier: { select: { name: true } },
      },
      take,
      orderBy: { quotationDate: "desc" },
    }),
    prisma.actionItem.findMany({
      where: { title: { contains: q } },
      select: { id: true, title: true, status: true },
      take,
      orderBy: { createdAt: "desc" },
    }),
    prisma.marketIntelligence.findMany({
      where: {
        OR: [{ title: { contains: q } }, { affectedMaterials: { contains: q } }],
      },
      select: { id: true, title: true, riskLevel: true },
      take,
      orderBy: { date: "desc" },
    }),
  ]);

  return NextResponse.json({
    query: q,
    groups: [
      {
        label: "Materials",
        items: materials.map((m) => ({
          id: m.id,
          title: `${m.code} — ${m.name}`,
          subtitle: m.category + (m.isActive ? "" : " · inactive"),
          href: `/materials/${m.id}`,
        })),
      },
      {
        label: "Suppliers",
        items: suppliers.map((s) => ({
          id: s.id,
          title: `${s.name} (${s.code})`,
          subtitle: s.country + (s.isActive ? "" : " · inactive"),
          href: `/suppliers/${s.id}`,
        })),
      },
      {
        label: "Quotations",
        items: quotations.map((qt) => ({
          id: qt.id,
          title: qt.quotationNumber,
          subtitle: `${qt.supplier.name} · ${qt.material.name}`,
          href: `/materials/${qt.materialId}?tab=quotations`,
        })),
      },
      {
        label: "Actions",
        items: actions.map((a) => ({
          id: a.id,
          title: a.title,
          subtitle: a.status.replaceAll("_", " "),
          href: `/actions`,
        })),
      },
      {
        label: "Market Intelligence",
        items: intel.map((i) => ({
          id: i.id,
          title: i.title,
          subtitle: `Risk: ${i.riskLevel}`,
          href: `/market-intelligence`,
        })),
      },
    ].filter((g) => g.items.length > 0),
  });
}
