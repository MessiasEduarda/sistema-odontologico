import type { Metadata } from "next";
import { AppointmentLinkPage } from "@/features/public/AppointmentLinkPage";

export const metadata: Metadata = { title: "Sua consulta", referrer: "no-referrer", robots: { index: false } };

export default function Page() {
  return <AppointmentLinkPage />;
}
