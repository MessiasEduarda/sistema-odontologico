import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { InventoryPage } from "@/features/inventory/InventoryPage";

export const metadata: Metadata = { title: "Estoque" };

export default function Page() {
  return (
    <Suspense fallback={<LoadingState />}>
      <InventoryPage />
    </Suspense>
  );
}
