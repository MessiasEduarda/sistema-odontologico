import type { Metadata } from "next";
import { PatientsPage } from "@/features/patients/PatientsPage";

export const metadata: Metadata = { title: "Pacientes" };

export default function Page() {
  return <PatientsPage />;
}
