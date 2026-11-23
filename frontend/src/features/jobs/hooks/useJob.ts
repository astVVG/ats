import { useQuery } from "@tanstack/react-query";
import { getJob } from "@/features/jobs/api";
import { JOBS_QUERY_KEY } from "@/features/jobs/hooks/useJobs";

export function useJob(id: number) {
  return useQuery({
    queryKey: [...JOBS_QUERY_KEY, "detail", id],
    queryFn: () => getJob(id),
  });
}
