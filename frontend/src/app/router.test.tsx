import { render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it, vi } from "vitest";
import { Providers } from "@/app/providers";
import { routes } from "@/app/router";

vi.mock("@/shared/api/health", () => ({
  fetchHealth: vi.fn().mockResolvedValue({ status: "UP" }),
}));

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  );
}

describe("app router", () => {
  it("renders the home page at /", async () => {
    renderAt("/");
    expect(
      await screen.findByRole("heading", { name: "Mini ATS" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Comenzar" }),
    ).toBeInTheDocument();
  });

  it("renders the not found page for unknown routes", async () => {
    renderAt("/nada");
    expect(
      await screen.findByRole("heading", { name: "Página no encontrada" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Volver al inicio" }),
    ).toHaveAttribute("href", "/");
  });
});
