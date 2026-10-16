"use client";

import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "next/navigation";
import { PageHeader, Stack } from "@/components/ui/display";
import { LoadingState } from "@/components/ui/LoadingState";
import { api } from "@/lib/api/endpoints";
import { PlanEditor } from "./PlanEditor";

export function NewPlanPage() {
  const patientId = useSearchParams().get("paciente");
  const patient = useQuery({
    queryKey: ["patient", patientId],
    queryFn: () => api.patients.get(patientId ?? ""),
    enabled: Boolean(patientId),
  });
  if (patientId && patient.isPending) return <LoadingState />;
  return (
    <Stack>
      <PageHeader title="Novo orçamento" />
      <PlanEditor initialPatient={patient.data ? { id: patient.data.id, name: patient.data.fullName } : null} />
    </Stack>
  );
}
