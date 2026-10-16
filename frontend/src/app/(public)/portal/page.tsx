import type { Metadata } from "next";
import { PortalPage } from "@/features/public/PortalPage";

export const metadata: Metadata = { title: "Portal do paciente", referrer: "no-referrer", robots: { index: false } };

export default function Page() {
  return <PortalPage />;
}
