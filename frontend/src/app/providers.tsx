"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { ThemeProvider } from "styled-components";
import { ToastProvider } from "@/components/ui/Toast";
import { StyledComponentsRegistry } from "@/lib/styled-registry";
import { GlobalStyle } from "@/styles/GlobalStyle";
import { theme } from "@/styles/theme";

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { staleTime: 30_000, refetchOnWindowFocus: false },
        },
      }),
  );

  return (
    <StyledComponentsRegistry>
      <ThemeProvider theme={theme}>
        <GlobalStyle />
        <QueryClientProvider client={queryClient}>
          <ToastProvider>{children}</ToastProvider>
        </QueryClientProvider>
      </ThemeProvider>
    </StyledComponentsRegistry>
  );
}
