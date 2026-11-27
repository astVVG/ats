import type { components, operations } from "@/shared/api/schema";

export type Funnel = components["schemas"]["FunnelResponse"];
export type FunnelStage = components["schemas"]["FunnelStageResponse"];
export type TimeInStage = components["schemas"]["TimeInStageResponse"];
export type StageTime = components["schemas"]["StageTimeResponse"];
export type TimeToHire = components["schemas"]["TimeToHireResponse"];

/** Query parameters shared by the three metrics endpoints. */
export type MetricsFilters = NonNullable<
  operations["funnel"]["parameters"]["query"]
>;

/** What the filter form holds; empty strings mean "no filter". */
export type MetricsFilterValues = {
  /** A job id, or ALL_JOBS. */
  job: string;
  /** yyyy-MM-dd from the date input, used as is. */
  from: string;
  to: string;
};

export const ALL_JOBS = "all";

export const EMPTY_FILTERS: MetricsFilterValues = {
  job: ALL_JOBS,
  from: "",
  to: "",
};

/** Same cap the jobs endpoint enforces per page. */
export const MAX_JOB_OPTIONS = 100;

/** yyyy-MM-dd strings compare in date order, so no Date conversion is needed. */
export function isRangeInverted({ from, to }: MetricsFilterValues): boolean {
  return from !== "" && to !== "" && from > to;
}

/** Only the filters that are set; null while the date range is inverted. */
export function toMetricsFilters(
  values: MetricsFilterValues,
): MetricsFilters | null {
  if (isRangeInverted(values)) {
    return null;
  }
  const filters: MetricsFilters = {};
  if (values.job !== ALL_JOBS) {
    filters.jobId = Number(values.job);
  }
  if (values.from !== "") {
    filters.from = values.from;
  }
  if (values.to !== "") {
    filters.to = values.to;
  }
  return filters;
}
