import type { Metadata } from "next";
import { AcceptInvitationForm } from "@/features/auth/AcceptInvitationForm";

export const metadata: Metadata = { title: "Aceitar convite", referrer: "no-referrer" };

export default function InvitationPage() {
  return <AcceptInvitationForm />;
}
