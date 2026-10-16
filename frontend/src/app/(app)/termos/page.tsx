import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { ConsentsPage } from "@/features/consents/ConsentsPage";
export const metadata: Metadata = { title: "Termos de consentimento" };
export default function Page() {
  return (
    <Suspense fallback={<LoadingState />}>
      <ConsentsPage />
    </Suspense>
  );
}
