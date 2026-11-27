import { useQuery } from "@tanstack/react-query";
import { getJobBoard } from "@/features/board/api";

export function jobBoardQueryKey(jobId: number) {
  return ["jobs", jobId, "board"] as const;
}

export function useJobBoard(jobId: number) {
  return useQuery({
    queryKey: jobBoardQueryKey(jobId),
    queryFn: () => getJobBoard(jobId),
  });
}
