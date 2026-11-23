import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { getCandidate } from "@/features/candidates/api";
import {
  buildCandidate,
  httpError,
  renderRoute,
  signIn,
  signOut,
} from "@/test/utils";

vi.mock("@/features/candidates/api", () => ({
  listCandidates: vi.fn(),
  getCandidate: vi.fn(),
  createCandidate: vi.fn(),
  updateCandidate: vi.fn(),
  uploadResume: vi.fn(),
  downloadResume: vi.fn(),
}));

const getCandidateMock = vi.mocked(getCandidate);

function renderDetail(path = "/candidates/20") {
  signIn();
  return renderRoute(path);
}

describe("CandidateDetailPage", () => {
  afterEach(() => {
    vi.resetAllMocks();
    signOut();
  });

  it("shows a loading state", async () => {
    getCandidateMock.mockReturnValue(new Promise(() => {}));
    renderDetail();

    expect(await screen.findByText("Cargando candidato…")).toHaveAttribute(
      "role",
      "status",
    );
  });

  it("shows the candidate data with an edit link and the CV section", async () => {
    getCandidateMock.mockResolvedValue(buildCandidate({ id: 20, phone: null }));
    renderDetail();

    expect(
      await screen.findByRole("heading", { name: "Laura Gómez" }),
    ).toBeInTheDocument();
    expect(screen.getByText("laura@example.com")).toBeInTheDocument();
    expect(screen.getByText("—")).toBeInTheDocument();
    expect(screen.getByText("LinkedIn")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Editar" })).toHaveAttribute(
      "href",
      "/candidates/20/edit",
    );
    expect(screen.getByRole("heading", { name: "CV" })).toBeInTheDocument();
    expect(getCandidateMock).toHaveBeenCalledWith(20);
  });

  it("shows not found for a missing candidate", async () => {
    getCandidateMock.mockRejectedValue(httpError(404));
    renderDetail("/candidates/999");

    expect(
      await screen.findByRole("heading", { name: "Candidato no encontrado" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Volver a candidatos" }),
    ).toHaveAttribute("href", "/candidates");
  });

  it("retries after a load error", async () => {
    getCandidateMock
      .mockRejectedValueOnce(httpError(500))
      .mockResolvedValueOnce(buildCandidate({ id: 20 }));
    renderDetail();

    expect(
      await screen.findByText("No se pudo cargar el candidato."),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Reintentar" }));

    expect(
      await screen.findByRole("heading", { name: "Laura Gómez" }),
    ).toBeInTheDocument();
  });
});
