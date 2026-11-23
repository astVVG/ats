import { QueryClient } from "@tanstack/react-query";
import { AxiosError, AxiosHeaders, type AxiosAdapter } from "axios";
import { afterEach, describe, expect, it } from "vitest";
import { connectApiClientToSession } from "@/features/auth/session";
import { useSessionStore } from "@/features/auth/store";
import {
  apiClient,
  setAuthTokenProvider,
  setUnauthorizedHandler,
} from "@/shared/api/client";

const unauthorizedAdapter: AxiosAdapter = async (config) => {
  throw new AxiosError("Unauthorized", "ERR_BAD_REQUEST", config, null, {
    data: {},
    status: 401,
    statusText: "Unauthorized",
    headers: new AxiosHeaders(),
    config,
  });
};

describe("connectApiClientToSession", () => {
  afterEach(() => {
    setAuthTokenProvider(() => null);
    setUnauthorizedHandler(null);
    useSessionStore.getState().clearSession();
    localStorage.clear();
  });

  it("clears the session and the query cache on a 401", async () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(["users"], ["cached"]);
    connectApiClientToSession(queryClient);
    useSessionStore.getState().setToken("expired");

    await expect(
      apiClient.get("/me", { adapter: unauthorizedAdapter }),
    ).rejects.toBeInstanceOf(AxiosError);

    expect(useSessionStore.getState().token).toBeNull();
    expect(queryClient.getQueryData(["users"])).toBeUndefined();
  });

  it("keeps the session when the login itself answers 401", async () => {
    const queryClient = new QueryClient();
    connectApiClientToSession(queryClient);
    useSessionStore.getState().setToken("still-valid");

    await expect(
      apiClient.post("/auth/login", {}, { adapter: unauthorizedAdapter }),
    ).rejects.toBeInstanceOf(AxiosError);

    expect(useSessionStore.getState().token).toBe("still-valid");
  });
});
