import type { Metadata } from "next";
import { PortalResetForm } from "@/features/public/PortalResetForm";

export const metadata: Metadata = { title: "Criar nova senha", referrer: "no-referrer", robots: { index: false } };

export default function Page() {
  return <PortalResetForm />;
}
