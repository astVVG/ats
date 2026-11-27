import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { rateApplication } from "@/features/board/api";
import { findCard, setCardRating } from "@/features/board/board-cache";
import { jobBoardQueryKey } from "@/features/board/hooks/useJobBoard";
import { httpStatus, rateErrorMessage } from "@/features/board/messages";
import type { Board, Rating } from "@/features/board/types";

/** Under the board key, so moves and ratings count as pending changes of one job. */
export function rateApplicationMutationKey(
  jobId: number,
  applicationId: number,
) {
  return [...jobBoardQueryKey(jobId), "rating", applicationId] as const;
}

/**
 * Rates a card in the cached board at once and saves it. Only the rating of
 * that card is touched, so a pending move stays on screen. Ratings of one
 * application are sent one after another; the board is reloaded once no move
 * or rating of the job is pending.
 */
export function useRateApplication(jobId: number, applicationId: number) {
  const queryClient = useQueryClient();
  const boardKey = jobBoardQueryKey(jobId);
  const mutationKey = rateApplicationMutationKey(jobId, applicationId);

  /** Only the newest rating of this application decides what the card shows. */
  const isLastRating = () => queryClient.isMutating({ mutationKey }) === 1;

  const writeRating = (rating: Rating) =>
    queryClient.setQueryData<Board>(boardKey, (board) =>
      board ? setCardRating(board, applicationId, rating) : board,
    );

  return useMutation({
    mutationKey,
    // Same scope for the card and the panel: requests go out in order.
    scope: { id: `rating-${applicationId}` },
    mutationFn: (rating: Rating) => rateApplication(applicationId, { rating }),
    onMutate: (rating) => {
      void queryClient.cancelQueries({ queryKey: boardKey });
      const board = queryClient.getQueryData<Board>(boardKey);
      const found = board ? findCard(board, applicationId) : null;
      // undefined: the card is not on the board, so there is nothing to restore.
      const previous = found ? (found.card.rating ?? null) : undefined;
      writeRating(rating);
      return { previous };
    },
    onSuccess: (application) => {
      if (isLastRating()) {
        writeRating(application.rating ?? null);
      }
    },
    onError: (error, _rating, context) => {
      // A newer rating is queued: it will set the value, so leave it on screen.
      if (isLastRating() && context?.previous !== undefined) {
        writeRating(context.previous);
      }
      toast.error(rateErrorMessage(error));
    },
    onSettled: (_data, error) => {
      // Still pending here: 1 means no other move or rating of this job is left.
      const last = queryClient.isMutating({ mutationKey: boardKey }) === 1;
      if (last || httpStatus(error) === 404) {
        return queryClient.invalidateQueries({ queryKey: boardKey });
      }
    },
  });
}
