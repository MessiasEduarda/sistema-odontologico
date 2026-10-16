import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { FinancePage } from "@/features/finance/FinancePage";

export const metadata: Metadata = { title: "Financeiro" };

export default function Page() {
  return (
    <Suspense fallback={<LoadingState />}>
      <FinancePage />
    </Suspense>
  );
}
