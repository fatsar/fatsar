"use server";

import { revalidatePath } from "next/cache";
import { prisma } from "@/lib/prisma";
import {
  formDataToObject,
  materialSchema,
  parseForm,
} from "@/lib/validation";
import type { FormState } from "./form-state";

function revalidateMaterialPages(id?: string) {
  revalidatePath("/");
  revalidatePath("/materials");
  revalidatePath("/plan");
  revalidatePath("/price-history");
  if (id) revalidatePath(`/materials/${id}`);
}

export async function createMaterial(
  _prev: FormState,
  formData: FormData
): Promise<FormState> {
  const parsed = parseForm(materialSchema, formDataToObject(formData));
  if (!parsed.success) {
    return { ok: false, message: "Please fix the highlighted fields.", fieldErrors: parsed.fieldErrors };
  }
  const { approvedSupplierIds, ...data } = parsed.data;

  const existing = await prisma.material.findUnique({
    where: { code: data.code },
  });
  if (existing) {
    return {
      ok: false,
      message: "Validation failed.",
      fieldErrors: { code: `Material code ${data.code} already exists.` },
    };
  }

  const created = await prisma.material.create({
    data: {
      ...data,
      suppliers: {
        create: approvedSupplierIds.map((supplierId) => ({
          supplierId,
          isApproved: true,
        })),
      },
    },
  });
  revalidateMaterialPages(created.id);
  return { ok: true, message: "Material created.", createdId: created.id };
}

export async function updateMaterial(
  id: string,
  _prev: FormState,
  formData: FormData
): Promise<FormState> {
  const parsed = parseForm(materialSchema, formDataToObject(formData));
  if (!parsed.success) {
    return { ok: false, message: "Please fix the highlighted fields.", fieldErrors: parsed.fieldErrors };
  }
  const { approvedSupplierIds, ...data } = parsed.data;

  const clash = await prisma.material.findFirst({
    where: { code: data.code, NOT: { id } },
    select: { id: true },
  });
  if (clash) {
    return {
      ok: false,
      message: "Validation failed.",
      fieldErrors: { code: `Material code ${data.code} already exists.` },
    };
  }

  await prisma.$transaction([
    prisma.material.update({ where: { id }, data: { ...data, isSample: false } }),
    prisma.materialSupplier.deleteMany({ where: { materialId: id } }),
    prisma.materialSupplier.createMany({
      data: approvedSupplierIds.map((supplierId) => ({
        materialId: id,
        supplierId,
        isApproved: true,
      })),
    }),
  ]);
  revalidateMaterialPages(id);
  return { ok: true, message: "Material updated." };
}

export async function setMaterialActive(
  id: string,
  isActive: boolean
): Promise<FormState> {
  await prisma.material.update({ where: { id }, data: { isActive } });
  revalidateMaterialPages(id);
  return { ok: true, message: isActive ? "Material reactivated." : "Material deactivated." };
}

/**
 * Safe delete: materials with history (quotations/actions) are deactivated
 * instead, so no purchasing history is ever lost silently.
 */
export async function deleteMaterial(id: string): Promise<FormState> {
  const [quotations, actions] = await Promise.all([
    prisma.quotation.count({ where: { materialId: id } }),
    prisma.actionItem.count({ where: { materialId: id } }),
  ]);
  if (quotations > 0 || actions > 0) {
    await prisma.material.update({ where: { id }, data: { isActive: false } });
    revalidateMaterialPages(id);
    return {
      ok: true,
      message: `Material has ${quotations} quotation(s) and ${actions} action(s) — it was deactivated instead of deleted to preserve history.`,
    };
  }
  await prisma.material.delete({ where: { id } });
  revalidateMaterialPages();
  return { ok: true, message: "Material deleted." };
}
