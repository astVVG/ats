import { focusManager } from "@tanstack/react-query";
import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getJobBoard, listHistory, listNotes } from "@/features/board/api";
import type { Board } from "@/features/board/types";
import { getCandidate } from "@/features/candidates/api";
import {
  buildBoard,
  buildBoardCard,
  buildCandidate,
  buildNote,
  httpError,
  pageOf,
  renderRoute,
  signIn,
  signOut,
} from "@/test/utils";

vi.mock("@/features/board/api", () => ({
  getJobBoard: vi.fn(),
  moveApplication: vi.fn(),
  listNotes: vi.fn(),
  createNote: vi.fn(),
  deleteNote: vi.fn(),
  listHistory: vi.fn(),
  rateApplication: vi.fn(),
}));

vi.mock("@/features/candidates/api", () => ({
  getCandidate: vi.fn(),
  listCandidates: vi.fn(),
  createCandidate: vi.fn(),
}));

const getJobBoardMock = vi.mocked(getJobBoard);
const getCandidateMock = vi.mocked(getCandidate);

const ana = buildBoardCard({
  id: 1,
  rating: 4,
  candidate: { id: 101, fullName: "Ana López", email: "ana@example.com" },
});
const beto = buildBoardCard({
  id: 2,
  candidate: { id: 102, fullName: "Beto Ruiz", email: "beto@example.com" },
});

function sampleBoard(job: Partial<Board["job"]> = {}): Board {
  return buildBoard({ job, cards: { APPLIED: [beto], INTERVIEW: [ana] } });
}

async function openDetail(name: string) {
  const user = userEvent.setup();
  const button = await screen.findByRole("button", {
    name: `Ver detalle de la postulación de ${name}`,
  });
  await user.click(button);
  const panel = await screen.findByRole("dialog", {
    name: `Postulación de ${name}`,
  });
  return { user, button, panel };
}

describe("ApplicationDetailSheet", () => {
  beforeEach(() => {
    vi.mocked(listNotes).mockResolvedValue(pageOf([]));
    vi.mocked(listHistory).mockResolvedValue(pageOf([]));
    getCandidateMock.mockResolvedValue(
      buildCandidate({
        id: 101,
        fullName: "Ana López",
        email: "ana@example.com",
        phone: "+52 777 000 1111",
        source: "Referido",
        hasResume: true,
      }),
    );
    signIn();
  });

  afterEach(() => {
    vi.resetAllMocks();
    signOut();
  });

  it("opens the panel of the card whose button was pressed", async () => {
    getJobBoardMock.mockResolvedValue(sampleBoard());
    renderRoute("/jobs/10/board");

    const { panel } = await openDetail("Ana López");

    expect(within(panel).getByText("ana@example.com")).toBeInTheDocument();
    expect(within(panel).getByText("Entrevista")).toBeInTheDocument();
    expect(await within(panel).findByText("+52 777 000 1111")).toBeVisible();
    expect(within(panel).getByText("Referido")).toBeInTheDocument();
    expect(within(panel).getByText("Tiene CV")).toBeInTheDocument();
    expect(
      within(panel).getByRole("link", { name: "Ver perfil completo" }),
    ).toHaveAttribute("href", "/candidates/101");
    expect(getCandidateMock).toHaveBeenCalledWith(101);
  });

  it("keeps the name link and the drag handle of the card as they were", async () => {
    getJobBoardMock.mockResolvedValue(sampleBoard());
    renderRoute("/jobs/10/board");

    expect(
      await screen.findByRole("link", { name: "Ana López" }),
    ).toHaveAttribute("href", "/candidates/101");
    expect(
      screen.getByRole("button", { name: "Mover postulación de Ana López" }),
    ).toBeEnabled();
  });

  it("closes with Escape and gives the focus back to the button that opened it", async () => {
    getJobBoardMock.mockResolvedValue(sampleBoard());
    renderRoute("/jobs/10/board");
    const { user, button } = await openDetail("Ana López");

    await user.keyboard("{Escape}");

    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(button).toHaveFocus();
  });

  it("shows the missing optional data of the candidate", async () => {
    getJobBoardMock.mockResolvedValue(sampleBoard());
    getCandidateMock.mockResolvedValue(
      buildCandidate({ id: 102, phone: null, source: null, hasResume: false }),
    );
    renderRoute("/jobs/10/board");

    const { panel } = await openDetail("Beto Ruiz");

    expect(await within(panel).findByText("Sin teléfono")).toBeVisible();
    expect(within(panel).getByText("Sin origen")).toBeInTheDocument();
    expect(within(panel).getByText("Sin CV")).toBeInTheDocument();
    expect(within(panel).getByText("Postulado")).toBeInTheDocument();
  });

  it("shows a loading state for the candidate", async () => {
    getJobBoardMock.mockResolvedValue(sampleBoard());
    getCandidateMock.mockReturnValue(new Promise(() => {}));
    renderRoute("/jobs/10/board");

    const { panel } = await openDetail("Ana López");

    expect(
      within(panel).getByText("Cargando datos del candidato…"),
    ).toBeInTheDocument();
    // The card data does not wait for the profile.
    expect(within(panel).getByText("ana@example.com")).toBeInTheDocument();
  });

  it("shows a candidate error with retry without blocking the rest of the panel", async () => {
    getJobBoardMock.mockResolvedValue(sampleBoard());
    getCandidateMock.mockRejectedValueOnce(httpError(500));
    renderRoute("/jobs/10/board");
    const { user, panel } = await openDetail("Ana López");

    expect(
      await within(panel).findByText(
        "No se pudieron cargar los datos del candidato.",
      ),
    ).toBeVisible();
    expect(within(panel).getByText("ana@example.com")).toBeInTheDocument();
    expect(
      within(panel).getByRole("link", { name: "Ver perfil completo" }),
    ).toBeInTheDocument();

    await user.click(within(panel).getByRole("button", { name: "Reintentar" }));

    expect(await within(panel).findByText("+52 777 000 1111")).toBeVisible();
  });

  it("does not carry the note draft or the page over to another application", async () => {
    getJobBoardMock.mockResolvedValue(sampleBoard());
    vi.mocked(listNotes).mockImplementation((_id, number) =>
      Promise.resolve(
        pageOf(
          [buildNote({ id: number + 1, content: `Nota ${number}` })],
          number,
          2,
        ),
      ),
    );
    renderRoute("/jobs/10/board");
    const first = await openDetail("Ana López");
    await first.user.type(
      within(first.panel).getByLabelText("Nueva nota"),
      "Borrador de Ana",
    );
    await first.user.click(
      within(first.panel).getByRole("button", { name: "Siguiente" }),
    );
    await first.user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());

    const second = await openDetail("Beto Ruiz");

    expect(within(second.panel).getByLabelText("Nueva nota")).toHaveValue("");
    expect(within(second.panel).getByText("Página 1 de 2")).toBeVisible();
    expect(vi.mocked(listNotes)).toHaveBeenLastCalledWith(2, 0);
  });

  it("works on a closed job", async () => {
    getJobBoardMock.mockResolvedValue(sampleBoard({ status: "CLOSED" }));
    renderRoute("/jobs/10/board");

    const { panel } = await openDetail("Ana López");

    expect(within(panel).getByText("ana@example.com")).toBeInTheDocument();
  });

  it("closes when the application is no longer on the board", async () => {
    getJobBoardMock.mockResolvedValueOnce(sampleBoard());
    renderRoute("/jobs/10/board");
    await openDetail("Ana López");
    getJobBoardMock.mockResolvedValue(
      buildBoard({ cards: { APPLIED: [beto] } }),
    );

    // Coming back to the window refetches the board.
    act(() => {
      focusManager.setFocused(false);
      focusManager.setFocused(true);
    });

    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    act(() => focusManager.setFocused(undefined));
  });
});
