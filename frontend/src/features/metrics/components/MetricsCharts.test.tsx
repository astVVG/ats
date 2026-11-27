import { render, screen, within } from "@testing-library/react";
import type { ReactElement, ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { funnelRows, stageTimeRows } from "@/features/metrics/chart-rows";
import { FunnelChart } from "@/features/metrics/components/FunnelChart";
import { StageTimeChart } from "@/features/metrics/components/StageTimeChart";
import { STAGES } from "@/shared/lib/stages";

// jsdom has no layout, so Recharts would draw nothing. The mock keeps the
// props each Recharts component receives to check data, colors and ticks.
const charts = vi.hoisted(() => ({
  calls: [] as { type: string; props: Record<string, unknown> }[],
}));
vi.mock("recharts", () => {
  const record =
    (type: string) =>
    (props: Record<string, unknown> & { children?: ReactNode }) => {
      charts.calls.push({ type, props });
      return <div data-recharts={type}>{props.children}</div>;
    };
  return {
    ResponsiveContainer: record("ResponsiveContainer"),
    BarChart: record("BarChart"),
    Bar: record("Bar"),
    Cell: record("Cell"),
    XAxis: record("XAxis"),
    YAxis: record("YAxis"),
    CartesianGrid: record("CartesianGrid"),
    Tooltip: record("Tooltip"),
  };
});

const propsOf = (type: string) =>
  charts.calls.filter((c) => c.type === type).map((c) => c.props);

const isColorKey = (key: string) =>
  /fill|stroke/i.test(key) && !/dasharray|opacity|width/i.test(key);

/** Every color-like prop given to Recharts, to make sure they are all tokens. */
function colorProps() {
  const colors: string[] = [];
  for (const { props } of charts.calls) {
    for (const [key, value] of Object.entries(props)) {
      if (isColorKey(key) && typeof value === "string") {
        colors.push(value);
      }
      if (typeof value === "object" && value !== null && key !== "children") {
        for (const [inner, innerValue] of Object.entries(value)) {
          if (isColorKey(inner) && typeof innerValue === "string") {
            colors.push(innerValue);
          }
        }
      }
    }
  }
  return colors;
}

/** Renders the right-axis tick of one stage as SVG text. */
function valueTick(stage: string) {
  const right = propsOf("YAxis").find((p) => p.orientation === "right");
  const tick = right?.tick as (props: object) => ReactElement;
  const { container } = render(
    <svg>{tick({ x: 0, y: 0, payload: { value: stage }, index: 0 })}</svg>,
  );
  return within(container);
}

const funnel = funnelRows({
  stages: [
    { stage: "APPLIED", count: 3, conversionToNext: 0.3333 },
    { stage: "INTERVIEW", count: 1, conversionToNext: 3 },
    { stage: "OFFER", count: 3, conversionToNext: 0.6667 },
    { stage: "HIRED", count: 2, conversionToNext: null },
    { stage: "REJECTED", count: 1, conversionToNext: null },
  ],
});

const times = stageTimeRows({
  stages: [
    { stage: "APPLIED", averageHours: 26, samples: 3 },
    { stage: "INTERVIEW", averageHours: 0.01, samples: 1 },
    { stage: "OFFER", averageHours: 33.5, samples: 2 },
    { stage: "HIRED", averageHours: null, samples: 0 },
    { stage: "REJECTED", averageHours: null, samples: 0 },
  ],
});

describe("FunnelChart", () => {
  afterEach(() => {
    charts.calls = [];
  });

  it("passes the five stages to the chart with their stage colors", () => {
    render(<FunnelChart rows={funnel} />);

    const [chart] = propsOf("BarChart");
    expect((chart.data as typeof funnel).map((r) => r.count)).toEqual([
      3, 1, 3, 2, 1,
    ]);
    expect(chart.layout).toBe("vertical");
    expect(propsOf("Bar")[0].dataKey).toBe("count");
    expect(propsOf("Cell").map((c) => c.fill)).toEqual(
      STAGES.map((stage) => `var(--stage-${stage.toLowerCase()})`),
    );
  });

  it("draws REJECTED apart from the sequence", () => {
    render(<FunnelChart rows={funnel} />);

    const cells = propsOf("Cell");
    const rejected = cells[4];
    expect(rejected.fillOpacity).toBeLessThan(1);
    expect(rejected.strokeDasharray).toBeTruthy();
    for (const cell of cells.slice(0, 4)) {
      expect(cell.fillOpacity).toBe(1);
      expect(cell.strokeDasharray).toBeUndefined();
    }
    expect(
      screen.getByText(/Rechazado no es el paso que sigue a Contratado/),
    ).toBeVisible();
  });

  it("uses only theme tokens for every color", () => {
    render(<FunnelChart rows={funnel} />);

    const colors = colorProps();
    expect(colors.length).toBeGreaterThan(0);
    for (const color of colors) {
      expect(color).toMatch(/^var\(--[a-z-]+\)$/);
    }
    expect(propsOf("CartesianGrid")[0].stroke).toBe("var(--border)");
    expect(propsOf("Tooltip")[0].cursor).toMatchObject({
      fill: "var(--muted)",
    });
  });

  it("writes the count and the conversion next to each bar", () => {
    render(<FunnelChart rows={funnel} />);

    expect(valueTick("APPLIED").getByText("→ 33.3 %")).toBeInTheDocument();
    expect(valueTick("HIRED").getByText("→ —")).toBeInTheDocument();
    expect(
      valueTick("REJECTED").getByText("fuera de la secuencia"),
    ).toBeInTheDocument();
  });

  it("has a table with the same data and does not cap 300 %", () => {
    render(<FunnelChart rows={funnel} />);

    const table = screen.getByRole("table", {
      name: "Embudo: postulaciones por etapa y conversión a la siguiente",
    });
    const rows = within(table).getAllByRole("row").slice(1);
    expect(rows.map((row) => row.textContent)).toEqual([
      "Postulado333.3 %",
      "Entrevista1300 %",
      "Oferta366.7 %",
      "Contratado2—",
      "Rechazado1—",
    ]);
    expect(within(rows[0]).getByRole("rowheader")).toHaveTextContent(
      "Postulado",
    );
    expect(
      screen.getByText(
        /conversión mayor a 100 % puede deberse a postulaciones que se saltaron etapas/,
      ),
    ).toBeVisible();
    // The chart itself is hidden from assistive technologies; the table is not.
    expect(
      document.querySelector('[data-recharts="ResponsiveContainer"]')
        ?.parentElement,
    ).toHaveAttribute("aria-hidden", "true");
  });

  it("does not explain skipped stages when no conversion goes over 100 %", () => {
    render(
      <FunnelChart
        rows={funnelRows({
          stages: STAGES.map((stage) => ({
            stage,
            count: 1,
            conversionToNext: 1,
          })),
        })}
      />,
    );

    expect(screen.queryByText(/saltaron etapas/)).toBeNull();
  });
});

describe("StageTimeChart", () => {
  afterEach(() => {
    charts.calls = [];
  });

  it("plots hours and leaves stages without data unplotted, not at 0", () => {
    render(<StageTimeChart rows={times} />);

    const [chart] = propsOf("BarChart");
    expect((chart.data as typeof times).map((r) => r.averageHours)).toEqual([
      26,
      0.01,
      33.5,
      null,
      null,
    ]);
    const [bar] = propsOf("Bar");
    expect(bar.dataKey).toBe("averageHours");
    const minPointSize = bar.minPointSize as (value: number | null) => number;
    expect(minPointSize(0.01)).toBeGreaterThan(0);
    expect(minPointSize(null)).toBe(0);
    expect(propsOf("Cell").map((c) => c.fill)).toEqual(
      STAGES.map((stage) => `var(--stage-${stage.toLowerCase()})`),
    );
    for (const color of colorProps()) {
      expect(color).toMatch(/^var\(--[a-z-]+\)$/);
    }
  });

  it("writes the hours and samples next to each bar and Sin datos without them", () => {
    render(<StageTimeChart rows={times} />);

    const interview = valueTick("INTERVIEW");
    expect(interview.getByText("0.01 h")).toBeInTheDocument();
    expect(interview.getByText("1 muestra")).toBeInTheDocument();
    expect(valueTick("HIRED").getByText("Sin datos")).toBeInTheDocument();
  });

  it("has a table with the same data and explains which stays count", () => {
    render(<StageTimeChart rows={times} />);

    const table = screen.getByRole("table", {
      name: "Tiempo promedio por etapa, en horas, y número de muestras",
    });
    expect(
      within(table)
        .getAllByRole("row")
        .slice(1)
        .map((row) => row.textContent),
    ).toEqual([
      "Postulado26 h3",
      "Entrevista0.01 h1",
      "Oferta33.5 h2",
      "ContratadoSin datos0",
      "RechazadoSin datos0",
    ]);
    expect(
      screen.getByText(/Solo cuentan las estancias terminadas/),
    ).toBeVisible();
  });
});
