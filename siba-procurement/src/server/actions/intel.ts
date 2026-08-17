"use server";

import { revalidatePath } from "next/cache";
import { prisma } from "@/lib/prisma";
import { formDataToObject, intelSchema, parseForm } from "@/lib/validation";
import type { FormState } from "./form-state";

function revalidateIntelPages() {
  revalidatePath("/");
  revalidatePath("/market-intelligence");
  revalidatePath("/plan");
}

export async function createIntel(
  _prev: FormState,
  formData: FormData
): Promise<FormState> {
  const parsed = parseForm(intelSchema, formDataToObject(formData));
  if (!parsed.success) {
    return { ok: false, message: "Please fix the highlighted fields.", fieldErrors: parsed.fieldErrors };
  }
  const created = await prisma.marketIntelligence.create({ data: parsed.data });
  revalidateIntelPages();
  return { ok: true, message: "Intelligence entry saved.", createdId: created.id };
}

export async function updateIntel(
  id: string,
  _prev: FormState,
  formData: FormData
): Promise<FormState> {
  const parsed = parseForm(intelSchema, formDataToObject(formData));
  if (!parsed.success) {
    return { ok: false, message: "Please fix the highlighted fields.", fieldErrors: parsed.fieldErrors };
  }
  await prisma.marketIntelligence.update({
    where: { id },
    data: { ...parsed.data, isSample: false },
  });
  revalidateIntelPages();
  return { ok: true, message: "Intelligence entry updated." };
}

export async function setIntelActive(
  id: string,
  isActive: boolean
): Promise<FormState> {
  await prisma.marketIntelligence.update({ where: { id }, data: { isActive } });
  revalidateIntelPages();
  return {
    ok: true,
    message: isActive ? "Entry reactivated." : "Entry archived.",
  };
}
