import { useQuery } from "@tanstack/react-query";
import { fetchMe } from "@/features/auth/api";
import { useSessionStore } from "@/features/auth/store";

export const ME_QUERY_KEY = ["auth", "me"] as const;

/** Loads /me when a token exists but the user is not known yet (e.g. after a reload). */
export function useMe() {
  const token = useSessionStore((state) => state.token);
  const user = useSessionStore((state) => state.user);

  return useQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: async () => {
      const me = await fetchMe();
      useSessionStore.getState().setUser(me);
      return me;
    },
    enabled: token !== null && user === null,
    retry: false,
  });
}
