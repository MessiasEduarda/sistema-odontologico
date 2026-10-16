import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { CommunicationPage } from "@/features/communication/CommunicationPage";

export const metadata: Metadata = { title: "Comunicação" };

export default function Page() {
  return (
    <Suspense fallback={<LoadingState />}>
      <CommunicationPage />
    </Suspense>
  );
}
