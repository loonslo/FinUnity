import { api } from "./client";
import type { Schemas } from "./types";

// A cash movement from one institution to another on a date.

export type Transfer = Schemas["TransferRead"];
export type TransferCreate = Schemas["TransferCreate"];

export async function getTransfers(): Promise<Transfer[]> {
  return (await api.get<Transfer[]>("/api/transfers")).data;
}

export async function createTransfer(data: TransferCreate): Promise<Transfer> {
  return (await api.post<Transfer>("/api/transfers", data)).data;
}

export async function updateTransfer(
  id: number,
  data: TransferCreate,
): Promise<Transfer> {
  return (await api.put<Transfer>(`/api/transfers/${id}`, data)).data;
}

export async function deleteTransfer(id: number): Promise<void> {
  await api.delete(`/api/transfers/${id}`);
}

// GET /api/transfers/{id}/deletion -> what deleting it moves, in the words
// the chat's card says it with (brief AP).
export async function transferDeletion(id: number): Promise<string> {
  return (await api.get<Schemas["DeletionRead"]>(`/api/transfers/${id}/deletion`)).data.said;
}
