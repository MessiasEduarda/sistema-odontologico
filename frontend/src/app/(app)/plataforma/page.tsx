import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { PlatformPage } from "@/features/platform/PlatformPage";
export const metadata: Metadata = { title: "Plataforma" };
export default function Page() {
  return (
    <Suspense fallback={<LoadingState />}>
      <PlatformPage />
    </Suspense>
  );
}
