import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { TeamPage } from "@/features/team/TeamPage";
export const metadata: Metadata = { title: "Equipe" };
export default function Page() {
  return (
    <Suspense fallback={<LoadingState />}>
      <TeamPage />
    </Suspense>
  );
}
