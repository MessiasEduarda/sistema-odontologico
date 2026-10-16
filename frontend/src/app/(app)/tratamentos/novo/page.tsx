import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { NewPlanPage } from "@/features/treatments/NewPlanPage";

export const metadata: Metadata = { title: "Novo orçamento" };

export default function Page() {
  return (
    <Suspense fallback={<LoadingState />}>
      <NewPlanPage />
    </Suspense>
  );
}
