import { useQuery } from "@tanstack/react-query";
import { getCandidate } from "@/features/candidates/api";
import { CANDIDATES_QUERY_KEY } from "@/features/candidates/hooks/useCandidates";

export function candidateQueryKey(id: number) {
  return [...CANDIDATES_QUERY_KEY, "detail", id] as const;
}

export function useCandidate(id: number) {
  return useQuery({
    queryKey: candidateQueryKey(id),
    queryFn: () => getCandidate(id),
  });
}
