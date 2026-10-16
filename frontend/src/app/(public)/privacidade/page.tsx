import type { Metadata } from "next";
import { LegalPage } from "@/features/public/LegalPage";

export const metadata: Metadata = { title: "Política de privacidade" };

export default function Page() {
  return <LegalPage kind="privacy" />;
}
