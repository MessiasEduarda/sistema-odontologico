import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { ReportsPage } from "@/features/reports/ReportsPage";

export const metadata: Metadata = { title: "Relatórios" };

export default function Page() {
  return (
    <Suspense fallback={<LoadingState />}>
      <ReportsPage />
    </Suspense>
  );
}
