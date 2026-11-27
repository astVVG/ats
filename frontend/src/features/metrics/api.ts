import { apiClient } from "@/shared/api/client";
import type {
  Funnel,
  MetricsFilters,
  TimeInStage,
  TimeToHire,
} from "@/features/metrics/types";

// Callers pass only the filters that are set, so no empty parameter is sent.

export async function getFunnel(filters: MetricsFilters): Promise<Funnel> {
  const { data } = await apiClient.get<Funnel>("/metrics/funnel", {
    params: filters,
  });
  return data;
}

export async function getTimeInStage(
  filters: MetricsFilters,
): Promise<TimeInStage> {
  const { data } = await apiClient.get<TimeInStage>("/metrics/time-in-stage", {
    params: filters,
  });
  return data;
}

export async function getTimeToHire(
  filters: MetricsFilters,
): Promise<TimeToHire> {
  const { data } = await apiClient.get<TimeToHire>("/metrics/time-to-hire", {
    params: filters,
  });
  return data;
}
