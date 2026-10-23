"use client";

import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { ThemeProvider } from "styled-components";
import { FeedbackProvider } from "@/components/ui/Feedback";
import { ToastProvider } from "@/components/ui/Toast";
import { ApiError } from "@/lib/api/client";
import { isSessionExpired, redirectToLogin } from "@/lib/session-expiry";
import { StyledComponentsRegistry } from "@/lib/styled-registry";
import { GlobalStyle } from "@/styles/GlobalStyle";
import { theme } from "@/styles/theme";

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(() => {
    const onUnauthenticated = (error: unknown) => {
      if (isSessionExpired(error)) redirectToLogin(() => client.clear());
    };
    const client: QueryClient = new QueryClient({
      queryCache: new QueryCache({ onError: onUnauthenticated }),
      mutationCache: new MutationCache({ onError: onUnauthenticated }),
      defaultOptions: {
        queries: {
          staleTime: 30_000,
          refetchOnWindowFocus: false,
          retry: (failures, error) => !(error instanceof ApiError && error.status < 500) && failures < 3,
        },
      },
    });
    return client;
  });

  return (
    <StyledComponentsRegistry>
      <ThemeProvider theme={theme}>
        <GlobalStyle />
        <QueryClientProvider client={queryClient}>
          <ToastProvider>
            <FeedbackProvider>{children}</FeedbackProvider>
          </ToastProvider>
        </QueryClientProvider>
      </ThemeProvider>
    </StyledComponentsRegistry>
  );
}
