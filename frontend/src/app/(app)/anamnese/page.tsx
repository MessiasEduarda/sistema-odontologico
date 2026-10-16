import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { AnamnesisPage } from "@/features/anamnesis/AnamnesisPage";
export const metadata: Metadata = { title: "Anamnese" };
export default function Page() {
  return (
    <Suspense fallback={<LoadingState />}>
      <AnamnesisPage />
    </Suspense>
  );
}
