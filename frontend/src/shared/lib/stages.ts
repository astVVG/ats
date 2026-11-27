import type { components } from "@/shared/api/schema";

export type Stage = components["schemas"]["ApplicationResponse"]["stage"];

/** Board column order, the same as the backend enum. */
export const STAGES = [
  "APPLIED",
  "INTERVIEW",
  "OFFER",
  "HIRED",
  "REJECTED",
] as const satisfies readonly Stage[];

export const STAGE_LABELS: Record<Stage, string> = {
  APPLIED: "Postulado",
  INTERVIEW: "Entrevista",
  OFFER: "Oferta",
  HIRED: "Contratado",
  REJECTED: "Rechazado",
};

// Class names are written out in full so Tailwind can find them in the source.

/** Soft background, readable text and solid border for badges and chips. */
export const STAGE_BADGE_CLASSES: Record<Stage, string> = {
  APPLIED:
    "bg-stage-applied-soft text-stage-applied-foreground border-stage-applied",
  INTERVIEW:
    "bg-stage-interview-soft text-stage-interview-foreground border-stage-interview",
  OFFER: "bg-stage-offer-soft text-stage-offer-foreground border-stage-offer",
  HIRED: "bg-stage-hired-soft text-stage-hired-foreground border-stage-hired",
  REJECTED:
    "bg-stage-rejected-soft text-stage-rejected-foreground border-stage-rejected",
};

/** Solid color for dots and accents. */
export const STAGE_DOT_CLASSES: Record<Stage, string> = {
  APPLIED: "bg-stage-applied",
  INTERVIEW: "bg-stage-interview",
  OFFER: "bg-stage-offer",
  HIRED: "bg-stage-hired",
  REJECTED: "bg-stage-rejected",
};

/** Solid top border for board columns. */
export const STAGE_ACCENT_BORDER_CLASSES: Record<Stage, string> = {
  APPLIED: "border-t-stage-applied",
  INTERVIEW: "border-t-stage-interview",
  OFFER: "border-t-stage-offer",
  HIRED: "border-t-stage-hired",
  REJECTED: "border-t-stage-rejected",
};

/** Highlight for the column a card is being dragged over. */
export const STAGE_DROP_HIGHLIGHT_CLASSES: Record<Stage, string> = {
  APPLIED: "bg-stage-applied-soft ring-2 ring-stage-applied",
  INTERVIEW: "bg-stage-interview-soft ring-2 ring-stage-interview",
  OFFER: "bg-stage-offer-soft ring-2 ring-stage-offer",
  HIRED: "bg-stage-hired-soft ring-2 ring-stage-hired",
  REJECTED: "bg-stage-rejected-soft ring-2 ring-stage-rejected",
};
