import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createJob } from "@/features/jobs/api";
import { JOBS_QUERY_KEY } from "@/features/jobs/hooks/useJobs";

export function useCreateJob() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createJob,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: JOBS_QUERY_KEY }),
  });
}
