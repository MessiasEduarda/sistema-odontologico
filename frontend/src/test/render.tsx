import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, type RenderOptions } from "@testing-library/react";
import type { ReactElement, ReactNode } from "react";
import { ThemeProvider } from "styled-components";
import { ToastProvider } from "@/components/ui/Toast";
import { theme } from "@/styles/theme";

/** Renderiza com os mesmos providers da aplicação (tema + React Query sem retentativas). */
export function renderWithProviders(ui: ReactElement, options?: RenderOptions) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <ToastProvider>{children}</ToastProvider>
      </QueryClientProvider>
    </ThemeProvider>
  );
  return render(ui, { wrapper: Wrapper, ...options });
}

export function jsonResponse(body: unknown, status = 200, contentType = "application/json") {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": contentType } });
}
