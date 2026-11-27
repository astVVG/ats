import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { moveApplication } from "@/features/board/api";
import { applyMove } from "@/features/board/board-dnd";
import { jobBoardQueryKey } from "@/features/board/hooks/useJobBoard";
import { httpStatus, moveErrorMessage } from "@/features/board/messages";
import type { Board, MoveApplicationRequest } from "@/features/board/types";

export type MoveApplicationVariables = MoveApplicationRequest & {
  applicationId: number;
};

export function moveApplicationMutationKey(jobId: number) {
  return [...jobBoardQueryKey(jobId), "move"] as const;
}

/**
 * Moves a card in the cached board at once and saves it. A failure restores
 * the board as it was and shows a toast; the board is reloaded from the
 * server once the last move of the job has finished.
 */
export function useMoveApplication(jobId: number) {
  const queryClient = useQueryClient();
  const boardKey = jobBoardQueryKey(jobId);
  const mutationKey = moveApplicationMutationKey(jobId);

  return useMutation({
    mutationKey,
    mutationFn: ({
      applicationId,
      stage,
      position,
    }: MoveApplicationVariables) =>
      moveApplication(applicationId, { stage, position }),
    // Kept synchronous: mutate() runs it before returning, so the new order is
    // in the cache when React renders the drop and the card never jumps back.
    onMutate: ({ applicationId, stage, position }) => {
      void queryClient.cancelQueries({ queryKey: boardKey });
      const previous = queryClient.getQueryData<Board>(boardKey);
      if (previous) {
        queryClient.setQueryData(
          boardKey,
          applyMove(previous, applicationId, stage, position),
        );
      }
      return { previous };
    },
    onError: (error, _variables, context) => {
      if (context?.previous) {
        queryClient.setQueryData(boardKey, context.previous);
      }
      toast.error(moveErrorMessage(error));
    },
    onSettled: (_data, error) => {
      // This mutation still counts as pending here, so 1 means it is the last one.
      const last = queryClient.isMutating({ mutationKey }) === 1;
      // A vanished card means the board changed elsewhere: reload it anyway.
      if (last || httpStatus(error) === 404) {
        return queryClient.invalidateQueries({ queryKey: boardKey });
      }
    },
  });
}
