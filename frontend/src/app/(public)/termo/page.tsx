import type { Metadata } from "next";
import { ConsentLinkPage } from "@/features/public/ConsentLinkPage";

export const metadata: Metadata = {
  title: "Termo de consentimento",
  referrer: "no-referrer",
  robots: { index: false },
};

export default function Page() {
  return <ConsentLinkPage />;
}
