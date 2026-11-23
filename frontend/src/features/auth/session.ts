import type { QueryClient } from "@tanstack/react-query";
import { useSessionStore } from "@/features/auth/store";
import {
  setAuthTokenProvider,
  setUnauthorizedHandler,
} from "@/shared/api/client";

/** Clears the session and every cached query. */
export function endSession(queryClient: QueryClient) {
  useSessionStore.getState().clearSession();
  queryClient.clear();
}

/**
 * Connects the API client to the session. Route guards react to the cleared
 * session and redirect to /login, so no navigation happens here.
 */
export function connectApiClientToSession(queryClient: QueryClient) {
  setAuthTokenProvider(() => useSessionStore.getState().token);
  setUnauthorizedHandler(() => endSession(queryClient));
}
