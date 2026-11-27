import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import {
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from "vitest";
import { listJobs } from "@/features/jobs/api";
import {
  getFunnel,
  getTimeInStage,
  getTimeToHire,
} from "@/features/metrics/api";
import type {
  Funnel,
  MetricsFilters,
  TimeInStage,
  TimeToHire,
} from "@/features/metrics/types";
import { STAGES } from "@/shared/lib/stages";
import {
  adminUser,
  buildJob,
  httpError,
  pageOf,
  recruiterUser,
  renderRoute,
  signIn,
  signOut,
} from "@/test/utils";

vi.mock("@/features/metrics/api", () => ({
  getFunnel: vi.fn(),
  getTimeInStage: vi.fn(),
  getTimeToHire: vi.fn(),
}));

vi.mock("@/features/jobs/api", () => ({
  listJobs: vi.fn(),
  getJob: vi.fn(),
}));

// jsdom has no layout, so the charts are replaced; their tables are real.
vi.mock("recharts", () => {
  const passthrough = ({ children }: { children?: ReactNode }) => (
    <div>{children}</div>
  );
  return {
    ResponsiveContainer: passthrough,
    BarChart: passthrough,
    Bar: passthrough,
    Cell: () => null,
    XAxis: () => null,
    YAxis: () => null,
    CartesianGrid: () => null,
    Tooltip: () => null,
  };
});

const funnelMock = vi.mocked(getFunnel);
const timeMock = vi.mocked(getTimeInStage);
const hireMock = vi.mocked(getTimeToHire);

const funnel: Funnel = {
  stages: [
    { stage: "APPLIED", count: 4, conversionToNext: 0.5 },
    { stage: "INTERVIEW", count: 2, conversionToNext: 1 },
    { stage: "OFFER", count: 2, conversionToNext: 1 },
    { stage: "HIRED", count: 2, conversionToNext: null },
    { stage: "REJECTED", count: 1, conversionToNext: null },
  ],
};
const times: TimeInStage = {
  stages: [
    { stage: "APPLIED", averageHours: 26, samples: 3 },
    { stage: "INTERVIEW", averageHours: 36, samples: 2 },
    { stage: "OFFER", averageHours: 33, samples: 2 },
    { stage: "HIRED", averageHours: null, samples: 0 },
    { stage: "REJECTED", averageHours: null, samples: 0 },
  ],
};
const hires: TimeToHire = { averageDays: 3, hiredCount: 2 };

const emptyFunnel: Funnel = {
  stages: STAGES.map((stage) => ({ stage, count: 0, conversionToNext: null })),
};
const emptyTimes: TimeInStage = {
  stages: STAGES.map((stage) => ({ stage, averageHours: null, samples: 0 })),
};

beforeAll(() => {
  // jsdom lacks the pointer and scroll APIs the Radix Select relies on.
  Element.prototype.hasPointerCapture ??= () => false;
  Element.prototype.releasePointerCapture ??= () => {};
  Element.prototype.scrollIntoView ??= () => {};
});

function region(name: string) {
  return screen.getByRole("region", { name });
}

function tableRows(name: string) {
  return within(screen.getByRole("table", { name }))
    .getAllByRole("row")
    .slice(1)
    .map((row) => row.textContent);
}

const FUNNEL_TABLE =
  "Embudo: postulaciones por etapa y conversión a la siguiente";
const TIME_TABLE = "Tiempo promedio por etapa, en horas, y número de muestras";

function lastFilters(
  mock: typeof funnelMock | typeof timeMock | typeof hireMock,
) {
  return mock.mock.lastCall?.[0];
}

function expectAllCalledWith(filters: MetricsFilters) {
  expect(lastFilters(funnelMock)).toEqual(filters);
  expect(lastFilters(timeMock)).toEqual(filters);
  expect(lastFilters(hireMock)).toEqual(filters);
}

function renderMetrics(user = recruiterUser) {
  signIn(user);
  const router = renderRoute("/metrics");
  return { router, user: userEvent.setup() };
}

describe("MetricsPage", () => {
  beforeEach(() => {
    vi.mocked(listJobs).mockResolvedValue(
      pageOf([
        buildJob({ id: 1, title: "Backend" }),
        buildJob({ id: 2, title: "Diseño", status: "CLOSED" }),
      ]),
    );
    funnelMock.mockResolvedValue(funnel);
    timeMock.mockResolvedValue(times);
    hireMock.mockResolvedValue(hires);
  });

  afterEach(() => {
    vi.resetAllMocks();
    signOut();
  });

  // ---- route and navigation ----

  it("requires a session", async () => {
    const router = renderRoute("/metrics");

    await waitFor(() => expect(router.state.location.pathname).toBe("/login"));
    expect(funnelMock).not.toHaveBeenCalled();
  });

  it.each([
    ["ADMIN", adminUser],
    ["RECRUITER", recruiterUser],
  ] as const)("is reached from the navigation as %s", async (_role, user) => {
    signIn(user);
    const router = renderRoute("/");
    const events = userEvent.setup();

    await events.click(
      within(
        await screen.findByRole("navigation", { name: "Principal" }),
      ).getByRole("link", { name: "Métricas" }),
    );

    expect(router.state.location.pathname).toBe("/metrics");
    expect(
      await screen.findByRole("heading", { level: 1, name: "Métricas" }),
    ).toBeVisible();
  });

  // ---- values ----

  it("shows the API values without filters", async () => {
    renderMetrics();

    expect(await screen.findByText("3 días")).toBeVisible();
    expect(
      within(region("Tiempo de contratación")).getByText("2 contratados"),
    ).toBeVisible();
    expect(tableRows(FUNNEL_TABLE)).toEqual([
      "Postulado450 %",
      "Entrevista2100 %",
      "Oferta2100 %",
      "Contratado2—",
      "Rechazado1—",
    ]);
    expect(tableRows(TIME_TABLE)).toEqual([
      "Postulado26 h3",
      "Entrevista36 h2",
      "Oferta33 h2",
      "ContratadoSin datos0",
      "RechazadoSin datos0",
    ]);
    expectAllCalledWith({});
  });

  it("applies the job to the three metrics and clears it", async () => {
    const { user } = renderMetrics();
    await screen.findByText("3 días");

    await user.click(screen.getByRole("combobox", { name: "Vacante" }));
    await user.click(
      await screen.findByRole("option", { name: "Diseño (Cerrada)" }),
    );

    await waitFor(() => expectAllCalledWith({ jobId: 2 }));

    await user.click(screen.getByRole("button", { name: "Limpiar filtros" }));
    await waitFor(() => expectAllCalledWith({}));
    expect(screen.getByRole("combobox", { name: "Vacante" })).toHaveTextContent(
      "Todas las vacantes",
    );
  });

  it("applies the dates as typed to the three metrics", async () => {
    const { user } = renderMetrics();
    await screen.findByText("3 días");

    await user.type(screen.getByLabelText("Desde"), "2026-01-15");
    await user.type(screen.getByLabelText("Hasta"), "2026-01-20");

    await waitFor(() =>
      expectAllCalledWith({ from: "2026-01-15", to: "2026-01-20" }),
    );
  });

  it("does not call the API for an inverted range nor show old results", async () => {
    const { user } = renderMetrics();
    await screen.findByText("3 días");

    await user.type(screen.getByLabelText("Hasta"), "2026-01-10");
    await user.type(screen.getByLabelText("Desde"), "2026-02-01");

    expect(
      screen.getByText(
        "La fecha Desde no puede ser posterior a la fecha Hasta.",
      ),
    ).toBeVisible();
    expect(
      screen.getAllByText(
        "Corrige el rango de fechas para ver los resultados.",
      ),
    ).toHaveLength(3);
    expect(screen.queryByText("3 días")).toBeNull();
    expect(screen.queryByRole("table")).toBeNull();
    for (const mock of [funnelMock, timeMock, hireMock]) {
      expect(
        mock.mock.calls.some(([filters]) => filters.from === "2026-02-01"),
      ).toBe(false);
    }
  });

  it("does not show the previous filter's numbers while the new ones load", async () => {
    const { user } = renderMetrics();
    await screen.findByText("3 días");
    hireMock.mockReturnValue(new Promise(() => {}));

    await user.click(screen.getByRole("combobox", { name: "Vacante" }));
    await user.click(await screen.findByRole("option", { name: "Backend" }));

    await waitFor(() => expect(screen.queryByText("3 días")).toBeNull());
    expect(
      within(region("Tiempo de contratación")).getByText("Cargando…"),
    ).toBeVisible();
  });

  it("asks again every time the page is entered", async () => {
    const { router } = renderMetrics();
    await screen.findByText("3 días");
    expect(funnelMock).toHaveBeenCalledTimes(1);

    await act(() => router.navigate("/"));
    await act(() => router.navigate("/metrics"));

    await waitFor(() => expect(funnelMock).toHaveBeenCalledTimes(2));
    expect(timeMock).toHaveBeenCalledTimes(2);
    expect(hireMock).toHaveBeenCalledTimes(2);
  });

  // ---- states ----

  it("shows a loading state per section", async () => {
    funnelMock.mockReturnValue(new Promise(() => {}));
    renderMetrics();

    expect(await screen.findByText("3 días")).toBeVisible();
    expect(within(region("Embudo")).getByText("Cargando…")).toBeVisible();
    expect(
      within(region("Tiempo por etapa")).getByRole("table"),
    ).toBeInTheDocument();
  });

  it("shows an error with retry in one section without blocking the others", async () => {
    funnelMock.mockRejectedValueOnce(httpError(500));
    const { user } = renderMetrics();

    expect(
      await within(region("Embudo")).findByText("No se pudo cargar el embudo."),
    ).toBeVisible();
    expect(screen.getByText("3 días")).toBeVisible();
    expect(tableRows(TIME_TABLE)).toHaveLength(5);

    await user.click(
      within(region("Embudo")).getByRole("button", { name: "Reintentar" }),
    );

    expect(
      await screen.findByRole("table", { name: FUNNEL_TABLE }),
    ).toBeVisible();
  });

  it.each([
    ["Tiempo de contratación", "el tiempo de contratación", hireMock],
    ["Tiempo por etapa", "el tiempo por etapa", timeMock],
  ] as const)("shows an error with retry in %s", async (name, what, mock) => {
    mock.mockRejectedValueOnce(httpError(500));
    const { user } = renderMetrics();

    expect(
      await within(region(name)).findByText(`No se pudo cargar ${what}.`),
    ).toBeVisible();
    await user.click(
      within(region(name)).getByRole("button", { name: "Reintentar" }),
    );
    await waitFor(() =>
      expect(
        within(region(name)).queryByText(`No se pudo cargar ${what}.`),
      ).toBeNull(),
    );
  });

  it("explains a missing job", async () => {
    funnelMock.mockRejectedValue(httpError(404));
    timeMock.mockRejectedValue(httpError(404));
    hireMock.mockRejectedValue(httpError(404));
    renderMetrics();

    expect(
      await within(region("Embudo")).findByText(
        "La vacante seleccionada ya no existe.",
      ),
    ).toBeVisible();
  });

  it("shows each section as empty on its own, never as 0", async () => {
    hireMock.mockResolvedValue({ averageDays: null, hiredCount: 0 });
    funnelMock.mockResolvedValue(emptyFunnel);
    renderMetrics();

    const hire = region("Tiempo de contratación");
    expect(await within(hire).findByText("Sin datos")).toBeVisible();
    expect(within(hire).queryByText(/0 días/)).toBeNull();
    expect(
      within(region("Embudo")).getByText(
        "No hay postulaciones con estos filtros.",
      ),
    ).toBeVisible();
    // The time section still has data.
    expect(tableRows(TIME_TABLE)).toHaveLength(5);
  });

  it("shows the stage times as empty when no stay has finished", async () => {
    timeMock.mockResolvedValue(emptyTimes);
    renderMetrics();

    expect(
      await within(region("Tiempo por etapa")).findByText(
        "Todavía no hay estancias terminadas con estos filtros.",
      ),
    ).toBeVisible();
    expect(screen.getByRole("table", { name: FUNNEL_TABLE })).toBeVisible();
  });

  it("keeps the global metrics working when the jobs fail to load", async () => {
    vi.mocked(listJobs).mockRejectedValue(httpError(500));
    renderMetrics();

    expect(
      await screen.findByText("No se pudieron cargar las vacantes."),
    ).toBeVisible();
    expect(await screen.findByText("3 días")).toBeVisible();
    expectAllCalledWith({});
  });

  it("explains conversions over 100 % without capping them", async () => {
    funnelMock.mockResolvedValue({
      stages: [
        { stage: "APPLIED", count: 3, conversionToNext: 0.3333 },
        { stage: "INTERVIEW", count: 1, conversionToNext: 3 },
        { stage: "OFFER", count: 3, conversionToNext: 0 },
        { stage: "HIRED", count: 0, conversionToNext: null },
        { stage: "REJECTED", count: 0, conversionToNext: null },
      ],
    });
    renderMetrics();

    expect(await screen.findByText("300 %")).toBeVisible();
    expect(screen.getByText("33.3 %")).toBeVisible();
    expect(screen.getByText(/se saltaron etapas/)).toBeVisible();
  });
});
