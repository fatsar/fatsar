"use server";

import { revalidatePath } from "next/cache";
import { prisma } from "@/lib/prisma";
import { formDataToObject, parseForm, supplierSchema } from "@/lib/validation";
import type { FormState } from "./form-state";

function revalidateSupplierPages(id?: string) {
  revalidatePath("/");
  revalidatePath("/suppliers");
  if (id) revalidatePath(`/suppliers/${id}`);
}

export async function createSupplier(
  _prev: FormState,
  formData: FormData
): Promise<FormState> {
  const parsed = parseForm(supplierSchema, formDataToObject(formData));
  if (!parsed.success) {
    return { ok: false, message: "Please fix the highlighted fields.", fieldErrors: parsed.fieldErrors };
  }
  const existing = await prisma.supplier.findUnique({
    where: { code: parsed.data.code },
    select: { id: true },
  });
  if (existing) {
    return {
      ok: false,
      message: "Validation failed.",
      fieldErrors: { code: `Supplier code ${parsed.data.code} already exists.` },
    };
  }
  const created = await prisma.supplier.create({ data: parsed.data });
  revalidateSupplierPages(created.id);
  return { ok: true, message: "Supplier created.", createdId: created.id };
}

export async function updateSupplier(
  id: string,
  _prev: FormState,
  formData: FormData
): Promise<FormState> {
  const parsed = parseForm(supplierSchema, formDataToObject(formData));
  if (!parsed.success) {
    return { ok: false, message: "Please fix the highlighted fields.", fieldErrors: parsed.fieldErrors };
  }
  const clash = await prisma.supplier.findFirst({
    where: { code: parsed.data.code, NOT: { id } },
    select: { id: true },
  });
  if (clash) {
    return {
      ok: false,
      message: "Validation failed.",
      fieldErrors: { code: `Supplier code ${parsed.data.code} already exists.` },
    };
  }
  await prisma.supplier.update({
    where: { id },
    data: { ...parsed.data, isSample: false },
  });
  revalidateSupplierPages(id);
  return { ok: true, message: "Supplier updated." };
}

export async function setSupplierActive(
  id: string,
  isActive: boolean
): Promise<FormState> {
  await prisma.supplier.update({ where: { id }, data: { isActive } });
  revalidateSupplierPages(id);
  return {
    ok: true,
    message: isActive ? "Supplier reactivated." : "Supplier deactivated.",
  };
}
