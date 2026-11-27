import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { StarRating } from "@/features/board/components/StarRating";
import { describeRating } from "@/features/board/rating";
import type { Rating } from "@/features/board/types";

function renderStars(value: Rating, onChange?: (value: Rating) => void) {
  const user = userEvent.setup();
  render(
    <StarRating
      value={value}
      label="Calificación de Ana"
      onChange={onChange}
    />,
  );
  return user;
}

describe("StarRating", () => {
  it("names each star and announces the current value", () => {
    renderStars(3, vi.fn());

    const group = screen.getByRole("group", {
      name: "Calificación de Ana: 3 de 5 estrellas",
    });
    expect(
      within(group)
        .getAllByRole("button")
        .map((b) => b.getAttribute("aria-label")),
    ).toEqual([
      "Calificar con 1 estrella",
      "Calificar con 2 estrellas",
      "Quitar calificación",
      "Calificar con 4 estrellas",
      "Calificar con 5 estrellas",
    ]);
    expect(
      screen.getByRole("button", { name: "Quitar calificación" }),
    ).toHaveAttribute("aria-pressed", "true");
    expect(
      screen.getByRole("button", { name: "Calificar con 4 estrellas" }),
    ).toHaveAttribute("aria-pressed", "false");
  });

  it("says when there is no rating", () => {
    renderStars(null, vi.fn());

    expect(
      screen.getByRole("group", {
        name: "Calificación de Ana: Sin calificación",
      }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Quitar calificación" }),
    ).toBeNull();
    expect(describeRating(1)).toBe("1 de 5 estrellas");
  });

  it("sends the chosen value, and null when the current one is pressed", async () => {
    const onChange = vi.fn();
    const user = renderStars(2, onChange);

    await user.click(
      screen.getByRole("button", { name: "Calificar con 5 estrellas" }),
    );
    await user.click(
      screen.getByRole("button", { name: "Quitar calificación" }),
    );

    expect(onChange.mock.calls).toEqual([[5], [null]]);
  });

  it("is one tab stop that moves with the arrow keys and chooses with Enter or Space", async () => {
    const onChange = vi.fn();
    const user = renderStars(2, onChange);

    await user.tab();
    expect(
      screen.getByRole("button", { name: "Quitar calificación" }),
    ).toHaveFocus();
    await user.keyboard("{ArrowRight}{ArrowRight}");
    expect(
      screen.getByRole("button", { name: "Calificar con 4 estrellas" }),
    ).toHaveFocus();
    await user.keyboard("{Enter}");
    await user.keyboard("{Home}");
    expect(
      screen.getByRole("button", { name: "Calificar con 1 estrella" }),
    ).toHaveFocus();
    await user.keyboard("{ArrowLeft}");
    expect(
      screen.getByRole("button", { name: "Calificar con 1 estrella" }),
    ).toHaveFocus();
    await user.keyboard("{End} ");

    expect(onChange.mock.calls).toEqual([[4], [5]]);
    // Only one star is in the tab order.
    expect(
      screen.getAllByRole("button").filter((b) => b.tabIndex === 0),
    ).toHaveLength(1);
  });

  it("starts the tab stop on the first star without a rating", async () => {
    const user = renderStars(null, vi.fn());

    await user.tab();

    expect(
      screen.getByRole("button", { name: "Calificar con 1 estrella" }),
    ).toHaveFocus();
  });

  it("paints the filled stars with the primary token", () => {
    renderStars(2, vi.fn());

    const icons = screen
      .getAllByRole("button")
      .map((b) => b.querySelector("svg")?.getAttribute("class") ?? "");
    expect(icons[0]).toContain("fill-primary");
    expect(icons[1]).toContain("text-primary");
    expect(icons[2]).toContain("text-muted-foreground");
    expect(icons.join(" ")).not.toMatch(/stage-/);
  });

  it("is read only without onChange", () => {
    renderStars(4);

    expect(
      screen.getByRole("img", {
        name: "Calificación de Ana: 4 de 5 estrellas",
      }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button")).toBeNull();
  });
});
