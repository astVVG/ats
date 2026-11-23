import { useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";
import { endSession } from "@/features/auth/session";

/** Returns a function that ends the session; route guards then redirect to /login. */
export function useLogout() {
  const queryClient = useQueryClient();
  return useCallback(() => endSession(queryClient), [queryClient]);
}
