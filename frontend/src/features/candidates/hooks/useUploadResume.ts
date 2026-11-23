import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { uploadResume } from "@/features/candidates/api";
import { candidateQueryKey } from "@/features/candidates/hooks/useCandidate";
import { CANDIDATES_QUERY_KEY } from "@/features/candidates/hooks/useCandidates";

/** `progress` is the percentage sent while an upload runs, otherwise null. */
export function useUploadResume(id: number) {
  const queryClient = useQueryClient();
  const [progress, setProgress] = useState<number | null>(null);

  const mutation = useMutation({
    mutationFn: (file: File) => {
      setProgress(0);
      return uploadResume(id, file, setProgress);
    },
    onSuccess: (candidate) => {
      queryClient.setQueryData(candidateQueryKey(id), candidate);
      return queryClient.invalidateQueries({ queryKey: CANDIDATES_QUERY_KEY });
    },
    onSettled: () => setProgress(null),
  });

  return { ...mutation, progress };
}
