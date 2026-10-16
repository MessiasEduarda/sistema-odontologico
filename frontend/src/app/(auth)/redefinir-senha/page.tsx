import type { Metadata } from "next";
import { ResetPasswordForm } from "@/features/auth/ResetPasswordForm";

export const metadata: Metadata = { title: "Criar nova senha", referrer: "no-referrer" };

export default function ResetPasswordPage() {
  return <ResetPasswordForm />;
}
