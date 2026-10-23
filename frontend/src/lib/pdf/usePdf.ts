"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useFeedback } from "@/components/ui/Feedback";
import { api } from "@/lib/api/endpoints";
import { downloadPdf, type PdfDocument } from "./render";

export type PdfRequest = PdfDocument & { patientId: string };

export function usePdf() {
  const queryClient = useQueryClient();
  const feedback = useFeedback();
  const [busy, setBusy] = useState<string | null>(null);

  async function download(req: PdfRequest, key: string = req.data.id) {
    setBusy(key);
    try {
      const [clinic, patient] = await Promise.all([
        queryClient.fetchQuery({ queryKey: ["clinic-settings"], queryFn: api.clinic.settings, staleTime: 60_000 }),
        queryClient.fetchQuery({
          queryKey: ["patient", req.patientId],
          queryFn: () => api.patients.get(req.patientId),
          staleTime: 60_000,
        }),
      ]);
      await downloadPdf(req, { clinic, patient, logo: null });
    } catch (error) {
      feedback.error("Não foi possível gerar o PDF", error instanceof Error ? error.message : undefined);
    } finally {
      setBusy(null);
    }
  }

  return { download, busy };
}
