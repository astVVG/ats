import { isAxiosError } from "axios";

// Manual type: the error format is not described in the OpenAPI schema.
export type ProblemDetail = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: { field: string; message: string }[];
};

function isProblemDetail(data: unknown): data is ProblemDetail {
  if (typeof data !== "object" || data === null) {
    return false;
  }
  return "title" in data || "status" in data || "detail" in data;
}

export function getProblemDetail(error: unknown): ProblemDetail | null {
  if (!isAxiosError(error)) {
    return null;
  }
  const data: unknown = error.response?.data;
  return isProblemDetail(data) ? data : null;
}
