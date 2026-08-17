"use server";

import { revalidatePath } from "next/cache";
import { prisma } from "@/lib/prisma";
import { formDataToObject, parseForm, quotationSchema } from "@/lib/validation";
import { canConvert, convertPrice, roundPrice } from "@/domain/units";
import type { QuantityUnit, QuotationStatus } from "@/domain/enums";
import type { FormState } from "./form-state";

function revalidateQuotationPages(materialId?: string) {
  revalidatePath("/");
  revalidatePath("/quotations");
  revalidatePath("/price-history");
  revalidatePath("/plan");
  if (materialId) revalidatePath(`/materials/${materialId}`);
}

async function validateAgainstMaterial(data: {
  materialId: string;
  priceUnit: QuantityUnit;
  quantityUnit: QuantityUnit;
}): Promise<
  | { ok: true; materialUnit: QuantityUnit }
  | { ok: false; state: FormState }
> {
  const material = await prisma.material.findUnique({
    where: { id: data.materialId },
    select: { unit: true },
  });
  if (!material) {
    return {
      ok: false,
      state: {
        ok: false,
        message: "Validation failed.",
        fieldErrors: { materialId: "Material not found." },
      },
    };
  }
  const unit = material.unit as QuantityUnit;
  if (!canConvert(data.priceUnit, unit)) {
    return {
      ok: false,
      state: {
        ok: false,
        message: "Validation failed.",
        fieldErrors: {
          priceUnit: `Price unit ${data.priceUnit} cannot be converted to the material's base unit (${unit}).`,
        },
      },
    };
  }
  if (!canConvert(data.quantityUnit, unit)) {
    return {
      ok: false,
      state: {
        ok: false,
        message: "Validation failed.",
        fieldErrors: {
          quantityUnit: `Quantity unit ${data.quantityUnit} cannot be converted to the material's base unit (${unit}).`,
        },
      },
    };
  }
  return { ok: true, materialUnit: unit };
}

export async function createQuotation(
  _prev: FormState,
  formData: FormData
): Promise<FormState> {
  const parsed = parseForm(quotationSchema, formDataToObject(formData));
  if (!parsed.success) {
    return { ok: false, message: "Please fix the highlighted fields.", fieldErrors: parsed.fieldErrors };
  }
  const check = await validateAgainstMaterial(parsed.data);
  if (!check.ok) return check.state;

  const normalizedPrice = roundPrice(
    convertPrice(parsed.data.price, parsed.data.priceUnit, check.materialUnit)
  );
  const created = await prisma.quotation.create({
    data: { ...parsed.data, normalizedPrice },
  });
  revalidateQuotationPages(parsed.data.materialId);
  return {
    ok: true,
    message: "Quotation saved.",
    createdId: created.id,
    meta: { materialId: parsed.data.materialId },
  };
}

export async function updateQuotation(
  id: string,
  _prev: FormState,
  formData: FormData
): Promise<FormState> {
  const parsed = parseForm(quotationSchema, formDataToObject(formData));
  if (!parsed.success) {
    return { ok: false, message: "Please fix the highlighted fields.", fieldErrors: parsed.fieldErrors };
  }
  const check = await validateAgainstMaterial(parsed.data);
  if (!check.ok) return check.state;

  const normalizedPrice = roundPrice(
    convertPrice(parsed.data.price, parsed.data.priceUnit, check.materialUnit)
  );
  await prisma.quotation.update({
    where: { id },
    data: { ...parsed.data, normalizedPrice, isSample: false },
  });
  revalidateQuotationPages(parsed.data.materialId);
  return { ok: true, message: "Quotation updated." };
}

export async function setQuotationStatus(
  id: string,
  status: QuotationStatus
): Promise<FormState> {
  const q = await prisma.quotation.update({
    where: { id },
    data: { status },
    select: { materialId: true },
  });
  revalidateQuotationPages(q.materialId);
  return { ok: true, message: `Quotation marked ${status}.` };
}

export async function deleteQuotation(id: string): Promise<FormState> {
  const actionRefs = await prisma.actionItem.count({
    where: { quotationId: id },
  });
  if (actionRefs > 0) {
    return {
      ok: false,
      message: `Quotation is referenced by ${actionRefs} action(s). Mark it REJECTED or SUPERSEDED instead of deleting.`,
    };
  }
  const q = await prisma.quotation.delete({
    where: { id },
    select: { materialId: true },
  });
  revalidateQuotationPages(q.materialId);
  return { ok: true, message: "Quotation deleted." };
}
