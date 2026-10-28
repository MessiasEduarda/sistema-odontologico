import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { SupportPage } from "@/features/support/SupportPage";

export const metadata: Metadata = { title: "Suporte" };

export default function Page() {
  return (
    <Suspense fallback={<LoadingState />}>
      <SupportPage />
    </Suspense>
  );
}
