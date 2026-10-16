import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { CarePage } from "@/features/care/CarePage";
export const metadata: Metadata = { title: "Orientações e cuidados" };
export default function Page() {
  return (
    <Suspense fallback={<LoadingState />}>
      <CarePage />
    </Suspense>
  );
}
