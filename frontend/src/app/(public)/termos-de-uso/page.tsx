import type { Metadata } from "next";
import { LegalPage } from "@/features/public/LegalPage";

export const metadata: Metadata = { title: "Termos de uso" };

export default function Page() {
  return <LegalPage kind="terms" />;
}
