import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StageBadge } from "@/shared/components/StageBadge";
import {
  STAGE_BADGE_CLASSES,
  STAGE_DOT_CLASSES,
  STAGE_LABELS,
  STAGES,
} from "@/shared/lib/stages";

describe("StageBadge", () => {
  it.each(STAGES)("shows the label and colors of %s", (stage) => {
    render(<StageBadge stage={stage} />);

    const badge = screen.getByText(STAGE_LABELS[stage]);
    expect(badge).toHaveAttribute("data-stage", stage);
    expect(badge).toHaveClass(...STAGE_BADGE_CLASSES[stage].split(" "));
    const dot = badge.querySelector('[aria-hidden="true"]');
    expect(dot).toHaveClass(STAGE_DOT_CLASSES[stage]);
  });

  it("keeps extra classes", () => {
    render(<StageBadge stage="OFFER" className="ml-2" />);

    expect(screen.getByText("Oferta")).toHaveClass(
      "ml-2",
      "bg-stage-offer-soft",
    );
  });
});
