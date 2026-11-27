import { useQuery } from "@tanstack/react-query";
import {
  getFunnel,
  getTimeInStage,
  getTimeToHire,
} from "@/features/metrics/api";
import type { MetricsFilters } from "@/features/metrics/types";

export const METRICS_QUERY_KEY = ["metrics"] as const;

type Fetcher<T> = (filters: MetricsFilters) => Promise<T>;

/**
 * One metric for the given filters; null filters (inverted range) send nothing.
 * Each filter set has its own key and no placeholder, so a change never shows
 * the numbers of the previous filters. Entering the page always refetches:
 * board moves change the metrics.
 */
function useMetric<T>(
  name: string,
  fetcher: Fetcher<T>,
  filters: MetricsFilters | null,
) {
  return useQuery({
    queryKey: [...METRICS_QUERY_KEY, name, filters],
    queryFn: () => fetcher(filters ?? {}),
    enabled: filters !== null,
    refetchOnMount: "always",
  });
}

export function useFunnel(filters: MetricsFilters | null) {
  return useMetric("funnel", getFunnel, filters);
}

export function useTimeInStage(filters: MetricsFilters | null) {
  return useMetric("time-in-stage", getTimeInStage, filters);
}

export function useTimeToHire(filters: MetricsFilters | null) {
  return useMetric("time-to-hire", getTimeToHire, filters);
}
