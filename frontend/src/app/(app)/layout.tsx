import { type ReactNode, Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { SessionGuard } from "@/features/auth/SessionGuard";
import { TermsGate } from "@/features/auth/TermsGate";
import { BillingGate } from "@/features/billing/BillingGate";
import { AppShell } from "@/features/shell/AppShell";

export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <Suspense fallback={<LoadingState />}>
      <SessionGuard>
        <AppShell>{children}</AppShell>
        <TermsGate />
        <BillingGate />
      </SessionGuard>
    </Suspense>
  );
}
