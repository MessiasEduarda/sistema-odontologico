import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { ProfilePage } from "@/features/profile/ProfilePage";
export const metadata: Metadata = { title: "Meu perfil" };
export default function Page() {
  return (
    <Suspense fallback={<LoadingState />}>
      <ProfilePage />
    </Suspense>
  );
}
