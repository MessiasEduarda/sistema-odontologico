"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { ThemeProvider } from "styled-components";
import { FeedbackProvider } from "@/components/ui/Feedback";
import { ToastProvider } from "@/components/ui/Toast";
import { ApiError } from "@/lib/api/client";
import { StyledComponentsRegistry } from "@/lib/styled-registry";
import { GlobalStyle } from "@/styles/GlobalStyle";
import { theme } from "@/styles/theme";

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            refetchOnWindowFocus: false,
            // Recusa do servidor (4xx: sem permissão, não encontrado, acesso suspenso) não melhora repetindo.
            retry: (failures, error) => !(error instanceof ApiError && error.status < 500) && failures < 3,
          },
        },
      }),
  );

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
