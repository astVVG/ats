import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { listHistory } from "@/features/board/api";

/** Every page of an application's stage history. */
export function applicationHistoryKey(applicationId: number) {
  return ["applications", applicationId, "history"] as const;
}

export function historyQueryKey(applicationId: number, page: number) {
  return [...applicationHistoryKey(applicationId), page] as const;
}

export function useHistory(applicationId: number, page: number) {
  return useQuery({
    queryKey: historyQueryKey(applicationId, page),
    queryFn: () => listHistory(applicationId, page),
    placeholderData: keepPreviousData,
    // Opening the panel always asks the server: moves happen on the board.
    refetchOnMount: "always",
  });
}
