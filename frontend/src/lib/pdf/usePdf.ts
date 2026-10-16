"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useFeedback } from "@/components/ui/Feedback";
import { api } from "@/lib/api/endpoints";
import type { AestheticRecord, Anamnesis, ClinicalDocument, Consent, Plan, ReceiptData } from "@/lib/api/types";

export type PdfRequest =
  | { kind: "anamnesis"; patientId: string; data: Anamnesis }
  | { kind: "consent"; patientId: string; data: Consent }
  | { kind: "document"; patientId: string; data: ClinicalDocument }
  | { kind: "aesthetic"; patientId: string; data: AestheticRecord }
  | { kind: "plan"; patientId: string; data: Plan }
  | { kind: "receipt"; patientId: string; data: ReceiptData };

const PREFIX: Record<PdfRequest["kind"], string> = {
  anamnesis: "anamnese",
  consent: "termo",
  document: "documento",
  aesthetic: "harmonizacao",
  plan: "orcamento",
  receipt: "recibo",
};

const DOCUMENT_PREFIX: Record<string, string> = {
  PRESCRIPTION: "receituario",
  CERTIFICATE: "atestado",
  REFERRAL: "encaminhamento",
  INSTRUCTIONS: "orientacoes",
};

function slug(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "")
    .slice(0, 60);
}

/**
 * Gera o PDF no navegador e baixa direto (sem janela de impressão). A biblioteca de PDF só é carregada no primeiro
 * clique. Os dados da clínica e do paciente vêm do cache do React Query (ou da API).
 */
export function usePdf() {
  const queryClient = useQueryClient();
  const feedback = useFeedback();
  const [busy, setBusy] = useState<string | null>(null);

  async function download(req: PdfRequest, key: string = req.data.id) {
    setBusy(key);
    try {
      const [{ pdf }, docs, clinic, patient] = await Promise.all([
        import("@react-pdf/renderer"),
        import("./documents"),
        queryClient.fetchQuery({ queryKey: ["clinic-settings"], queryFn: api.clinic.settings, staleTime: 60_000 }),
        queryClient.fetchQuery({
          queryKey: ["patient", req.patientId],
          queryFn: () => api.patients.get(req.patientId),
          staleTime: 60_000,
        }),
      ]);
      const logo = null;
      const ctx = { clinic, patient, logo };
      const element =
        req.kind === "anamnesis"
          ? docs.AnamnesisPdf({ ctx, anamnesis: req.data })
          : req.kind === "consent"
            ? docs.ConsentPdf({ ctx, consent: req.data })
            : req.kind === "document"
              ? docs.ClinicalDocumentPdf({ ctx, doc: req.data })
              : req.kind === "aesthetic"
                ? docs.AestheticPdf({ ctx, record: req.data })
                : req.kind === "plan"
                  ? docs.PlanPdf({ ctx, plan: req.data })
                  : docs.ReceiptPdf({ ctx, receipt: req.data });
      const blob = await pdf(element).toBlob();
      const date = new Date().toISOString().slice(0, 10);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      const prefix = req.kind === "document" ? (DOCUMENT_PREFIX[req.data.type] ?? "documento") : PREFIX[req.kind];
      a.download = `${prefix}-${slug(patient.fullName)}-${date}.pdf`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 10_000);
    } catch (error) {
      feedback.error("Não foi possível gerar o PDF", error instanceof Error ? error.message : undefined);
    } finally {
      setBusy(null);
    }
  }

  return { download, busy };
}
