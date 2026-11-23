import { useMe } from "@/features/auth/hooks/useMe";
import { useSessionStore } from "@/features/auth/store";

export function useAuth() {
  const token = useSessionStore((state) => state.token);
  const user = useSessionStore((state) => state.user);
  const me = useMe();

  const resolvingUser = token !== null && user === null;

  return {
    user,
    token,
    isAuthenticated: token !== null && user !== null,
    isLoading: resolvingUser && !me.isError,
    isError: resolvingUser && me.isError,
    retry: me.refetch,
    role: user?.role ?? null,
  };
}
