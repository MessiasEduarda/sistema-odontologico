import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { PlanDetail } from "@/features/treatments/PlanDetail";

export const metadata: Metadata = { title: "Orçamento" };

export default function Page({ params }: PageProps<"/tratamentos/[id]">) {
  return (
    <Suspense fallback={<LoadingState />}>
      <PlanRoute params={params} />
    </Suspense>
  );
}

async function PlanRoute({ params }: { params: PageProps<"/tratamentos/[id]">["params"] }) {
  const { id } = await params;
  return <PlanDetail planId={id} />;
}
