import { useDroppable } from "@dnd-kit/core";
import {
  SortableContext,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { BoardCard } from "@/features/board/components/BoardCard";
import type { BoardColumn as BoardColumnData } from "@/features/board/types";
import { StageBadge } from "@/shared/components/StageBadge";
import {
  STAGE_ACCENT_BORDER_CLASSES,
  STAGE_DROP_HIGHLIGHT_CLASSES,
  STAGE_LABELS,
} from "@/shared/lib/stages";
import { cn } from "@/shared/lib/utils";

type BoardColumnProps = {
  column: BoardColumnData;
  /** A dragged card is over this column. */
  highlighted: boolean;
  dragDisabled: boolean;
  onOpenDetail: (applicationId: number) => void;
  jobId: number;
};

export function BoardColumn({
  column,
  highlighted,
  dragDisabled,
  onOpenDetail,
  jobId,
}: BoardColumnProps) {
  const { stage, applications } = column;
  const { setNodeRef } = useDroppable({ id: stage });
  const headingId = `board-column-${stage}`;

  return (
    // relative: keeps the sr-only count inside the scrolling row; without it
    // that text is placed against the page and widens it.
    <section
      aria-labelledby={headingId}
      data-stage={stage}
      className={cn(
        "relative flex w-72 shrink-0 flex-col rounded-xl border border-t-4 bg-muted/40 transition-colors",
        STAGE_ACCENT_BORDER_CLASSES[stage],
        highlighted && STAGE_DROP_HIGHLIGHT_CLASSES[stage],
      )}
    >
      <header className="flex items-center justify-between gap-2 px-3 pt-3 pb-2">
        <h2 id={headingId}>
          <StageBadge stage={stage} />
        </h2>
        <span className="text-sm text-muted-foreground">
          {applications.length}
          <span className="sr-only">
            {" "}
            {applications.length === 1
              ? "postulación"
              : "postulaciones"} en {STAGE_LABELS[stage]}
          </span>
        </span>
      </header>
      <SortableContext
        id={stage}
        items={applications.map((a) => a.id)}
        strategy={verticalListSortingStrategy}
      >
        <ul
          ref={setNodeRef}
          className="flex min-h-32 flex-1 flex-col gap-2 px-3 pb-3"
        >
          {applications.map((card) => (
            <BoardCard
              key={card.id}
              card={card}
              dragDisabled={dragDisabled}
              onOpenDetail={onOpenDetail}
              jobId={jobId}
            />
          ))}
          {applications.length === 0 && (
            <li className="flex flex-1 items-center justify-center rounded-lg border border-dashed p-4 text-sm text-muted-foreground">
              Sin postulaciones
            </li>
          )}
        </ul>
      </SortableContext>
    </section>
  );
}
