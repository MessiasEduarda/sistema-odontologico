import { type ReactNode, Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { SessionGuard } from "@/features/auth/SessionGuard";
import { TermsGate } from "@/features/auth/TermsGate";
import { AppShell } from "@/features/shell/AppShell";

/**
 * Área autenticada com menu lateral. O Suspense é necessário porque a guarda de sessão e o menu leem a rota
 * atual (`usePathname`), que nas rotas dinâmicas só existe em tempo de execução.
 */
export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <Suspense fallback={<LoadingState />}>
      <SessionGuard>
        <AppShell>{children}</AppShell>
        <TermsGate />
      </SessionGuard>
    </Suspense>
  );
}
