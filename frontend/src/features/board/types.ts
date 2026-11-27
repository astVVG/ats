import type { components } from "@/shared/api/schema";

export type Board = components["schemas"]["BoardResponse"];
export type BoardJob = components["schemas"]["BoardJobResponse"];
export type BoardColumn = components["schemas"]["BoardColumnResponse"];
export type BoardCard = components["schemas"]["BoardCardResponse"];
export type MoveApplicationRequest =
  components["schemas"]["MoveApplicationRequest"];
export type Application = components["schemas"]["ApplicationResponse"];
export type CreateApplicationRequest =
  components["schemas"]["CreateApplicationRequest"];

/** The :id route parameter as a job id, or null if it cannot be one. */
export function parseJobId(value: string | undefined): number | null {
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}
