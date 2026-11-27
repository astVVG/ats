import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createApplication, getJobBoard } from "@/features/board/api";
import type { Application, Board } from "@/features/board/types";
import { createCandidate, listCandidates } from "@/features/candidates/api";
import {
  buildBoard,
  buildBoardCard,
  buildCandidate,
  httpError,
  pageOf,
  renderRoute,
  signIn,
  signOut,
} from "@/test/utils";

vi.mock("@/features/board/api", () => ({
  getJobBoard: vi.fn(),
  moveApplication: vi.fn(),
  createApplication: vi.fn(),
}));

vi.mock("@/features/candidates/api", () => ({
  listCandidates: vi.fn(),
  createCandidate: vi.fn(),
  getCandidate: vi.fn(),
}));

const getJobBoardMock = vi.mocked(getJobBoard);
const createApplicationMock = vi.mocked(createApplication);
const listCandidatesMock = vi.mocked(listCandidates);
const createCandidateMock = vi.mocked(createCandidate);

const laura = buildCandidate({
  id: 20,
  fullName: "Laura Gómez",
  email: "laura@example.com",
});

const existingCard = buildBoardCard({
  id: 1,
  candidate: { id: 5, fullName: "Ana", email: "ana@example.com" },
});

function boardWith(...applied: ReturnType<typeof buildBoardCard>[]): Board {
  return buildBoard({ job: { id: 10 }, cards: { APPLIED: applied } });
}

function application(candidateId: number): Application {
  return {
    id: 99,
    jobId: 10,
    candidateId,
    stage: "APPLIED",
    position: 1,
    rating: null,
    createdAt: "2026-04-10T10:00:00Z",
    updatedAt: "2026-04-10T10:00:00Z",
  };
}

async function openDialog(board: Board = boardWith(existingCard)) {
  getJobBoardMock.mockResolvedValue(board);
  signIn();
  renderRoute("/jobs/10/board");
  const user = userEvent.setup();
  await user.click(
    await screen.findByRole("button", { name: "Agregar candidato" }),
  );
  return { user, dialog: await screen.findByRole("dialog") };
}

function appliedNames() {
  return within(screen.getByRole("region", { name: "Postulado" }))
    .queryAllByRole("link")
    .map((link) => link.textContent);
}

describe("AddApplicationDialog", () => {
  afterEach(() => {
    vi.resetAllMocks();
    signOut();
    act(() => {
      toast.dismiss();
    });
  });

  // ---- opening and modes ----

  it("opens in search mode with the focus on the search field", async () => {
    listCandidatesMock.mockResolvedValue(pageOf([laura]));
    const { dialog } = await openDialog();

    expect(
      within(dialog).getByRole("heading", { name: "Agregar candidato" }),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByText(/Busca un candidato existente o crea uno nuevo/),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole("button", { name: "Buscar candidato" }),
    ).toHaveAttribute("aria-pressed", "true");
    expect(
      within(dialog).getByRole("button", { name: "Crear candidato nuevo" }),
    ).toHaveAttribute("aria-pressed", "false");
    await waitFor(() =>
      expect(within(dialog).getByLabelText("Buscar")).toHaveFocus(),
    );
  });

  it("is disabled for a closed job", async () => {
    getJobBoardMock.mockResolvedValue(
      buildBoard({ job: { id: 10, status: "CLOSED" } }),
    );
    signIn();
    renderRoute("/jobs/10/board");

    const button = await screen.findByRole("button", {
      name: "Agregar candidato",
    });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute(
      "title",
      "La vacante está cerrada y no admite nuevas postulaciones.",
    );
  });

  it("switches modes and starts over when closed", async () => {
    listCandidatesMock.mockResolvedValue(pageOf([laura]));
    const { user, dialog } = await openDialog();

    await user.type(within(dialog).getByLabelText("Buscar"), "lau");
    await user.click(
      within(dialog).getByRole("button", { name: "Crear candidato nuevo" }),
    );
    expect(
      within(dialog).getByRole("button", { name: "Crear candidato nuevo" }),
    ).toHaveAttribute("aria-pressed", "true");
    expect(within(dialog).getByLabelText("Nombre completo")).toBeVisible();

    await user.keyboard("{Escape}");
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );

    await user.click(screen.getByRole("button", { name: "Agregar candidato" }));
    const reopened = await screen.findByRole("dialog");
    expect(
      within(reopened).getByRole("button", { name: "Buscar candidato" }),
    ).toHaveAttribute("aria-pressed", "true");
    expect(within(reopened).getByLabelText("Buscar")).toHaveValue("");
  });

  // ---- adding an existing candidate ----

  it("adds an existing candidate at the end of Postulado", async () => {
    listCandidatesMock.mockResolvedValue(pageOf([laura]));
    createApplicationMock.mockResolvedValue(application(laura.id));
    const { user, dialog } = await openDialog();
    getJobBoardMock.mockResolvedValue(
      boardWith(
        existingCard,
        buildBoardCard({
          id: 99,
          candidate: {
            id: laura.id,
            fullName: laura.fullName,
            email: laura.email,
          },
        }),
      ),
    );

    await user.click(
      await within(dialog).findByRole("button", {
        name: "Agregar a Laura Gómez",
      }),
    );

    expect(createApplicationMock).toHaveBeenCalledWith({
      jobId: 10,
      candidateId: 20,
    });
    expect(
      await screen.findByText("Candidato agregado a Postulado."),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
    await waitFor(() => expect(appliedNames()).toEqual(["Ana", "Laura Gómez"]));
    expect(getJobBoardMock).toHaveBeenCalledTimes(2);
  });

  it.each([
    [
      409,
      "No se pudo agregar al candidato: ya está postulado a esta vacante o la vacante está cerrada.",
    ],
    [404, "El candidato o la vacante ya no existe."],
    [500, "No se pudo agregar al candidato. Inténtalo de nuevo."],
  ])(
    "keeps the dialog open with the message for a %s",
    async (status, message) => {
      listCandidatesMock.mockResolvedValue(pageOf([laura]));
      createApplicationMock.mockRejectedValue(httpError(status));
      const { user, dialog } = await openDialog();

      await user.click(
        await within(dialog).findByRole("button", {
          name: "Agregar a Laura Gómez",
        }),
      );

      expect(await within(dialog).findByText(message)).toBeInTheDocument();
      expect(screen.getByRole("dialog")).toBeInTheDocument();
      expect(getJobBoardMock).toHaveBeenCalledTimes(1);
    },
  );

  it("disables the buttons while the application is created", async () => {
    let finish: () => void = () => {};
    listCandidatesMock.mockResolvedValue(pageOf([laura]));
    createApplicationMock.mockReturnValue(
      new Promise((resolve) => {
        finish = () => resolve(application(laura.id));
      }),
    );
    const { user, dialog } = await openDialog();

    await user.click(
      await within(dialog).findByRole("button", {
        name: "Agregar a Laura Gómez",
      }),
    );

    await waitFor(() =>
      expect(
        within(dialog).getByRole("button", { name: "Agregar a Laura Gómez" }),
      ).toBeDisabled(),
    );
    expect(
      within(dialog).getByRole("button", { name: "Crear candidato nuevo" }),
    ).toBeDisabled();

    await act(async () => finish());
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
  });

  // ---- creating a new candidate ----

  async function openCreateMode() {
    listCandidatesMock.mockResolvedValue(pageOf([]));
    const opened = await openDialog();
    await opened.user.click(
      within(opened.dialog).getByRole("button", {
        name: "Crear candidato nuevo",
      }),
    );
    return opened;
  }

  async function fillAndSubmit(
    user: ReturnType<typeof userEvent.setup>,
    dialog: HTMLElement,
  ) {
    await user.type(
      within(dialog).getByLabelText("Nombre completo"),
      "Nora Díaz",
    );
    await user.type(
      within(dialog).getByLabelText("Correo"),
      "nora@example.com",
    );
    await user.click(
      within(dialog).getByRole("button", { name: "Crear y agregar" }),
    );
  }

  it("creates the candidate and then the application", async () => {
    const nora = buildCandidate({ id: 30, fullName: "Nora Díaz" });
    createCandidateMock.mockResolvedValue(nora);
    createApplicationMock.mockResolvedValue(application(nora.id));
    const { user, dialog } = await openCreateMode();

    await fillAndSubmit(user, dialog);

    await waitFor(() =>
      expect(createApplicationMock).toHaveBeenCalledWith({
        jobId: 10,
        candidateId: 30,
      }),
    );
    expect(createCandidateMock.mock.calls[0][0]).toEqual({
      fullName: "Nora Díaz",
      email: "nora@example.com",
    });
    expect(createCandidateMock.mock.invocationCallOrder[0]).toBeLessThan(
      createApplicationMock.mock.invocationCallOrder[0],
    );
    expect(
      await screen.findByText("Candidato agregado a Postulado."),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
  });

  it("retries only the application when it failed after creating the candidate", async () => {
    const nora = buildCandidate({ id: 30, fullName: "Nora Díaz" });
    createCandidateMock.mockResolvedValue(nora);
    createApplicationMock
      .mockRejectedValueOnce(httpError(500))
      .mockResolvedValueOnce(application(nora.id));
    const { user, dialog } = await openCreateMode();

    await fillAndSubmit(user, dialog);

    expect(
      await within(dialog).findByText(
        "No se pudo agregar al candidato. Inténtalo de nuevo.",
      ),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByText(/Nora Díaz ya se creó como candidato/),
    ).toBeInTheDocument();

    await user.click(
      within(dialog).getByRole("button", { name: "Agregar a la vacante" }),
    );

    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
    expect(createCandidateMock).toHaveBeenCalledTimes(1);
    expect(createApplicationMock).toHaveBeenCalledTimes(2);
    expect(createApplicationMock).toHaveBeenLastCalledWith({
      jobId: 10,
      candidateId: 30,
    });
  });

  it("does not create an application when the candidate cannot be created", async () => {
    createCandidateMock.mockRejectedValue(httpError(409));
    const { user, dialog } = await openCreateMode();

    await fillAndSubmit(user, dialog);

    expect(
      await within(dialog).findByText(
        "Ya existe un candidato con este correo. Búscalo en la pestaña de búsqueda.",
      ),
    ).toBeInTheDocument();
    expect(createApplicationMock).not.toHaveBeenCalled();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });
});
