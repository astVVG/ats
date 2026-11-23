import { useMutation, useQueryClient } from "@tanstack/react-query";
import { updateJob } from "@/features/jobs/api";
import { JOBS_QUERY_KEY } from "@/features/jobs/hooks/useJobs";
import type { JobRequest } from "@/features/jobs/types";

export function useUpdateJob(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: JobRequest) => updateJob(id, request),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: JOBS_QUERY_KEY }),
  });
}
