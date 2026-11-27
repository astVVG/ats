import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createApplication } from "@/features/board/api";
import { jobBoardQueryKey } from "@/features/board/hooks/useJobBoard";

/** Applies a candidate to the job; the card shows up at the end of APPLIED. */
export function useCreateApplication(jobId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (candidateId: number) =>
      createApplication({ jobId, candidateId }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: jobBoardQueryKey(jobId) }),
  });
}
