"use server";

import { revalidatePath } from "next/cache";
import { prisma } from "@/lib/prisma";
import { actionItemSchema, formDataToObject, parseForm } from "@/lib/validation";
import type { ActionStatus } from "@/domain/enums";
import type { FormState } from "./form-state";

function revalidateActionPages() {
  revalidatePath("/");
  revalidatePath("/actions");
}

export async function createActionItem(
  _prev: FormState,
  formData: FormData
): Promise<FormState> {
  const parsed = parseForm(actionItemSchema, formDataToObject(formData));
  if (!parsed.success) {
    return { ok: false, message: "Please fix the highlighted fields.", fieldErrors: parsed.fieldErrors };
  }
  const created = await prisma.actionItem.create({ data: parsed.data });
  revalidateActionPages();
  return { ok: true, message: "Action created.", createdId: created.id };
}

export async function updateActionItem(
  id: string,
  _prev: FormState,
  formData: FormData
): Promise<FormState> {
  const parsed = parseForm(actionItemSchema, formDataToObject(formData));
  if (!parsed.success) {
    return { ok: false, message: "Please fix the highlighted fields.", fieldErrors: parsed.fieldErrors };
  }
  await prisma.actionItem.update({
    where: { id },
    data: { ...parsed.data, isSample: false },
  });
  revalidateActionPages();
  return { ok: true, message: "Action updated." };
}

export async function setActionStatus(
  id: string,
  status: ActionStatus
): Promise<FormState> {
  await prisma.actionItem.update({ where: { id }, data: { status } });
  revalidateActionPages();
  return { ok: true, message: `Action marked ${status}.` };
}

export async function deleteActionItem(id: string): Promise<FormState> {
  await prisma.actionItem.delete({ where: { id } });
  revalidateActionPages();
  return { ok: true, message: "Action deleted." };
}
