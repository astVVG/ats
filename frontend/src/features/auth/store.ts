import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { User } from "@/features/auth/types";

export const SESSION_STORAGE_KEY = "ats.session";

type SessionState = {
  token: string | null;
  user: User | null;
  setToken: (token: string) => void;
  setUser: (user: User) => void;
  clearSession: () => void;
};

export const useSessionStore = create<SessionState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      setToken: (token) => set({ token }),
      setUser: (user) => set({ user }),
      clearSession: () => set({ token: null, user: null }),
    }),
    {
      name: SESSION_STORAGE_KEY,
      // Only the token is persisted; the user is reloaded from /me.
      partialize: (state) => ({ token: state.token }),
    },
  ),
);
