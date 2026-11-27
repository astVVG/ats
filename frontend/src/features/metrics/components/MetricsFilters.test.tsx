import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { Providers } from "@/app/providers";
import { listJobs } from "@/features/jobs/api";
import {
  INVERTED_RANGE_MESSAGE,
  MetricsFilters,
} from "@/features/metrics/components/MetricsFilters";
import {
  EMPTY_FILTERS,
  type MetricsFilterValues,
} from "@/features/metrics/types";
import {
  buildJob,
  createTestQueryClient,
  httpError,
  pageOf,
} from "@/test/utils";

vi.mock("@/features/jobs/api", () => ({ listJobs: vi.fn() }));

const listJobsMock = vi.mocked(listJobs);

beforeAll(() => {
  // jsdom lacks the pointer and scroll APIs the Radix Select relies on.
  Element.prototype.hasPointerCapture ??= () => false;
  Element.prototype.releasePointerCapture ??= () => {};
  Element.prototype.scrollIntoView ??= () => {};
});

function renderFilters(initial: MetricsFilterValues = EMPTY_FILTERS) {
  const changes: MetricsFilterValues[] = [];
  function Harness() {
    const [values, setValues] = useState(initial);
    return (
      <MetricsFilters
        values={values}
        onChange={(next) => {
          changes.push(next);
          setValues(next);
        }}
      />
    );
  }
  const user = userEvent.setup();
  render(
    <Providers queryClient={createTestQueryClient()}>
      <Harness />
    </Providers>,
  );
  return { user, changes };
}

describe("MetricsFilters", () => {
  afterEach(() => {
    vi.resetAllMocks();
  });

  it("lists every job, marks the closed ones and asks for up to 100", async () => {
    listJobsMock.mockResolvedValue(
      pageOf([
        buildJob({ id: 1, title: "Backend" }),
        buildJob({ id: 2, title: "Diseño", status: "CLOSED" }),
      ]),
    );
    const { user, changes } = renderFilters();

    const trigger = screen.getByRole("combobox", { name: "Vacante" });
    expect(trigger).toHaveTextContent("Todas las vacantes");
    await user.click(trigger);
    expect(
      await screen.findByRole("option", { name: "Diseño (Cerrada)" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Backend" })).toBeInTheDocument();
    await user.click(screen.getByRole("option", { name: "Diseño (Cerrada)" }));

    expect(changes.at(-1)).toEqual({ ...EMPTY_FILTERS, job: "2" });
    expect(listJobsMock).toHaveBeenCalledWith({ page: 0, size: 100 });
  });

  it("warns when there are more than 100 jobs", async () => {
    listJobsMock.mockResolvedValue({
      ...pageOf([buildJob({ id: 1 })]),
      totalElements: 130,
    });
    renderFilters();

    expect(
      await screen.findByText(
        "Se muestran las 1 vacantes más recientes de 130.",
      ),
    ).toBeVisible();
  });

  it("shows a job loading error with retry", async () => {
    listJobsMock.mockRejectedValueOnce(httpError(500));
    listJobsMock.mockResolvedValue(
      pageOf([buildJob({ id: 1, title: "Backend" })]),
    );
    const { user } = renderFilters();

    expect(
      await screen.findByText("No se pudieron cargar las vacantes."),
    ).toBeVisible();
    // The global option keeps working.
    expect(screen.getByRole("combobox", { name: "Vacante" })).toHaveTextContent(
      "Todas las vacantes",
    );
    await user.click(screen.getByRole("button", { name: "Reintentar" }));

    await user.click(screen.getByRole("combobox", { name: "Vacante" }));
    expect(
      await screen.findByRole("option", { name: "Backend" }),
    ).toBeVisible();
  });

  it("passes the date strings as typed and explains what they filter", async () => {
    listJobsMock.mockResolvedValue(pageOf([]));
    const { user, changes } = renderFilters();

    const from = screen.getByLabelText("Desde");
    expect(from).toHaveAttribute("type", "date");
    expect(from).toHaveAccessibleDescription(
      /filtran por la fecha de creación de la postulación/,
    );
    await user.type(from, "2026-01-15");
    await user.type(screen.getByLabelText("Hasta"), "2026-01-20");

    expect(changes.at(-1)).toEqual({
      job: "all",
      from: "2026-01-15",
      to: "2026-01-20",
    });
  });

  it("shows a message next to Desde when it is after Hasta", async () => {
    listJobsMock.mockResolvedValue(pageOf([]));
    renderFilters({ job: "all", from: "2026-02-01", to: "2026-01-31" });

    const from = screen.getByLabelText("Desde");
    expect(from).toHaveAttribute("aria-invalid", "true");
    expect(from).toHaveAccessibleDescription(
      expect.stringContaining(INVERTED_RANGE_MESSAGE),
    );
  });

  it("clears every filter", async () => {
    listJobsMock.mockResolvedValue(pageOf([buildJob({ id: 1 })]));
    const { user, changes } = renderFilters({
      job: "1",
      from: "2026-01-01",
      to: "2026-01-31",
    });

    await user.click(screen.getByRole("button", { name: "Limpiar filtros" }));

    expect(changes.at(-1)).toEqual(EMPTY_FILTERS);
    expect(screen.getByLabelText("Desde")).toHaveValue("");
    expect(
      screen.getByRole("button", { name: "Limpiar filtros" }),
    ).toBeDisabled();
  });
});
