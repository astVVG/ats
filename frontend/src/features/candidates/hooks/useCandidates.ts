import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { listCandidates } from "@/features/candidates/api";
import type { CandidateListParams } from "@/features/candidates/types";

export const CANDIDATES_QUERY_KEY = ["candidates"] as const;

export function useCandidates(params: CandidateListParams) {
  return useQuery({
    queryKey: [...CANDIDATES_QUERY_KEY, "list", params],
    queryFn: () => listCandidates(params),
    placeholderData: keepPreviousData,
  });
}
