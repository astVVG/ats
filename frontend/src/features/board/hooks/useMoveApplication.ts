import { useMutation, useQueryClient } from "@tanstack/react-query";
import { moveApplication } from "@/features/board/api";
import { jobBoardQueryKey } from "@/features/board/hooks/useJobBoard";
import type { MoveApplicationRequest } from "@/features/board/types";

export type MoveApplicationVariables = MoveApplicationRequest & {
  applicationId: number;
};

/** Saves a move and reloads the board afterwards, whether it worked or not. */
export function useMoveApplication(jobId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      applicationId,
      stage,
      position,
    }: MoveApplicationVariables) =>
      moveApplication(applicationId, { stage, position }),
    onSettled: () =>
      queryClient.invalidateQueries({ queryKey: jobBoardQueryKey(jobId) }),
  });
}
