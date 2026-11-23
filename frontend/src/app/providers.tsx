import { QueryClientProvider, type QueryClient } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { createQueryClient } from "@/app/queryClient";
import { connectApiClientToSession } from "@/features/auth/session";
import { Toaster } from "@/shared/ui/sonner";

type ProvidersProps = {
  children: ReactNode;
  queryClient?: QueryClient;
};

export function Providers({ children, queryClient }: ProvidersProps) {
  const [client] = useState(() => {
    const instance = queryClient ?? createQueryClient();
    // Registered before the first render so the first requests carry the token.
    connectApiClientToSession(instance);
    return instance;
  });
  return (
    <QueryClientProvider client={client}>
      {children}
      <Toaster richColors position="top-right" />
    </QueryClientProvider>
  );
}
