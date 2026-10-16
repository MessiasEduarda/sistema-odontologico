import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { SettingsPage } from "@/features/settings/SettingsPage";
export const metadata: Metadata = { title: "Configurações" };
export default function Page() {
  return (
    <Suspense fallback={<LoadingState />}>
      <SettingsPage />
    </Suspense>
  );
}
