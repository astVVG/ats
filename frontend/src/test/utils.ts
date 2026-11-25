import { QueryClient } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { AxiosError, AxiosHeaders } from "axios";
import { createElement } from "react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { Providers } from "@/app/providers";
import { routes } from "@/app/router";
import { useSessionStore } from "@/features/auth/store";
import type { LoginResponse, User } from "@/features/auth/types";
import type { Board, BoardCard } from "@/features/board/types";
import type { Candidate } from "@/features/candidates/types";
import type { Job } from "@/features/jobs/types";
import { STAGES, type Stage } from "@/shared/lib/stages";

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

/** Successful login response carrying the given token. */
export function loginResponse(accessToken: string): LoginResponse {
  return { accessToken, tokenType: "Bearer", expiresIn: 7200 };
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

/** Starts a session as if the user had logged in. */
export function signIn(user: User = recruiterUser) {
  useSessionStore.setState({ token: "token-123", user });
}

/** Clears the session left by signIn; call it after each test. */
export function signOut() {
  useSessionStore.getState().clearSession();
  localStorage.clear();
}

/** Renders the app routes at `path` inside the Providers. */
export function renderRoute(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    createElement(Providers, {
      queryClient: createTestQueryClient(),
      children: createElement(RouterProvider, { router }),
    }),
  );
  return router;
}

/** A page as the API returns it; pages are zero-based. */
export function pageOf<T>(content: T[], page = 0, totalPages = 1) {
  return {
    content,
    page,
    size: 20,
    totalElements: content.length,
    totalPages,
  };
}

export function buildJob(overrides: Partial<Job> = {}): Job {
  return {
    id: 10,
    title: "Desarrollador backend",
    department: "Ingeniería",
    location: "Remoto",
    description: "Java y Spring",
    status: "OPEN",
    createdBy: { id: adminUser.id, name: adminUser.name },
    createdAt: "2026-03-01T10:00:00Z",
    updatedAt: "2026-03-01T10:00:00Z",
    ...overrides,
  };
}

export function buildBoardCard(overrides: Partial<BoardCard> = {}): BoardCard {
  const id = overrides.id ?? 100;
  return {
    id,
    position: 0,
    rating: null,
    candidate: {
      id: id + 1000,
      fullName: `Candidato ${id}`,
      email: `candidato${id}@example.com`,
    },
    updatedAt: "2026-04-10T10:00:00Z",
    ...overrides,
  };
}

/**
 * A board with all five columns; `cards` fills some of them and positions
 * follow the order given.
 */
export function buildBoard({
  job = {},
  cards = {},
}: {
  job?: Partial<Board["job"]>;
  cards?: Partial<Record<Stage, BoardCard[]>>;
} = {}): Board {
  return {
    job: { id: 10, title: "Desarrollador backend", status: "OPEN", ...job },
    columns: STAGES.map((stage) => ({
      stage,
      applications: (cards[stage] ?? []).map((card, position) => ({
        ...card,
        position,
      })),
    })),
  };
}

export function buildCandidate(overrides: Partial<Candidate> = {}): Candidate {
  return {
    id: 20,
    fullName: "Laura Gómez",
    email: "laura@example.com",
    phone: "+52 777 123 4567",
    source: "LinkedIn",
    hasResume: false,
    createdAt: "2026-03-05T10:00:00Z",
    updatedAt: "2026-03-05T10:00:00Z",
    ...overrides,
  };
}
