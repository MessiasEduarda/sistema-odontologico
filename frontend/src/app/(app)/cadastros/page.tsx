import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { RegistryPage } from "@/features/registry/RegistryPage";
export const metadata: Metadata = { title: "Cadastros" };
export default function Page() {
  return (
    <Suspense fallback={<LoadingState />}>
      <RegistryPage />
    </Suspense>
  );
}
