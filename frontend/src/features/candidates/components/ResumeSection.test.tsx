import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  downloadResume,
  getCandidate,
  uploadResume,
} from "@/features/candidates/api";
import { MAX_RESUME_BYTES, type Candidate } from "@/features/candidates/types";
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
const uploadResumeMock = vi.mocked(uploadResume);
const downloadResumeMock = vi.mocked(downloadResume);

const withoutResume = buildCandidate({ id: 20, hasResume: false });
const withResume = buildCandidate({ id: 20, hasResume: true });

function pdf(name = "cv.pdf", size = 1024) {
  return new File([new Uint8Array(size)], name, { type: "application/pdf" });
}

async function renderDetail(candidate: Candidate) {
  getCandidateMock.mockResolvedValue(candidate);
  signIn();
  renderRoute(`/candidates/${candidate.id}`);
  await screen.findByRole("heading", { name: candidate.fullName });
  // Files are selected without the accept filter, as a user could do from the OS dialog.
  return userEvent.setup({ applyAccept: false });
}

const fileInput = () => screen.getByLabelText("Archivo PDF (máximo 5 MB)");

describe("ResumeSection", () => {
  afterEach(() => {
    vi.resetAllMocks();
    signOut();
  });

  describe("upload", () => {
    it("rejects a file that is not a PDF without calling the API", async () => {
      const user = await renderDetail(withoutResume);

      await user.upload(
        fileInput(),
        new File(["hola"], "notas.txt", { type: "text/plain" }),
      );

      expect(
        await screen.findByText("El archivo debe ser un PDF."),
      ).toHaveAttribute("id", "resume-file-error");
      expect(screen.getByRole("button", { name: "Subir CV" })).toBeDisabled();
      expect(uploadResumeMock).not.toHaveBeenCalled();
    });

    it("rejects a file over 5 MB without calling the API", async () => {
      const user = await renderDetail(withoutResume);

      await user.upload(fileInput(), pdf("grande.pdf", MAX_RESUME_BYTES + 1));

      expect(
        await screen.findByText("El archivo supera el tamaño máximo de 5 MB."),
      ).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Subir CV" })).toBeDisabled();
      expect(uploadResumeMock).not.toHaveBeenCalled();
    });

    it("accepts a .pdf name even without a PDF content type", async () => {
      const user = await renderDetail(withoutResume);

      await user.upload(
        fileInput(),
        new File(["%PDF-1.4"], "cv.PDF", { type: "" }),
      );

      expect(screen.getByRole("button", { name: "Subir CV" })).toBeEnabled();
      expect(screen.queryByText("El archivo debe ser un PDF.")).toBeNull();
    });

    it("shows progress, then refreshes the candidate and clears the input", async () => {
      let reportProgress: (percent: number) => void = () => {};
      let finish: (candidate: Candidate) => void = () => {};
      uploadResumeMock.mockImplementation((_id, _file, onProgress) => {
        reportProgress = onProgress ?? reportProgress;
        return new Promise((resolve) => {
          finish = resolve;
        });
      });
      const user = await renderDetail(withoutResume);
      const file = pdf();

      await user.upload(fileInput(), file);
      await user.click(screen.getByRole("button", { name: "Subir CV" }));

      expect(uploadResumeMock).toHaveBeenCalledWith(
        20,
        file,
        expect.any(Function),
      );
      expect(
        await screen.findByRole("button", { name: "Subiendo…" }),
      ).toBeDisabled();
      act(() => reportProgress(40));
      expect(
        screen.getByRole("progressbar", { name: "Progreso de subida" }),
      ).toHaveAttribute("aria-valuenow", "40");

      getCandidateMock.mockResolvedValue(withResume);
      await act(async () => finish(withResume));

      expect(await screen.findByText("CV subido")).toBeInTheDocument();
      expect(
        await screen.findByRole("button", { name: "Reemplazar CV" }),
      ).toBeDisabled();
      expect(
        screen.getByRole("button", { name: "Descargar CV" }),
      ).toBeInTheDocument();
      expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
      expect(fileInput()).toHaveValue("");
      await waitFor(() => expect(getCandidateMock).toHaveBeenCalledTimes(2));
    });

    it.each([
      [400, "No se pudo subir el CV. Verifica que sea un PDF de máximo 5 MB."],
      [404, "No se encontró el candidato."],
      [500, "No se pudo subir el CV. Inténtalo de nuevo."],
    ])("explains a %i from the backend in Spanish", async (status, message) => {
      uploadResumeMock.mockRejectedValue(
        httpError(status, { status, detail: "The file is not a PDF" }),
      );
      const user = await renderDetail(withoutResume);

      await user.upload(fileInput(), pdf());
      await user.click(screen.getByRole("button", { name: "Subir CV" }));

      expect(await screen.findByText(message)).toHaveAttribute(
        "id",
        "resume-file-error",
      );
      expect(screen.queryByText("The file is not a PDF")).toBeNull();
    });

    it("labels the button Reemplazar CV when there is a CV", async () => {
      await renderDetail(withResume);

      expect(
        screen.getByRole("button", { name: "Reemplazar CV" }),
      ).toBeInTheDocument();
    });
  });

  describe("download", () => {
    const originalCreate = URL.createObjectURL;
    const originalRevoke = URL.revokeObjectURL;

    beforeEach(() => {
      URL.createObjectURL = vi.fn(() => "blob:cv-20");
      URL.revokeObjectURL = vi.fn();
    });

    afterEach(() => {
      URL.createObjectURL = originalCreate;
      URL.revokeObjectURL = originalRevoke;
      vi.restoreAllMocks();
    });

    it("is hidden without a CV", async () => {
      await renderDetail(withoutResume);

      expect(
        screen.queryByRole("button", { name: "Descargar CV" }),
      ).not.toBeInTheDocument();
    });

    it("saves the blob as cv-{id}.pdf and revokes its URL", async () => {
      const blob = new Blob(["%PDF-1.4"], { type: "application/pdf" });
      downloadResumeMock.mockResolvedValue(blob);
      const click = vi
        .spyOn(HTMLAnchorElement.prototype, "click")
        .mockImplementation(() => {});
      const user = await renderDetail(withResume);

      await user.click(screen.getByRole("button", { name: "Descargar CV" }));

      await waitFor(() =>
        expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:cv-20"),
      );
      expect(downloadResumeMock).toHaveBeenCalledWith(20);
      expect(URL.createObjectURL).toHaveBeenCalledWith(blob);
      expect(click).toHaveBeenCalledTimes(1);
      const link = click.mock.contexts[0];
      if (!(link instanceof HTMLAnchorElement)) {
        throw new Error("The download did not click a link");
      }
      expect(link.download).toBe("cv-20.pdf");
      expect(link.href).toBe("blob:cv-20");
      expect(link.isConnected).toBe(false);
    });

    it("shows a clear error when there is no CV to download", async () => {
      downloadResumeMock.mockRejectedValue(httpError(404));
      const user = await renderDetail(withResume);

      await user.click(screen.getByRole("button", { name: "Descargar CV" }));

      expect(
        await screen.findByText("Este candidato no tiene un CV disponible."),
      ).toBeInTheDocument();
      expect(URL.createObjectURL).not.toHaveBeenCalled();
    });
  });
});
