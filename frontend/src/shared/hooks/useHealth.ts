import { useQuery } from "@tanstack/react-query";
import { fetchHealth } from "@/shared/api/health";

export function useHealth() {
  return useQuery({
    queryKey: ["health"],
    queryFn: fetchHealth,
    retry: false,
    staleTime: 30_000,
  });
}
