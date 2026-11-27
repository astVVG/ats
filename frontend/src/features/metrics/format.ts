import { isAxiosError } from "axios";

// Spanish formats with at most 2 decimals and no trailing zeros.
const numberFormat = new Intl.NumberFormat("es-MX", {
  maximumFractionDigits: 2,
});
const percentFormat = new Intl.NumberFormat("es-MX", {
  maximumFractionDigits: 1,
});

export const NO_VALUE = "—";
export const NO_DATA = "Sin datos";

export function formatNumber(value: number): string {
  return numberFormat.format(value);
}

/** A ratio as a percentage, never capped: 0.3333 is "33.3 %", 1.5 is "150 %". */
export function formatPercent(ratio: number | null | undefined): string {
  if (ratio === null || ratio === undefined) {
    return NO_VALUE;
  }
  return `${percentFormat.format(ratio * 100)} %`;
}

export function formatHours(hours: number | null | undefined): string {
  if (hours === null || hours === undefined) {
    return NO_DATA;
  }
  return `${formatNumber(hours)} h`;
}

export function formatDays(days: number): string {
  return `${formatNumber(days)} ${days === 1 ? "día" : "días"}`;
}

export function formatCount(count: number, one: string, many: string) {
  return `${formatNumber(count)} ${count === 1 ? one : many}`;
}

/** Message for a failed metric, in Spanish. */
export function metricsErrorMessage(error: unknown, what: string): string {
  const status = isAxiosError(error) ? error.response?.status : undefined;
  if (status === 404) {
    return "La vacante seleccionada ya no existe.";
  }
  if (status === 400) {
    return "Los filtros no son válidos. Revisa las fechas.";
  }
  return `No se pudo cargar ${what}.`;
}
