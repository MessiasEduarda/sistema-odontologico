import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { AestheticsPage } from "@/features/aesthetics/AestheticsPage";
export const metadata: Metadata = { title: "Harmonização orofacial" };
export default function Page() {
  return (
    <Suspense fallback={<LoadingState />}>
      <AestheticsPage />
    </Suspense>
  );
}
