import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/LoadingState";
import { PatientPage } from "@/features/patients/PatientPage";

export const metadata: Metadata = { title: "Paciente" };

export default function Page({ params }: PageProps<"/pacientes/[id]">) {
  return (
    <Suspense fallback={<LoadingState />}>
      <PatientRoute params={params} />
    </Suspense>
  );
}

async function PatientRoute({ params }: { params: PageProps<"/pacientes/[id]">["params"] }) {
  const { id } = await params;
  return <PatientPage patientId={id} />;
}
