import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { listJobs } from "@/features/jobs/api";
import type { JobListParams } from "@/features/jobs/types";

export const JOBS_QUERY_KEY = ["jobs"] as const;

export function useJobs(params: JobListParams) {
  return useQuery({
    queryKey: [...JOBS_QUERY_KEY, "list", params],
    queryFn: () => listJobs(params),
    placeholderData: keepPreviousData,
  });
}
