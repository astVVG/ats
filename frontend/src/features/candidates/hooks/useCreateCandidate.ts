import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createCandidate } from "@/features/candidates/api";
import { CANDIDATES_QUERY_KEY } from "@/features/candidates/hooks/useCandidates";

export function useCreateCandidate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createCandidate,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: CANDIDATES_QUERY_KEY }),
  });
}
