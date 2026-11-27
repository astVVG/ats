import { Star } from "lucide-react";
import { useRef, useState, type KeyboardEvent } from "react";
import { describeRating } from "@/features/board/rating";
import type { Rating } from "@/features/board/types";
import { cn } from "@/shared/lib/utils";

const STARS = [1, 2, 3, 4, 5] as const;

function starLabel(star: number, value: Rating) {
  if (star === value) {
    return "Quitar calificación";
  }
  return `Calificar con ${star} ${star === 1 ? "estrella" : "estrellas"}`;
}

type StarRatingProps = {
  value: Rating;
  /** Name of the group, e.g. "Calificación de Ana López". */
  label: string;
  /** Without it the stars are read only. Choosing the current value sends null. */
  onChange?: (value: Rating) => void;
  size?: "sm" | "md";
  className?: string;
};

/**
 * One to five stars. Interactive stars are a single tab stop: the arrow keys,
 * Home and End move between them, and Enter or Space chooses one.
 */
export function StarRating({
  value,
  label,
  onChange,
  size = "sm",
  className,
}: StarRatingProps) {
  const buttons = useRef<(HTMLButtonElement | null)[]>([]);
  // Star that keeps the tab stop; follows the focus while moving with the keys.
  const [focused, setFocused] = useState<number | null>(null);
  const summary = describeRating(value);
  const iconSize = size === "sm" ? "size-4" : "size-6";

  const icon = (star: number) => (
    <Star
      aria-hidden="true"
      className={cn(
        iconSize,
        value !== null && star <= value
          ? "fill-primary text-primary"
          : "text-muted-foreground",
      )}
    />
  );

  if (!onChange) {
    return (
      <span
        role="img"
        aria-label={`${label}: ${summary}`}
        className={cn("inline-flex items-center gap-0.5", className)}
      >
        {STARS.map((star) => (
          <span key={star}>{icon(star)}</span>
        ))}
      </span>
    );
  }

  const tabStop = focused ?? value ?? 1;

  function moveFocus(star: number) {
    const target = Math.min(5, Math.max(1, star));
    setFocused(target);
    buttons.current[target - 1]?.focus();
  }

  function handleKeyDown(
    event: KeyboardEvent<HTMLButtonElement>,
    star: number,
  ) {
    const next: Record<string, number> = {
      ArrowRight: star + 1,
      ArrowUp: star + 1,
      ArrowLeft: star - 1,
      ArrowDown: star - 1,
      Home: 1,
      End: 5,
    };
    if (event.key in next) {
      event.preventDefault();
      moveFocus(next[event.key]);
    }
  }

  return (
    <div
      role="group"
      aria-label={`${label}: ${summary}`}
      className={cn("inline-flex items-center", className)}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) {
          setFocused(null);
        }
      }}
    >
      {STARS.map((star) => (
        <button
          key={star}
          ref={(element) => {
            buttons.current[star - 1] = element;
          }}
          type="button"
          tabIndex={star === tabStop ? 0 : -1}
          aria-label={starLabel(star, value)}
          aria-pressed={star === value}
          className="rounded-sm p-0.5 outline-none hover:bg-accent focus-visible:ring-2 focus-visible:ring-ring"
          onClick={() => onChange(star === value ? null : star)}
          onKeyDown={(event) => handleKeyDown(event, star)}
          onFocus={() => setFocused(star)}
        >
          {icon(star)}
        </button>
      ))}
      {/* Announces the new value after a change. */}
      <span className="sr-only" aria-live="polite">
        {summary}
      </span>
    </div>
  );
}
