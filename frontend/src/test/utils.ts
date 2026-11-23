import { QueryClient } from "@tanstack/react-query";
import { AxiosError, AxiosHeaders } from "axios";
import type { User } from "@/features/auth/types";

/** Query client without retries, so error states show up immediately. */
export function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

/** Axios error as the API would return it, with an optional ProblemDetail body. */
export function httpError(status: number, data: unknown = { status }) {
  const config = { headers: new AxiosHeaders() };
  return new AxiosError("Request failed", "ERR_BAD_REQUEST", config, null, {
    data,
    status,
    statusText: "",
    headers: {},
    config,
  });
}

export const adminUser: User = {
  id: 1,
  name: "Ada Admin",
  email: "ada@example.com",
  role: "ADMIN",
  active: true,
  createdAt: "2026-01-15T10:00:00Z",
};

export const recruiterUser: User = {
  id: 2,
  name: "Rita Reclutadora",
  email: "rita@example.com",
  role: "RECRUITER",
  active: true,
  createdAt: "2026-02-20T10:00:00Z",
};
