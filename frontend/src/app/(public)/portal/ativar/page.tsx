import type { Metadata } from "next";
import { PortalActivationForm } from "@/features/public/PortalActivationForm";

export const metadata: Metadata = { title: "Criar senha do portal", referrer: "no-referrer", robots: { index: false } };

export default function Page() {
  return <PortalActivationForm />;
}
