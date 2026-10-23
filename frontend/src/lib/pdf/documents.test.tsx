import { writeFileSync } from "node:fs";
import { join } from "node:path";
import { renderToBuffer } from "@react-pdf/renderer";
import type { DocumentProps } from "@react-pdf/renderer";
import type { ReactElement } from "react";
import { describe, expect, it } from "vitest";
import type { ClinicSettings, Patient } from "@/lib/api/types";
import {
  AestheticPdf,
  AnamnesisPdf,
  ClinicalDocumentPdf,
  ConsentPdf,
  PlanPdf,
  ReceiptPdf,
  type PdfContext,
} from "./documents";

const clinic = {
  name: "Clínica Exemplo Dois",
  legalName: "Exemplo Dois Odontologia Ltda (fictícia)",
  document: "00.000.000/0001-00",
  phone: "1133330000",
  email: "contato@exemplo2.example",
  zipCode: "01000-000",
  address: "Rua Fictícia, 100",
  city: "São Paulo",
  state: "SP",
  timeZone: "America/Sao_Paulo",
} as ClinicSettings;

const patient = {
  id: "p1",
  fullName: "Marina Teste Fictícia",
  socialName: null,
  birthDate: "1992-03-08",
  cpf: "52998224725",
  mobile: "11900000099",
  phone: null,
  guardianName: null,
} as Patient;

const LOGO =
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";
const ctx: PdfContext = { clinic, patient, logo: LOGO };
const now = "2026-10-15T17:00:00Z";

const cases: [string, ReactElement<DocumentProps>][] = [
  [
    "anamnese",
    AnamnesisPdf({
      ctx,
      anamnesis: {
        id: "a1",
        formId: "f1",
        formName: "Anamnese de harmonização orofacial",
        formKind: "AESTHETIC",
        questions: [
          {
            id: "allergies",
            section: "Saúde geral",
            text: "Tem alergia a algum medicamento?",
            type: "YES_NO_TEXT",
            alertOnYes: true,
          },
          { id: "smoking", section: "Hábitos", text: "Fuma?", type: "YES_NO", alertOnYes: false },
          {
            id: "goal",
            section: "Harmonização orofacial",
            text: "O que deseja melhorar?",
            type: "TEXT",
            alertOnYes: false,
          },
        ],
        answers: {
          allergies: { answer: "YES", detail: "Lidocaína (teste)" },
          smoking: { answer: "NO" },
          goal: { detail: "Linhas da testa" },
        },
        alerts: [{ questionId: "allergies", text: "Tem alergia a algum medicamento?", detail: "Lidocaína (teste)" }],
        notes: "Observação de teste.",
        filledBy: "PROFESSIONAL",
        createdByName: "Dra. Lívia Exemplo",
        createdAt: now,
      },
    }),
  ],
  [
    "termo",
    ConsentPdf({
      ctx,
      consent: {
        id: "c1",
        title: "Consentimento para procedimento de harmonização orofacial",
        category: "AESTHETIC",
        body: "Eu, Marina Teste Fictícia, declaro que fui informada sobre o procedimento.\n\n1. Recebi explicações.\n2. Entendo que o resultado varia.",
        bodySha256: "67adaa247be06ba7850878eca11d7ddfa326dd9dc471cc6a2fc1a5163100ce78",
        templateVersion: 1,
        procedureDescription: "Toxina botulínica",
        professionalName: "Dra. Lívia Exemplo",
        status: "SIGNED",
        signedAt: now,
        signerName: "Marina Teste Fictícia",
        signerDocument: null,
        signatureImage:
          "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
        signedIp: "127.0.0.1",
        revokedAt: null,
        revokeReason: null,
        createdAt: now,
      },
    }),
  ],
  [
    "documento",
    ClinicalDocumentPdf({
      ctx,
      doc: {
        id: "d1",
        professionalId: "pr1",
        professionalName: "Dra. Lívia Exemplo",
        professionalCro: "CRO-SP 99999",
        type: "INSTRUCTIONS",
        title: "Cuidados após aplicação (texto de teste)",
        content: "TEXTO DE TESTE — a clínica escreve aqui as orientações definidas pelo responsável técnico.",
        issuedAt: now,
        sharedWithPatient: false,
        updatedAt: null,
        voidedAt: null,
        voidReason: null,
      },
    }),
  ],
  [
    "harmonizacao",
    AestheticPdf({
      ctx,
      record: {
        id: "h1",
        patientId: "p1",
        patientName: patient.fullName,
        professionalId: "pr1",
        professionalName: "Dra. Lívia Exemplo",
        performedAt: now,
        procedureType: "TOXIN",
        productName: "Toxina Fictícia 100U",
        manufacturer: "Fabricante Exemplo",
        anvisaRegistry: "0000000000",
        batch: "TESTE-2026-01",
        expiryDate: "2027-12-31",
        unit: "U",
        regions: [
          { region: "Glabela", quantity: 20 },
          { region: "Fronte (testa)", quantity: 10, notes: "Dose de teste" },
        ],
        totalQuantity: 30,
        technique: "Texto de teste.",
        notes: null,
        returnDate: "2026-10-30",
        consentId: "c1",
        consentStatus: "SIGNED",
        createdByName: "Diego Amostra",
        voidedAt: null,
        voidReason: null,
      },
    }),
  ],
  [
    "orcamento",
    PlanPdf({
      ctx,
      plan: {
        id: "t1",
        patientId: "p1",
        patientName: patient.fullName,
        professionalId: "pr1",
        professionalName: "Dra. Lívia Exemplo",
        title: "Tratamento restaurador",
        status: "PROPOSED",
        subtotal: 680,
        itemsDiscount: 0,
        discount: 30,
        total: 650,
        paymentConditions: "Até 3x sem juros",
        notes: null,
        validUntil: "2026-11-15",
        approvedAt: null,
        approvedByName: null,
        createdAt: now,
        version: 0,
        receivableId: null,
        items: [
          {
            id: "i1",
            procedureId: null,
            description: "Restauração em resina — dente 36",
            tooth: 36,
            surfaces: null,
            quantity: 1,
            unitPrice: 250,
            discount: 0,
            total: 250,
            status: "PLANNED",
            doneAt: null,
          },
          {
            id: "i2",
            procedureId: null,
            description: "Limpeza (profilaxia)",
            tooth: null,
            surfaces: null,
            quantity: 1,
            unitPrice: 180,
            discount: 0,
            total: 180,
            status: "PLANNED",
            doneAt: null,
          },
          {
            id: "i3",
            procedureId: null,
            description: "Restauração em resina — dente 46",
            tooth: 46,
            surfaces: null,
            quantity: 1,
            unitPrice: 250,
            discount: 0,
            total: 250,
            status: "PLANNED",
            doneAt: null,
          },
        ],
      },
    }),
  ],
  [
    "recibo",
    ReceiptPdf({
      ctx,
      receipt: {
        id: "3f2a9c10-0000-0000-0000-000000000000",
        amount: 450.5,
        refundedAmount: 0,
        method: "PIX",
        paidAt: now,
        notes: null,
        createdByName: "Recepção Fictícia",
        description: "Orçamento: Plano de tratamento",
        installment: "1/3",
      },
    }),
  ],
];

describe("PDFs do paciente", () => {
  it.each(cases)("gera o PDF de %s", async (name, element) => {
    const buffer = await renderToBuffer(element);
    expect(buffer.subarray(0, 5).toString()).toBe("%PDF-");
    expect(buffer.length).toBeGreaterThan(1500);
    if (process.env.PDF_OUT) writeFileSync(join(process.env.PDF_OUT, `${name}.pdf`), buffer);
  });
});
