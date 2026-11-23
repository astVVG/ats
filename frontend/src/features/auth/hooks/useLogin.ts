import { useMutation } from "@tanstack/react-query";
import { fetchMe, login } from "@/features/auth/api";
import { useSessionStore } from "@/features/auth/store";
import type { LoginRequest } from "@/features/auth/types";

export function useLogin() {
  return useMutation({
    mutationFn: async (credentials: LoginRequest) => {
      const { accessToken } = await login(credentials);
      if (!accessToken) {
        throw new Error("Login response without access token");
      }
      // Token and user are stored together, so guards never see a half session.
      const user = await fetchMe(accessToken);
      const { setToken, setUser } = useSessionStore.getState();
      setToken(accessToken);
      setUser(user);
      return user;
    },
  });
}
