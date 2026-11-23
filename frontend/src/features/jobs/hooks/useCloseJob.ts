import { useMutation, useQueryClient } from "@tanstack/react-query";
import { updateJobStatus } from "@/features/jobs/api";
import { JOBS_QUERY_KEY } from "@/features/jobs/hooks/useJobs";

export function useCloseJob() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => updateJobStatus(id, "CLOSED"),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: JOBS_QUERY_KEY }),
  });
}
