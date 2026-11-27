import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CandidatePicker } from "@/features/board/components/CandidatePicker";
import { listCandidates } from "@/features/candidates/api";
import {
  buildCandidate,
  createTestQueryClient,
  httpError,
  pageOf,
} from "@/test/utils";

vi.mock("@/features/candidates/api", () => ({
  listCandidates: vi.fn(),
}));

const listCandidatesMock = vi.mocked(listCandidates);

const laura = buildCandidate({
  id: 20,
  fullName: "Laura Gómez",
  email: "laura@example.com",
});
const mario = buildCandidate({
  id: 21,
  fullName: "Mario Ruiz",
  email: "mario@example.com",
});

function renderPicker(onPick = vi.fn(), disabled = false) {
  render(
    <QueryClientProvider client={createTestQueryClient()}>
      <CandidatePicker onPick={onPick} disabled={disabled} />
    </QueryClientProvider>,
  );
  return onPick;
}

describe("CandidatePicker", () => {
  afterEach(() => {
    vi.resetAllMocks();
  });

  it("lists the most recent candidates first, ten at a time", async () => {
    listCandidatesMock.mockResolvedValue(pageOf([laura, mario]));
    renderPicker();

    expect(await screen.findByText("Laura Gómez")).toBeInTheDocument();
    expect(screen.getByText("mario@example.com")).toBeInTheDocument();
    expect(listCandidatesMock).toHaveBeenCalledWith({
      page: 0,
      size: 10,
      q: undefined,
    });
  });

  it("searches with a 300 ms debounce", async () => {
    listCandidatesMock.mockResolvedValue(pageOf([laura]));
    const user = userEvent.setup();
    renderPicker();
    await screen.findByText("Laura Gómez");

    await user.type(screen.getByLabelText("Buscar"), "  lau ");

    await waitFor(() =>
      expect(listCandidatesMock).toHaveBeenLastCalledWith({
        page: 0,
        size: 10,
        q: "lau",
      }),
    );
    const queries = listCandidatesMock.mock.calls.map(([params]) => params.q);
    expect(queries).toEqual([undefined, "lau"]);
  });

  it("shows a loading state", async () => {
    listCandidatesMock.mockReturnValue(new Promise(() => {}));
    renderPicker();

    expect(await screen.findByText("Buscando candidatos…")).toHaveAttribute(
      "role",
      "status",
    );
  });

  it("shows an error with retry", async () => {
    listCandidatesMock
      .mockRejectedValueOnce(httpError(500))
      .mockResolvedValueOnce(pageOf([laura]));
    const user = userEvent.setup();
    renderPicker();

    expect(
      await screen.findByText("No se pudieron buscar los candidatos."),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Reintentar" }));

    expect(await screen.findByText("Laura Gómez")).toBeInTheDocument();
    expect(listCandidatesMock).toHaveBeenCalledTimes(2);
  });

  it("shows an empty state", async () => {
    listCandidatesMock.mockResolvedValue(pageOf([]));
    renderPicker();

    expect(
      await screen.findByText("No se encontraron candidatos."),
    ).toBeInTheDocument();
  });

  it("asks to narrow the search when there are more pages", async () => {
    listCandidatesMock.mockResolvedValue(pageOf([laura, mario], 0, 3));
    renderPicker();

    expect(
      await screen.findByText("Hay más resultados. Afina la búsqueda."),
    ).toBeInTheDocument();
  });

  it("does not show the notice when everything fits", async () => {
    listCandidatesMock.mockResolvedValue(pageOf([laura]));
    renderPicker();

    await screen.findByText("Laura Gómez");
    expect(
      screen.queryByText("Hay más resultados. Afina la búsqueda."),
    ).not.toBeInTheDocument();
  });

  it("picks a candidate with a labelled button", async () => {
    listCandidatesMock.mockResolvedValue(pageOf([laura, mario]));
    const user = userEvent.setup();
    const onPick = renderPicker();

    await user.click(
      await screen.findByRole("button", { name: "Agregar a Mario Ruiz" }),
    );

    expect(onPick).toHaveBeenCalledWith(mario);
  });

  it("disables the buttons while an application is created", async () => {
    listCandidatesMock.mockResolvedValue(pageOf([laura]));
    renderPicker(vi.fn(), true);

    expect(
      await screen.findByRole("button", { name: "Agregar a Laura Gómez" }),
    ).toBeDisabled();
  });
});
