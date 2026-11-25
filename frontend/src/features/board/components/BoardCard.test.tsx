import { DndContext } from "@dnd-kit/core";
import { SortableContext } from "@dnd-kit/sortable";
import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";
import {
  BoardCard,
  BoardCardOverlay,
} from "@/features/board/components/BoardCard";
import { buildBoardCard } from "@/test/utils";

const card = buildBoardCard({
  id: 7,
  candidate: {
    id: 42,
    fullName: "Laura Gómez",
    email: "laura.gomez.con.un.correo.largo@example.com",
  },
  updatedAt: "2026-04-10T10:00:00Z",
});

const formattedDate = new Intl.DateTimeFormat("es-MX", {
  dateStyle: "medium",
}).format(new Date(card.updatedAt));

function renderInBoard(children: ReactNode) {
  render(
    <MemoryRouter>
      <DndContext>
        <SortableContext items={[card.id]}>
          <ul>{children}</ul>
        </SortableContext>
      </DndContext>
    </MemoryRouter>,
  );
}

describe("BoardCard", () => {
  it("shows the candidate, email and update date", () => {
    renderInBoard(<BoardCard card={card} />);

    expect(screen.getByRole("link", { name: "Laura Gómez" })).toHaveAttribute(
      "href",
      "/candidates/42",
    );
    const email = screen.getByText(card.candidate.email);
    expect(email).toHaveAttribute("title", card.candidate.email);
    expect(email).toHaveClass("truncate");
    expect(screen.getByText(formattedDate)).toHaveAttribute(
      "datetime",
      card.updatedAt,
    );
  });

  it("has a labelled drag handle", () => {
    renderInBoard(<BoardCard card={card} />);

    const handle = screen.getByRole("button", {
      name: "Mover postulación de Laura Gómez",
    });
    expect(handle).toBeEnabled();
    expect(handle).toHaveAttribute(
      "aria-roledescription",
      "postulación arrastrable",
    );
  });

  it("disables the handle and dims the card while saving", () => {
    renderInBoard(<BoardCard card={card} dragDisabled saving />);

    expect(
      screen.getByRole("button", { name: "Mover postulación de Laura Gómez" }),
    ).toBeDisabled();
    const item = screen.getByRole("listitem");
    expect(item).toHaveAttribute("aria-busy", "true");
    expect(item).toHaveClass("opacity-50");
  });

  it("renders the drag overlay with the same content and a primary ring", () => {
    render(
      <MemoryRouter>
        <BoardCardOverlay card={card} />
      </MemoryRouter>,
    );

    expect(screen.getByRole("link", { name: "Laura Gómez" })).toBeVisible();
    expect(screen.getByText(formattedDate)).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Laura Gómez" }).closest("div.ring-2"),
    ).toHaveClass("ring-primary", "shadow-lg");
  });
});
