import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createUser } from "@/features/users/api";
import { USERS_QUERY_KEY } from "@/features/users/hooks/useUsers";

export function useCreateUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createUser,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: USERS_QUERY_KEY }),
  });
}
