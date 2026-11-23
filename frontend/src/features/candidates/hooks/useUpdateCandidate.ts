import { useMutation, useQueryClient } from "@tanstack/react-query";
import { updateCandidate } from "@/features/candidates/api";
import { CANDIDATES_QUERY_KEY } from "@/features/candidates/hooks/useCandidates";
import type { CandidateRequest } from "@/features/candidates/types";

export function useUpdateCandidate(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CandidateRequest) => updateCandidate(id, request),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: CANDIDATES_QUERY_KEY }),
  });
}
