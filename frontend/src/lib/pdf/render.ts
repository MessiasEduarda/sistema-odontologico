import type { AestheticRecord, Anamnesis, ClinicalDocument, Consent, Plan, ReceiptData } from "@/lib/api/types";
import { downloadBlob, fileDate, fileSlug } from "@/lib/download";
import type { PdfContext } from "./documents";

export type PdfDocument =
  | { kind: "anamnesis"; data: Anamnesis }
  | { kind: "consent"; data: Consent }
  | { kind: "document"; data: ClinicalDocument }
  | { kind: "aesthetic"; data: AestheticRecord }
  | { kind: "plan"; data: Plan }
  | { kind: "receipt"; data: ReceiptData };

const PREFIX: Record<PdfDocument["kind"], string> = {
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

export async function downloadPdf(doc: PdfDocument, ctx: PdfContext) {
  const [{ pdf }, docs] = await Promise.all([import("@react-pdf/renderer"), import("./documents")]);
  const element =
    doc.kind === "anamnesis"
      ? docs.AnamnesisPdf({ ctx, anamnesis: doc.data })
      : doc.kind === "consent"
        ? docs.ConsentPdf({ ctx, consent: doc.data })
        : doc.kind === "document"
          ? docs.ClinicalDocumentPdf({ ctx, doc: doc.data })
          : doc.kind === "aesthetic"
            ? docs.AestheticPdf({ ctx, record: doc.data })
            : doc.kind === "plan"
              ? docs.PlanPdf({ ctx, plan: doc.data })
              : docs.ReceiptPdf({ ctx, receipt: doc.data });
  const prefix = doc.kind === "document" ? (DOCUMENT_PREFIX[doc.data.type] ?? "documento") : PREFIX[doc.kind];
  downloadBlob(await pdf(element).toBlob(), `${prefix}-${fileSlug(ctx.patient.fullName)}-${fileDate()}.pdf`);
}
