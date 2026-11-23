import { afterEach, describe, expect, it } from "vitest";
import { SESSION_STORAGE_KEY, useSessionStore } from "@/features/auth/store";
import type { User } from "@/features/auth/types";

const user: User = {
  id: 1,
  name: "Ada Admin",
  email: "ada@example.com",
  role: "ADMIN",
  active: true,
  createdAt: "2026-01-01T00:00:00Z",
};

function persisted(): unknown {
  const raw = localStorage.getItem(SESSION_STORAGE_KEY);
  return raw === null ? null : JSON.parse(raw);
}

describe("session store", () => {
  afterEach(() => {
    useSessionStore.getState().clearSession();
    localStorage.clear();
  });

  it("persists the token under ats.session", () => {
    useSessionStore.getState().setToken("abc");

    expect(useSessionStore.getState().token).toBe("abc");
    expect(persisted()).toMatchObject({ state: { token: "abc" } });
  });

  it("persists only the token, never the user", () => {
    useSessionStore.getState().setToken("abc");
    useSessionStore.getState().setUser(user);

    expect(useSessionStore.getState().user).toEqual(user);
    const stored = JSON.stringify(persisted());
    expect(stored).not.toContain("ada@example.com");
    expect(stored).not.toContain("user");
  });

  it("clearSession removes token and user", () => {
    useSessionStore.getState().setToken("abc");
    useSessionStore.getState().setUser(user);

    useSessionStore.getState().clearSession();

    expect(useSessionStore.getState()).toMatchObject({
      token: null,
      user: null,
    });
    expect(persisted()).toMatchObject({ state: { token: null } });
  });
});
