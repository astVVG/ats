import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { QuickCandidateForm } from "@/features/board/components/QuickCandidateForm";
import { buildCandidate, httpError } from "@/test/utils";

function renderForm({
  onSubmit = vi.fn().mockResolvedValue(undefined),
  created = null,
  pending = false,
}: Partial<Parameters<typeof QuickCandidateForm>[0]> = {}) {
  render(
    <QuickCandidateForm
      created={created}
      pending={pending}
      onSubmit={onSubmit}
    />,
  );
  return { onSubmit, user: userEvent.setup() };
}

const nameField = () => screen.getByLabelText("Nombre completo");
const emailField = () => screen.getByLabelText("Correo");
const submit = () => screen.getByRole("button", { name: "Crear y agregar" });

describe("QuickCandidateForm", () => {
  it("requires name and email and links the errors to the fields", async () => {
    const { onSubmit, user } = renderForm();

    await user.click(submit());

    expect(await screen.findByText("Ingresa el nombre")).toBeInTheDocument();
    expect(screen.getByText("Ingresa el correo")).toBeInTheDocument();
    expect(nameField()).toHaveAttribute("aria-invalid", "true");
    expect(nameField()).toHaveAccessibleDescription("Ingresa el nombre");
    expect(emailField()).toHaveAccessibleDescription("Ingresa el correo");
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("validates the email format", async () => {
    const { onSubmit, user } = renderForm();

    await user.type(nameField(), "Nora Díaz");
    await user.type(emailField(), "nora@");
    await user.click(submit());

    expect(
      await screen.findByText("Ingresa un correo válido"),
    ).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("applies the same length limits as the candidate form", async () => {
    const { onSubmit, user } = renderForm();

    await user.click(nameField());
    await user.paste("a".repeat(201));
    await user.click(emailField());
    await user.paste(`${"b".repeat(243)}@example.com`);
    await user.click(submit());

    expect(
      await screen.findByText("El nombre admite como máximo 200 caracteres"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("El correo admite como máximo 254 caracteres"),
    ).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("sends the trimmed values", async () => {
    const { onSubmit, user } = renderForm();

    await user.type(nameField(), "  Nora Díaz ");
    await user.type(emailField(), " nora@example.com ");
    await user.click(submit());

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({
        fullName: "Nora Díaz",
        email: "nora@example.com",
      }),
    );
  });

  it("shows a duplicated email under the email field", async () => {
    const { user } = renderForm({
      onSubmit: vi.fn().mockRejectedValue(httpError(409)),
    });

    await user.type(nameField(), "Nora Díaz");
    await user.type(emailField(), "nora@example.com");
    await user.click(submit());

    const message =
      "Ya existe un candidato con este correo. Búscalo en la pestaña de búsqueda.";
    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(emailField()).toHaveAccessibleDescription(message);
  });

  it("shows the server's field errors under each field", async () => {
    const { user } = renderForm({
      onSubmit: vi.fn().mockRejectedValue(
        httpError(400, {
          status: 400,
          errors: [
            { field: "fullName", message: "no debe estar vacío" },
            { field: "email", message: "debe ser un correo válido" },
          ],
        }),
      ),
    });

    await user.type(nameField(), "Nora Díaz");
    await user.type(emailField(), "nora@example.com");
    await user.click(submit());

    expect(await screen.findByText("no debe estar vacío")).toBeInTheDocument();
    expect(nameField()).toHaveAccessibleDescription("no debe estar vacío");
    expect(emailField()).toHaveAccessibleDescription(
      "debe ser un correo válido",
    );
  });

  it("shows a general error for other failures", async () => {
    const { user } = renderForm({
      onSubmit: vi.fn().mockRejectedValue(httpError(500)),
    });

    await user.type(nameField(), "Nora Díaz");
    await user.type(emailField(), "nora@example.com");
    await user.click(submit());

    expect(
      await screen.findByText(
        "No se pudo crear el candidato. Inténtalo de nuevo.",
      ),
    ).toBeInTheDocument();
  });

  it("only offers to add the candidate once it has been created", () => {
    renderForm({ created: buildCandidate({ fullName: "Nora Díaz" }) });

    expect(
      screen.getByText(/Nora Díaz ya se creó como candidato/),
    ).toBeInTheDocument();
    expect(nameField()).toHaveAttribute("readonly");
    expect(emailField()).toHaveAttribute("readonly");
    expect(
      screen.getByRole("button", { name: "Agregar a la vacante" }),
    ).toBeEnabled();
  });

  it("disables the submit button while pending", () => {
    renderForm({ pending: true });

    expect(screen.getByRole("button", { name: "Agregando…" })).toBeDisabled();
  });
});
