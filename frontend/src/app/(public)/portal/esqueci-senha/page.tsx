import type { Metadata } from "next";
import { PortalForgotForm } from "@/features/public/PortalForgotForm";

export const metadata: Metadata = { title: "Esqueci minha senha", referrer: "no-referrer", robots: { index: false } };

export default function Page() {
  return <PortalForgotForm />;
}
