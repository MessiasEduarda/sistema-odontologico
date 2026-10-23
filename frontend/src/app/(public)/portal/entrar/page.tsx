import type { Metadata } from "next";
import { PortalLoginForm } from "@/features/public/PortalLoginForm";

export const metadata: Metadata = { title: "Portal do paciente", referrer: "no-referrer", robots: { index: false } };

export default function Page() {
  return <PortalLoginForm />;
}
