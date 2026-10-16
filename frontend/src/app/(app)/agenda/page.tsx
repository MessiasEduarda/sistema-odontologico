import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { AgendaPage } from "@/features/agenda/AgendaPage";

export const metadata: Metadata = { title: "Agenda" };

export default function Page() {
  return (
    <Suspense fallback={<LoadingState />}>
      <AgendaPage />
    </Suspense>
  );
}
