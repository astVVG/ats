import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVertical, PanelRightOpen } from "lucide-react";
import type { Ref } from "react";
import { Link } from "react-router";
import { StarRating } from "@/features/board/components/StarRating";
import { useRateApplication } from "@/features/board/hooks/useRateApplication";
import type { BoardCard as BoardCardData } from "@/features/board/types";
import { cn } from "@/shared/lib/utils";
import { Button } from "@/shared/ui/button";

const dateFormat = new Intl.DateTimeFormat("es-MX", { dateStyle: "medium" });

type Sortable = ReturnType<typeof useSortable>;

const CARD_CLASSES =
  "flex items-start gap-2 rounded-lg border bg-card p-3 text-card-foreground shadow-xs";

type CardContentProps = {
  card: BoardCardData;
  dragDisabled?: boolean;
  handleRef?: Ref<HTMLButtonElement>;
  handleAttributes?: Sortable["attributes"];
  handleListeners?: Sortable["listeners"];
  /** Without it the detail button is shown but inert, as in the drag copy. */
  onOpenDetail?: (applicationId: number) => void;
  /** Without it the stars are read only, as in the drag copy. */
  jobId?: number;
};

function CardRating({ jobId, card }: { jobId: number; card: BoardCardData }) {
  const rate = useRateApplication(jobId, card.id);
  return (
    <StarRating
      value={card.rating ?? null}
      label={`Calificación de ${card.candidate.fullName}`}
      onChange={(rating) => rate.mutate(rating)}
    />
  );
}

function CardContent({
  card,
  dragDisabled = false,
  handleRef,
  handleAttributes,
  handleListeners,
  onOpenDetail,
  jobId,
}: CardContentProps) {
  const name = card.candidate.fullName;
  return (
    <>
      <div className="min-w-0 flex-1 space-y-1">
        <Link
          to={`/candidates/${card.candidate.id}`}
          className="block truncate font-medium hover:underline"
        >
          {name}
        </Link>
        <p
          className="truncate text-sm text-muted-foreground"
          title={card.candidate.email}
        >
          {card.candidate.email}
        </p>
        <p className="text-xs text-muted-foreground">
          Actualizada el{" "}
          <time dateTime={card.updatedAt}>
            {dateFormat.format(new Date(card.updatedAt))}
          </time>
        </p>
        {jobId === undefined ? (
          <StarRating
            value={card.rating ?? null}
            label={`Calificación de ${name}`}
            className="-ml-0.5"
          />
        ) : (
          <CardRating jobId={jobId} card={card} />
        )}
      </div>
      <div className="flex shrink-0 flex-col items-center gap-1">
        <Button
          ref={handleRef}
          type="button"
          variant="ghost"
          size="icon-xs"
          className="cursor-grab touch-none text-muted-foreground"
          disabled={dragDisabled}
          {...handleAttributes}
          {...handleListeners}
          aria-label={`Mover postulación de ${name}`}
        >
          <GripVertical aria-hidden="true" />
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="icon-xs"
          className="text-muted-foreground"
          data-detail-trigger={onOpenDetail ? card.id : undefined}
          tabIndex={onOpenDetail ? undefined : -1}
          onClick={onOpenDetail ? () => onOpenDetail(card.id) : undefined}
          aria-label={`Ver detalle de la postulación de ${name}`}
        >
          <PanelRightOpen aria-hidden="true" />
        </Button>
      </div>
    </>
  );
}

/** Copy of a card that follows the pointer while it is dragged. */
export function BoardCardOverlay({ card }: { card: BoardCardData }) {
  return (
    <div
      className={cn(
        CARD_CLASSES,
        "cursor-grabbing shadow-lg ring-2 ring-primary",
      )}
    >
      <CardContent card={card} />
    </div>
  );
}

type BoardCardProps = {
  card: BoardCardData;
  dragDisabled?: boolean;
  /** The card's move is being saved. */
  saving?: boolean;
  onOpenDetail?: (applicationId: number) => void;
  /** Enables rating from the card. */
  jobId?: number;
};

/** A card that can be dragged with its handle, by pointer or keyboard. */
export function BoardCard({
  card,
  dragDisabled = false,
  saving = false,
  onOpenDetail,
  jobId,
}: BoardCardProps) {
  const {
    setNodeRef,
    setActivatorNodeRef,
    attributes,
    listeners,
    transform,
    transition,
    isDragging,
  } = useSortable({
    id: card.id,
    disabled: dragDisabled,
    attributes: { roleDescription: "postulación arrastrable" },
  });

  return (
    <li
      ref={setNodeRef}
      style={{ transform: CSS.Translate.toString(transform), transition }}
      data-card-id={card.id}
      aria-busy={saving || undefined}
      className={cn(CARD_CLASSES, (isDragging || saving) && "opacity-50")}
    >
      <CardContent
        card={card}
        dragDisabled={dragDisabled}
        handleRef={setActivatorNodeRef}
        handleAttributes={attributes}
        handleListeners={listeners}
        onOpenDetail={onOpenDetail}
        jobId={jobId}
      />
    </li>
  );
}
