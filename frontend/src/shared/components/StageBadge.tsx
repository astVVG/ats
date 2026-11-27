import { cn } from "@/shared/lib/utils";
import {
  STAGE_BADGE_CLASSES,
  STAGE_DOT_CLASSES,
  STAGE_LABELS,
  type Stage,
} from "@/shared/lib/stages";
import { Badge } from "@/shared/ui/badge";

type StageBadgeProps = {
  stage: Stage;
  className?: string;
};

export function StageBadge({ stage, className }: StageBadgeProps) {
  return (
    <Badge
      variant="outline"
      data-stage={stage}
      className={cn(STAGE_BADGE_CLASSES[stage], className)}
    >
      <span
        aria-hidden="true"
        className={cn("size-1.5 rounded-full", STAGE_DOT_CLASSES[stage])}
      />
      {STAGE_LABELS[stage]}
    </Badge>
  );
}
