import type { Board, BoardCard, Rating } from "@/features/board/types";
import type { Stage } from "@/shared/lib/stages";

export type LocatedCard = { card: BoardCard; stage: Stage };

/** The card with this application id and the column it is in, if it is on the board. */
export function findCard(
  board: Board,
  applicationId: number,
): LocatedCard | null {
  for (const column of board.columns) {
    const card = column.applications.find((a) => a.id === applicationId);
    if (card) {
      return { card, stage: column.stage };
    }
  }
  return null;
}

/** The board with only the rating of one card changed; everything else is kept as is. */
export function setCardRating(
  board: Board,
  applicationId: number,
  rating: Rating,
): Board {
  if (findCard(board, applicationId) === null) {
    return board;
  }
  return {
    ...board,
    columns: board.columns.map((column) =>
      column.applications.some((a) => a.id === applicationId)
        ? {
            ...column,
            applications: column.applications.map((a) =>
              a.id === applicationId ? { ...a, rating } : a,
            ),
          }
        : column,
    ),
  };
}
