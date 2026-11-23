import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Pagination } from "@/shared/components/Pagination";

function renderPagination(page: number, totalPages: number) {
  const onPageChange = vi.fn();
  render(
    <Pagination
      page={page}
      totalPages={totalPages}
      onPageChange={onPageChange}
    />,
  );
  return onPageChange;
}

describe("Pagination", () => {
  it("shows the zero-based page as one-based", () => {
    renderPagination(1, 3);
    expect(screen.getByText("Página 2 de 3")).toBeInTheDocument();
  });

  it("disables Anterior on the first page", async () => {
    const onPageChange = renderPagination(0, 3);

    expect(screen.getByRole("button", { name: "Anterior" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Siguiente" }));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  it("disables Siguiente on the last page", async () => {
    const onPageChange = renderPagination(2, 3);

    expect(screen.getByRole("button", { name: "Siguiente" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Anterior" }));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  it("treats an empty result as a single page", () => {
    renderPagination(0, 0);

    expect(screen.getByText("Página 1 de 1")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Anterior" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Siguiente" })).toBeDisabled();
  });
});
