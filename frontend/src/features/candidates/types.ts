import type { components } from "@/shared/api/schema";

export type Candidate = components["schemas"]["CandidateResponse"];
export type CandidatePage =
  components["schemas"]["PageResponseCandidateResponse"];
export type CandidateRequest = components["schemas"]["CandidateRequest"];

export type CandidateListParams = {
  page: number;
  size: number;
  q?: string;
};

/** Same limit the backend enforces. */
export const MAX_RESUME_BYTES = 5 * 1024 * 1024;

/** The :id route parameter as a candidate id, or null if it cannot be one. */
export function parseCandidateId(value: string | undefined): number | null {
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}
