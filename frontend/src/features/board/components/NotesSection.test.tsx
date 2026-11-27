import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Providers } from "@/app/providers";
import { createNote, deleteNote, listNotes } from "@/features/board/api";
import { NotesSection } from "@/features/board/components/NotesSection";
import type { Note, NotePage } from "@/features/board/types";
import {
  adminUser,
  buildNote,
  createTestQueryClient,
  httpError,
  pageOf,
  recruiterUser,
  signIn,
  signOut,
} from "@/test/utils";

vi.mock("@/features/board/api", () => ({
  listNotes: vi.fn(),
  createNote: vi.fn(),
  deleteNote: vi.fn(),
}));

const listNotesMock = vi.mocked(listNotes);
const createNoteMock = vi.mocked(createNote);
const deleteNoteMock = vi.mocked(deleteNote);

const otherRecruiter = { id: 3, name: "Otro Reclutador" };

const byRita = buildNote({
  id: 1,
  content: "Primera línea\nSegunda línea",
  author: { id: recruiterUser.id, name: recruiterUser.name },
  createdAt: "2026-04-12T15:30:00Z",
});
const byAda = buildNote({
  id: 2,
  content: "Nota del administrador",
  author: { id: adminUser.id, name: adminUser.name },
});
const byOther = buildNote({
  id: 3,
  content: "Nota ajena",
  author: otherRecruiter,
});

function page(content: Note[], number = 0, totalPages = 1): NotePage {
  return {
    ...pageOf(content, number, totalPages),
    totalElements: content.length,
  };
}

function renderNotes(applicationId = 7) {
  const user = userEvent.setup();
  render(
    <Providers queryClient={createTestQueryClient()}>
      <NotesSection applicationId={applicationId} />
    </Providers>,
  );
  return user;
}

function textarea() {
  return screen.getByLabelText("Nueva nota");
}

function noteList() {
  return screen.getByRole("list", { name: "Notas de la postulación" });
}

describe("NotesSection", () => {
  beforeEach(() => {
    signIn(recruiterUser);
  });

  afterEach(() => {
    vi.resetAllMocks();
    signOut();
    act(() => {
      toast.dismiss();
    });
  });

  // ---- states ----

  it("shows a loading state", () => {
    listNotesMock.mockReturnValue(new Promise(() => {}));
    renderNotes();

    expect(screen.getByText("Cargando notas…")).toBeInTheDocument();
  });

  it("shows an error with retry", async () => {
    listNotesMock.mockRejectedValueOnce(httpError(500));
    listNotesMock.mockResolvedValue(page([byRita]));
    const user = renderNotes();

    expect(
      await screen.findByText("No se pudieron cargar las notas."),
    ).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Reintentar" }));

    expect(
      await screen.findByText("Primera línea", { exact: false }),
    ).toBeVisible();
  });

  it("shows an empty state", async () => {
    listNotesMock.mockResolvedValue(page([]));
    renderNotes();

    expect(await screen.findByText("Todavía no hay notas.")).toBeVisible();
    expect(listNotesMock).toHaveBeenCalledWith(7, 0);
  });

  // ---- list ----

  it("shows author, local date and time, and the content with its line breaks", async () => {
    listNotesMock.mockResolvedValue(page([byRita]));
    renderNotes();

    const item = within(await screen.findByRole("list")).getByRole("listitem");
    expect(within(item).getByText(recruiterUser.name)).toBeInTheDocument();
    const formatted = new Intl.DateTimeFormat("es-MX", {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(new Date(byRita.createdAt));
    expect(within(item).getByText(formatted)).toHaveAttribute(
      "datetime",
      byRita.createdAt,
    );
    const content = within(item).getByText(/Primera línea/);
    expect(content.textContent).toBe("Primera línea\nSegunda línea");
    expect(content).toHaveClass("whitespace-pre-wrap");
  });

  it("never renders the content as HTML", async () => {
    listNotesMock.mockResolvedValue(
      page([
        buildNote({ content: '<img src=x onerror="alert(1)"><b>hola</b>' }),
      ]),
    );
    renderNotes();

    const list = await screen.findByRole("list");
    expect(
      within(list).getByText('<img src=x onerror="alert(1)"><b>hola</b>'),
    ).toBeInTheDocument();
    expect(list.querySelector("img, b")).toBeNull();
  });

  it("paginates 20 at a time", async () => {
    listNotesMock.mockImplementation((_id, number) =>
      Promise.resolve(page(number === 0 ? [byRita] : [byAda], number, 2)),
    );
    const user = renderNotes();

    expect(await screen.findByText("Página 1 de 2")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Siguiente" }));

    expect(await screen.findByText("Nota del administrador")).toBeVisible();
    expect(screen.getByText("Página 2 de 2")).toBeVisible();
    expect(listNotesMock).toHaveBeenLastCalledWith(7, 1);
  });

  // ---- adding ----

  it("rejects an empty or blank note without calling the API", async () => {
    listNotesMock.mockResolvedValue(page([]));
    const user = renderNotes();
    await screen.findByText("Todavía no hay notas.");

    await user.click(screen.getByRole("button", { name: "Agregar nota" }));
    expect(await screen.findByText("Escribe la nota")).toBeVisible();

    await user.type(textarea(), "   ");
    await user.click(screen.getByRole("button", { name: "Agregar nota" }));
    expect(await screen.findByText("Escribe la nota")).toBeVisible();
    expect(textarea()).toHaveAttribute("aria-invalid", "true");

    expect(createNoteMock).not.toHaveBeenCalled();
  });

  it("rejects a note longer than 2000 characters and counts the trimmed text", async () => {
    listNotesMock.mockResolvedValue(page([]));
    const user = renderNotes();
    await screen.findByText("Todavía no hay notas.");
    expect(screen.getByText("0/2000")).toBeInTheDocument();

    await user.click(textarea());
    await user.paste("x".repeat(2001));
    expect(screen.getByText("2001/2000")).toHaveClass("text-destructive");
    await user.click(screen.getByRole("button", { name: "Agregar nota" }));

    expect(
      await screen.findByText("La nota admite como máximo 2000 caracteres"),
    ).toBeVisible();
    expect(createNoteMock).not.toHaveBeenCalled();
  });

  it("sends the trimmed text, clears the form and shows the new note first", async () => {
    let resolveCreate: (note: Note) => void = () => {};
    createNoteMock.mockReturnValue(
      new Promise((resolve) => {
        resolveCreate = resolve;
      }),
    );
    const created = buildNote({ id: 9, content: "Nueva" });
    listNotesMock.mockResolvedValueOnce(page([byRita]));
    const user = renderNotes();
    await screen.findByText(/Primera línea/);

    await user.click(textarea());
    await user.paste(`  ${"y".repeat(2000)}  `);
    expect(screen.getByText("2000/2000")).toBeInTheDocument();
    await user.clear(textarea());
    await user.type(textarea(), "  Nueva  ");
    await user.click(screen.getByRole("button", { name: "Agregar nota" }));

    expect(
      await screen.findByRole("button", { name: "Guardando…" }),
    ).toBeDisabled();
    expect(createNoteMock).toHaveBeenCalledWith(7, { content: "Nueva" });

    listNotesMock.mockResolvedValue(page([created, byRita]));
    await act(async () => resolveCreate(created));

    await waitFor(() => expect(textarea()).toHaveValue(""));
    const items = within(noteList()).getAllByRole("listitem");
    await waitFor(() => expect(items[0]).toHaveTextContent("Nueva"));
    expect(screen.getByRole("button", { name: "Agregar nota" })).toBeEnabled();
  });

  it("goes back to the first page after adding a note", async () => {
    listNotesMock.mockImplementation((_id, number) =>
      Promise.resolve(page(number === 0 ? [byRita] : [byAda], number, 2)),
    );
    createNoteMock.mockResolvedValue(buildNote({ id: 9 }));
    const user = renderNotes();
    await screen.findByText("Página 1 de 2");
    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    await screen.findByText("Página 2 de 2");

    await user.type(textarea(), "Otra");
    await user.click(screen.getByRole("button", { name: "Agregar nota" }));

    expect(await screen.findByText("Página 1 de 2")).toBeVisible();
    expect(listNotesMock).toHaveBeenLastCalledWith(7, 0);
  });

  it("shows a 400 in the field and keeps the text", async () => {
    listNotesMock.mockResolvedValue(page([]));
    createNoteMock.mockRejectedValue(
      httpError(400, {
        status: 400,
        errors: [{ field: "content", message: "x" }],
      }),
    );
    const user = renderNotes();
    await screen.findByText("Todavía no hay notas.");

    await user.type(textarea(), "Texto");
    await user.click(screen.getByRole("button", { name: "Agregar nota" }));

    expect(
      await screen.findByText(
        "Revisa la nota: no puede estar vacía ni pasar de 2000 caracteres.",
      ),
    ).toBeVisible();
    expect(textarea()).toHaveValue("Texto");
  });

  it("keeps the text when saving fails", async () => {
    listNotesMock.mockResolvedValue(page([]));
    createNoteMock.mockRejectedValue(httpError(500));
    const user = renderNotes();
    await screen.findByText("Todavía no hay notas.");

    await user.type(textarea(), "No la pierdas");
    await user.click(screen.getByRole("button", { name: "Agregar nota" }));

    expect(
      await screen.findByText(
        "No se pudo guardar la nota. Inténtalo de nuevo.",
      ),
    ).toBeVisible();
    expect(textarea()).toHaveValue("No la pierdas");
    expect(screen.getByRole("button", { name: "Agregar nota" })).toBeEnabled();
  });

  // ---- deleting ----

  it("lets a RECRUITER delete only their own notes", async () => {
    listNotesMock.mockResolvedValue(page([byRita, byAda, byOther]));
    renderNotes();
    await screen.findByText("Nota ajena");

    expect(
      screen.getAllByRole("button", { name: /^Eliminar nota de/ }),
    ).toHaveLength(1);
    expect(
      screen.getByRole("button", {
        name: `Eliminar nota de ${recruiterUser.name}`,
      }),
    ).toBeInTheDocument();
  });

  it("lets an ADMIN delete any note", async () => {
    signIn(adminUser);
    listNotesMock.mockResolvedValue(page([byRita, byAda, byOther]));
    renderNotes();
    await screen.findByText("Nota ajena");

    expect(
      screen.getAllByRole("button", { name: /^Eliminar nota de/ }),
    ).toHaveLength(3);
  });

  it("asks for confirmation before deleting", async () => {
    listNotesMock.mockResolvedValueOnce(page([byRita]));
    deleteNoteMock.mockResolvedValue(undefined);
    const user = renderNotes();
    await screen.findByText(/Primera línea/);

    await user.click(
      screen.getByRole("button", {
        name: `Eliminar nota de ${recruiterUser.name}`,
      }),
    );
    const dialog = screen.getByRole("dialog", { name: "¿Eliminar la nota?" });
    await user.click(within(dialog).getByRole("button", { name: "Cancelar" }));
    expect(deleteNoteMock).not.toHaveBeenCalled();

    listNotesMock.mockResolvedValue(page([]));
    await user.click(
      screen.getByRole("button", {
        name: `Eliminar nota de ${recruiterUser.name}`,
      }),
    );
    await user.click(
      within(screen.getByRole("dialog")).getByRole("button", {
        name: "Eliminar",
      }),
    );

    expect(deleteNoteMock).toHaveBeenCalledWith(1);
    expect(await screen.findByText("Nota eliminada.")).toBeVisible();
    expect(await screen.findByText("Todavía no hay notas.")).toBeVisible();
  });

  it("shows a permission toast on 403 and keeps the note", async () => {
    listNotesMock.mockResolvedValue(page([byRita]));
    deleteNoteMock.mockRejectedValue(httpError(403));
    const user = renderNotes();
    await screen.findByText(/Primera línea/);

    await user.click(
      screen.getByRole("button", {
        name: `Eliminar nota de ${recruiterUser.name}`,
      }),
    );
    await user.click(
      within(screen.getByRole("dialog")).getByRole("button", {
        name: "Eliminar",
      }),
    );

    expect(
      await screen.findByText("No tienes permiso para eliminar esta nota."),
    ).toBeVisible();
    expect(screen.getByText(/Primera línea/)).toBeInTheDocument();
    expect(listNotesMock).toHaveBeenCalledTimes(1);
  });

  it("reloads the list when the note no longer exists", async () => {
    listNotesMock.mockResolvedValueOnce(page([byRita]));
    listNotesMock.mockResolvedValue(page([]));
    deleteNoteMock.mockRejectedValue(httpError(404));
    const user = renderNotes();
    await screen.findByText(/Primera línea/);

    await user.click(
      screen.getByRole("button", {
        name: `Eliminar nota de ${recruiterUser.name}`,
      }),
    );
    await user.click(
      within(screen.getByRole("dialog")).getByRole("button", {
        name: "Eliminar",
      }),
    );

    expect(await screen.findByText("La nota ya no existe.")).toBeVisible();
    expect(await screen.findByText("Todavía no hay notas.")).toBeVisible();
    expect(listNotesMock).toHaveBeenCalledTimes(2);
  });

  it("goes back a page when the only note of a later page is deleted", async () => {
    let onSecondPage = [byRita];
    listNotesMock.mockImplementation((_id, number) =>
      Promise.resolve(
        number === 0
          ? page([byAda], 0, onSecondPage.length > 0 ? 2 : 1)
          : page(onSecondPage, 1, onSecondPage.length > 0 ? 2 : 1),
      ),
    );
    deleteNoteMock.mockImplementation(() => {
      onSecondPage = [];
      return Promise.resolve();
    });
    const user = renderNotes();
    await screen.findByText("Página 1 de 2");
    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    await screen.findByText(/Primera línea/);

    await user.click(
      screen.getByRole("button", {
        name: `Eliminar nota de ${recruiterUser.name}`,
      }),
    );
    await user.click(
      within(screen.getByRole("dialog")).getByRole("button", {
        name: "Eliminar",
      }),
    );

    expect(await screen.findByText("Nota del administrador")).toBeVisible();
    expect(screen.queryByText("Todavía no hay notas.")).toBeNull();
    expect(screen.queryByRole("navigation", { name: "Paginación" })).toBeNull();
  });
});
