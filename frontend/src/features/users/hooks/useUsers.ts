import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { listUsers } from "@/features/users/api";
import type { PageParams } from "@/features/users/types";

export const USERS_QUERY_KEY = ["users"] as const;

export function useUsers(params: PageParams) {
  return useQuery({
    queryKey: [...USERS_QUERY_KEY, "list", params.page, params.size],
    queryFn: () => listUsers(params),
    placeholderData: keepPreviousData,
  });
}
