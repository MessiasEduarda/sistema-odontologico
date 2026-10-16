import type { Metadata } from "next";
import { ClinicPicker } from "@/features/auth/ClinicPicker";

export const metadata: Metadata = { title: "Escolha a clínica" };

export default function SelectClinicPage() {
  return <ClinicPicker />;
}
