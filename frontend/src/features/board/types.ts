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
export type Note = components["schemas"]["NoteResponse"];
export type NotePage = components["schemas"]["PageResponseNoteResponse"];
export type CreateNoteRequest = components["schemas"]["CreateNoteRequest"];
export type StageHistoryEntry = components["schemas"]["StageHistoryResponse"];
export type StageHistoryPage =
  components["schemas"]["PageResponseStageHistoryResponse"];
export type UpdateRatingRequest = components["schemas"]["UpdateRatingRequest"];
/** 1 to 5, or null when the application has no rating. */
export type Rating = NonNullable<BoardCard["rating"]> | null;

/** Page size of the notes and history lists in the application panel. */
export const DETAIL_PAGE_SIZE = 20;

/** Same limit the backend enforces, on the trimmed text. */
export const MAX_NOTE_LENGTH = 2000;

/** The :id route parameter as a job id, or null if it cannot be one. */
export function parseJobId(value: string | undefined): number | null {
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}
