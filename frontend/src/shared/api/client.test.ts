import {
  AxiosError,
  AxiosHeaders,
  type AxiosAdapter,
  type InternalAxiosRequestConfig,
} from "axios";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  apiClient,
  setAuthTokenProvider,
  setUnauthorizedHandler,
} from "@/shared/api/client";

// Adapters replace the network: they capture the request or answer with a status.
function okAdapter(seen: InternalAxiosRequestConfig[]): AxiosAdapter {
  return async (config) => {
    seen.push(config);
    return { data: {}, status: 200, statusText: "OK", headers: {}, config };
  };
}

const unauthorizedAdapter: AxiosAdapter = async (config) => {
  throw new AxiosError("Unauthorized", "ERR_BAD_REQUEST", config, null, {
    data: { status: 401 },
    status: 401,
    statusText: "Unauthorized",
    headers: new AxiosHeaders(),
    config,
  });
};

describe("apiClient interceptors", () => {
  afterEach(() => {
    setAuthTokenProvider(() => null);
    setUnauthorizedHandler(null);
  });

  it("adds the bearer token when the provider returns one", async () => {
    const seen: InternalAxiosRequestConfig[] = [];
    setAuthTokenProvider(() => "token-123");

    await apiClient.get("/me", { adapter: okAdapter(seen) });

    expect(seen[0].headers.get("Authorization")).toBe("Bearer token-123");
  });

  it("sends no Authorization header without a token", async () => {
    const seen: InternalAxiosRequestConfig[] = [];

    await apiClient.get("/me", { adapter: okAdapter(seen) });

    expect(seen[0].headers.has("Authorization")).toBe(false);
  });

  it("calls the unauthorized handler on a 401 outside the login", async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);

    await expect(
      apiClient.get("/users", { adapter: unauthorizedAdapter }),
    ).rejects.toBeInstanceOf(AxiosError);
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it("does not call the unauthorized handler on a failed login", async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);

    await expect(
      apiClient.post("/auth/login", {}, { adapter: unauthorizedAdapter }),
    ).rejects.toBeInstanceOf(AxiosError);
    expect(handler).not.toHaveBeenCalled();
  });
});
