import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVertical } from "lucide-react";
import type { Ref } from "react";
import { Link } from "react-router";
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
};

function CardContent({
  card,
  dragDisabled = false,
  handleRef,
  handleAttributes,
  handleListeners,
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
      </div>
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
};

/** A card that can be dragged with its handle, by pointer or keyboard. */
export function BoardCard({
  card,
  dragDisabled = false,
  saving = false,
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
      />
    </li>
  );
}
