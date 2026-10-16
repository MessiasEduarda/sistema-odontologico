import type { Metadata } from "next";
import { PlansPage } from "@/features/treatments/PlansPage";

export const metadata: Metadata = { title: "Tratamentos" };

export default function Page() {
  return <PlansPage />;
}
