// "PdfImage": o Image do react-pdf não tem atributo alt (o PDF não é uma página web).
import { Document, Image as PdfImage, Page, StyleSheet, Text, View } from "@react-pdf/renderer";
import type { ReactNode } from "react";
import type {
  ReceiptData,
  AestheticRecord,
  Anamnesis,
  ClinicalDocument,
  ClinicSettings,
  Consent,
  Patient,
  Plan,
  Question,
} from "@/lib/api/types";
import { ageFrom, formatCpf, formatDate, formatDateTime, formatMoney, formatPhone, moneyInWords } from "@/lib/format";
import { AESTHETIC_TYPES, CONSENT_CATEGORIES, DOCUMENT_TYPES, methodLabel, PLAN_STATUS } from "@/lib/labels";

/**
 * Modelos de PDF (gerados no navegador com @react-pdf/renderer). Texto real e selecionável, A4, cabeçalho com os
 * dados da clínica, bloco do paciente, rodapé com data de geração e paginação. Fonte Helvetica (padrão do PDF), que
 * cobre a acentuação do português.
 */

const A4_HEIGHT = 841.89;

const COLORS = {
  brand: "#7A5C41",
  line: "#D6CEC5",
  muted: "#6B655F",
  text: "#1E1C1A",
  danger: "#B3261E",
  soft: "#F4EDE6",
};

const s = StyleSheet.create({
  page: {
    paddingTop: 36,
    paddingBottom: 56,
    paddingHorizontal: 42,
    fontFamily: "Helvetica",
    fontSize: 10,
    color: COLORS.text,
    lineHeight: 1.45,
  },
  logoBox: { width: 46, height: 46, borderRadius: 12, backgroundColor: "#141414", overflow: "hidden", marginRight: 12 },
  logo: { width: 46, height: 46, objectFit: "cover" },
  header: {
    flexDirection: "row",
    alignItems: "center",
    borderBottomWidth: 1.5,
    borderBottomColor: COLORS.brand,
    paddingBottom: 8,
    marginBottom: 14,
  },
  clinic: { fontSize: 15, fontFamily: "Helvetica-Bold", color: COLORS.brand },
  clinicInfo: { fontSize: 8.5, color: COLORS.muted, marginTop: 4 },
  title: {
    fontSize: 13,
    fontFamily: "Helvetica-Bold",
    textAlign: "center",
    textTransform: "uppercase",
    marginBottom: 4,
  },
  subtitle: { fontSize: 9, color: COLORS.muted, textAlign: "center", marginBottom: 12 },
  box: { borderWidth: 1, borderColor: COLORS.line, borderRadius: 4, padding: 8, marginBottom: 12 },
  grid: { flexDirection: "row", flexWrap: "wrap" },
  cell: { width: "50%", marginBottom: 3 },
  label: { fontSize: 8, color: COLORS.muted, textTransform: "uppercase" },
  value: { fontSize: 10 },
  section: { fontSize: 10.5, fontFamily: "Helvetica-Bold", color: COLORS.brand, marginTop: 10, marginBottom: 5 },
  row: { flexDirection: "row", borderBottomWidth: 0.5, borderBottomColor: COLORS.line, paddingVertical: 3.5 },
  question: { width: "62%", paddingRight: 8 },
  answer: { width: "38%" },
  alert: { color: COLORS.danger, fontFamily: "Helvetica-Bold" },
  alertBox: { borderWidth: 1, borderColor: COLORS.danger, borderRadius: 4, padding: 8, marginBottom: 12 },
  body: { fontSize: 10.5, textAlign: "justify" },
  signatures: { flexDirection: "row", justifyContent: "space-around", marginTop: 46 },
  signature: {
    width: "42%",
    borderTopWidth: 1,
    borderTopColor: COLORS.text,
    paddingTop: 4,
    textAlign: "center",
    fontSize: 9,
  },
  footerLeft: { position: "absolute", bottom: 24, left: 42, fontSize: 7.5, color: COLORS.muted },
  // Texto com `render` (paginação) precisa de `top`: com `bottom` o react-pdf 4.x o posiciona fora da folha.
  footerRight: { position: "absolute", top: A4_HEIGHT - 35, right: 42, fontSize: 7.5, color: COLORS.muted },
  th: { fontFamily: "Helvetica-Bold", fontSize: 8.5, color: "#FFFFFF" },
  thead: { flexDirection: "row", backgroundColor: COLORS.brand, paddingVertical: 4, paddingHorizontal: 4 },
  tr: {
    flexDirection: "row",
    borderBottomWidth: 0.5,
    borderBottomColor: COLORS.line,
    paddingVertical: 4,
    paddingHorizontal: 4,
  },
  totals: { alignSelf: "flex-end", width: "45%", marginTop: 8 },
  totalRow: { flexDirection: "row", justifyContent: "space-between", paddingVertical: 2 },
  sigImage: { width: 180, height: 60, objectFit: "contain" },
  small: { fontSize: 8, color: COLORS.muted },
});

const SHORT_UNITS: Record<string, string> = { U: "U", ML: "mL", UN: "un.", MG: "mg" };

export interface PdfContext {
  clinic: ClinicSettings;
  patient: Patient;
  /** Logo em PNG (data URL), se a clínica tiver. */
  logo?: string | null;
}

function clinicLine(c: ClinicSettings): string {
  const address = [c.address, c.city && c.state ? `${c.city}/${c.state}` : c.city, c.zipCode && `CEP ${c.zipCode}`]
    .filter(Boolean)
    .join(" · ");
  return [
    c.legalName && c.legalName !== c.name ? c.legalName : null,
    c.document && `CNPJ/CPF ${c.document}`,
    address,
    c.phone && formatPhone(c.phone),
    c.email,
  ]
    .filter(Boolean)
    .join(" · ");
}

function Sheet({
  ctx,
  title,
  subtitle,
  children,
}: {
  ctx: PdfContext;
  title: string;
  subtitle?: string;
  children: ReactNode;
}) {
  const generated = formatDateTime(new Date().toISOString());
  return (
    <Document
      title={`${title} — ${ctx.patient.fullName}`}
      author={ctx.clinic.name}
      creator="Odonto SaaS"
      language="pt-BR"
    >
      <Page size="A4" style={s.page}>
        <Text
          style={s.footerRight}
          fixed
          render={({ pageNumber, totalPages }) => `Página ${pageNumber} de ${totalPages}`}
        />
        <View style={s.header} fixed>
          {ctx.logo && (
            <View style={s.logoBox}>
              <PdfImage style={s.logo} src={ctx.logo} />
            </View>
          )}
          <View style={{ flex: 1 }}>
            <Text style={s.clinic}>{ctx.clinic.name}</Text>
            {clinicLine(ctx.clinic) && <Text style={s.clinicInfo}>{clinicLine(ctx.clinic)}</Text>}
          </View>
        </View>
        <Text style={s.title}>{title}</Text>
        {subtitle && <Text style={s.subtitle}>{subtitle}</Text>}
        <PatientBox patient={ctx.patient} />
        {children}
        <Text style={s.footerLeft} fixed>
          {ctx.patient.fullName} · Gerado em {generated}
        </Text>
      </Page>
    </Document>
  );
}

function Field({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <View style={s.cell}>
      <Text style={s.label}>{label}</Text>
      <Text style={s.value}>{value || "—"}</Text>
    </View>
  );
}

function PatientBox({ patient: p }: { patient: Patient }) {
  const age = ageFrom(p.birthDate);
  return (
    <View style={s.box}>
      <View style={s.grid}>
        <Field label="Paciente" value={p.socialName ? `${p.socialName} (${p.fullName})` : p.fullName} />
        <Field label="CPF" value={p.cpf ? formatCpf(p.cpf) : null} />
        <Field
          label="Nascimento"
          value={p.birthDate ? `${formatDate(p.birthDate)}${age !== null ? ` (${age} anos)` : ""}` : null}
        />
        <Field label="Telefone" value={formatPhone(p.mobile ?? p.phone)} />
        {p.guardianName && <Field label="Responsável legal" value={p.guardianName} />}
      </View>
    </View>
  );
}

function Signatures({ left, right }: { left: string; right?: string }) {
  return (
    <View style={s.signatures} wrap={false}>
      <Text style={s.signature}>{left}</Text>
      {right && <Text style={s.signature}>{right}</Text>}
    </View>
  );
}

const answerText = (a: { answer?: string | null; detail?: string | null } | undefined, q: Question) => {
  const yesNo = a?.answer === "YES" ? "Sim" : a?.answer === "NO" ? "Não" : q.type === "TEXT" ? "" : "Sem resposta";
  return [yesNo, a?.detail].filter(Boolean).join(" — ") || "—";
};

export function AnamnesisPdf({ ctx, anamnesis: a }: { ctx: PdfContext; anamnesis: Anamnesis }) {
  const sections = new Map<string, Question[]>();
  for (const q of a.questions) sections.set(q.section, [...(sections.get(q.section) ?? []), q]);
  return (
    <Sheet
      ctx={ctx}
      title={a.formName}
      subtitle={`Preenchida em ${formatDateTime(a.createdAt)} por ${a.filledBy === "PATIENT" ? "paciente" : "profissional"}${a.createdByName ? ` · registrada por ${a.createdByName}` : ""}`}
    >
      {a.alerts.length > 0 && (
        <View style={s.alertBox} wrap={false}>
          <Text style={s.alert}>ATENÇÃO — respostas que exigem cuidado:</Text>
          {a.alerts.map((x) => (
            <Text key={x.questionId}>
              • {x.text}
              {x.detail ? ` — ${x.detail}` : ""}
            </Text>
          ))}
        </View>
      )}
      {[...sections.entries()].map(([section, qs]) => (
        <View key={section}>
          <Text style={s.section}>{section}</Text>
          {qs.map((q) => {
            const ans = a.answers[q.id];
            const isAlert = q.alertOnYes && ans?.answer === "YES";
            return (
              <View key={q.id} style={s.row} wrap={false}>
                <Text style={s.question}>{q.text}</Text>
                <Text style={[s.answer, ...(isAlert ? [s.alert] : [])]}>{answerText(ans, q)}</Text>
              </View>
            );
          })}
        </View>
      ))}
      {a.notes && (
        <View>
          <Text style={s.section}>Observações</Text>
          <Text>{a.notes}</Text>
        </View>
      )}
      <Text style={[s.body, { marginTop: 16 }]}>
        Declaro que as informações acima são verdadeiras e que comunicarei à clínica qualquer alteração no meu estado de
        saúde ou nos medicamentos que utilizo.
      </Text>
      <Signatures left="Paciente ou responsável legal" right="Profissional responsável" />
    </Sheet>
  );
}

export function ConsentPdf({ ctx, consent: c }: { ctx: PdfContext; consent: Consent }) {
  const category = CONSENT_CATEGORIES[c.category]?.label;
  return (
    <Sheet
      ctx={ctx}
      title={c.title}
      subtitle={`${category ? `${category} · ` : ""}Emitido em ${formatDateTime(c.createdAt)}${c.templateVersion ? ` · modelo v${c.templateVersion}` : ""}`}
    >
      <Text style={s.body}>{c.body}</Text>
      {c.status === "SIGNED" ? (
        <View style={[s.box, { marginTop: 22 }]} wrap={false}>
          {c.signatureImage && <PdfImage style={s.sigImage} src={c.signatureImage} />}
          <Text style={{ fontFamily: "Helvetica-Bold" }}>
            {c.signerName}
            {c.signerDocument ? ` · Documento: ${c.signerDocument}` : ""}
          </Text>
          <Text style={s.small}>
            Assinado eletronicamente em {formatDateTime(c.signedAt)} · IP {c.signedIp ?? "—"}
          </Text>
          <Text style={s.small}>Integridade do texto (SHA-256): {c.bodySha256}</Text>
        </View>
      ) : c.status === "REVOKED" ? (
        <View style={[s.alertBox, { marginTop: 22 }]}>
          <Text style={s.alert}>
            Termo revogado em {formatDateTime(c.revokedAt)}: {c.revokeReason}
          </Text>
        </View>
      ) : (
        <>
          <Text style={[s.small, { marginTop: 14 }]}>Documento aguardando assinatura.</Text>
          <Signatures left="Paciente ou responsável legal" right={c.professionalName ?? "Profissional responsável"} />
        </>
      )}
    </Sheet>
  );
}

export function ClinicalDocumentPdf({ ctx, doc: d }: { ctx: PdfContext; doc: ClinicalDocument }) {
  const type = DOCUMENT_TYPES.find((t) => t.value === d.type)?.label ?? "Documento";
  return (
    <Sheet ctx={ctx} title={d.title} subtitle={`${type} · Emitido em ${formatDateTime(d.issuedAt)}`}>
      <Text style={s.body}>{d.content}</Text>
      <Text style={{ marginTop: 26 }}>
        {ctx.clinic.city ? `${ctx.clinic.city}, ` : ""}
        {formatDate(d.issuedAt)}.
      </Text>
      <View style={s.signatures} wrap={false}>
        <Text style={s.signature}>
          {d.professionalName}
          {d.professionalCro ? `\n${d.professionalCro}` : ""}
        </Text>
      </View>
    </Sheet>
  );
}

export function AestheticPdf({ ctx, record: r }: { ctx: PdfContext; record: AestheticRecord }) {
  const unit = SHORT_UNITS[r.unit] ?? r.unit;
  const qty = (n: number) => `${Number(n).toLocaleString("pt-BR")} ${unit}`;
  return (
    <Sheet
      ctx={ctx}
      title="Registro de procedimento — harmonização orofacial"
      subtitle={`${AESTHETIC_TYPES[r.procedureType] ?? r.procedureType} · ${formatDateTime(r.performedAt)}`}
    >
      {r.voidedAt && (
        <View style={s.alertBox}>
          <Text style={s.alert}>
            REGISTRO ANULADO em {formatDateTime(r.voidedAt)}: {r.voidReason}
          </Text>
        </View>
      )}
      <View style={s.box}>
        <View style={s.grid}>
          <Field label="Profissional" value={r.professionalName} />
          <Field label="Procedimento" value={AESTHETIC_TYPES[r.procedureType] ?? r.procedureType} />
          <Field label="Produto" value={r.productName} />
          <Field label="Fabricante" value={r.manufacturer} />
          <Field label="Lote" value={r.batch} />
          <Field label="Validade" value={formatDate(r.expiryDate)} />
          <Field label="Registro ANVISA" value={r.anvisaRegistry} />
          <Field label="Retorno / revisão" value={formatDate(r.returnDate)} />
          <Field
            label="Termo de consentimento"
            value={
              r.consentStatus === "SIGNED" ? "Assinado" : r.consentStatus ? "Aguardando assinatura" : "Não vinculado"
            }
          />
        </View>
      </View>
      <Text style={s.section}>Regiões tratadas</Text>
      <View style={s.thead}>
        <Text style={[s.th, { width: "45%" }]}>Região</Text>
        <Text style={[s.th, { width: "20%", textAlign: "right" }]}>Quantidade</Text>
        <Text style={[s.th, { width: "35%", paddingLeft: 8 }]}>Observação</Text>
      </View>
      {r.regions.map((x, i) => (
        <View key={i} style={s.tr} wrap={false}>
          <Text style={{ width: "45%" }}>{x.region}</Text>
          <Text style={{ width: "20%", textAlign: "right" }}>{qty(x.quantity)}</Text>
          <Text style={{ width: "35%", paddingLeft: 8 }}>{x.notes || "—"}</Text>
        </View>
      ))}
      <View style={s.totals}>
        <View style={s.totalRow}>
          <Text style={{ fontFamily: "Helvetica-Bold" }}>Total aplicado</Text>
          <Text style={{ fontFamily: "Helvetica-Bold" }}>{qty(r.totalQuantity)}</Text>
        </View>
      </View>
      {r.technique && (
        <View>
          <Text style={s.section}>Técnica / plano de aplicação</Text>
          <Text>{r.technique}</Text>
        </View>
      )}
      {r.notes && (
        <View>
          <Text style={s.section}>Observações</Text>
          <Text>{r.notes}</Text>
        </View>
      )}
      <Signatures left="Paciente ou responsável legal" right={r.professionalName} />
    </Sheet>
  );
}

export function PlanPdf({ ctx, plan: p }: { ctx: PdfContext; plan: Plan }) {
  const items = p.items.filter((i) => i.status !== "CANCELLED");
  return (
    <Sheet
      ctx={ctx}
      title={`Orçamento — ${p.title}`}
      subtitle={`${PLAN_STATUS[p.status]?.label ?? p.status} · Emitido em ${formatDate(p.createdAt)}${p.validUntil ? ` · válido até ${formatDate(p.validUntil)}` : ""} · ${p.professionalName}`}
    >
      <View style={s.thead}>
        <Text style={[s.th, { width: "46%" }]}>Procedimento</Text>
        <Text style={[s.th, { width: "9%", textAlign: "center" }]}>Dente</Text>
        <Text style={[s.th, { width: "7%", textAlign: "center" }]}>Qtd.</Text>
        <Text style={[s.th, { width: "13%", textAlign: "right" }]}>Valor</Text>
        <Text style={[s.th, { width: "11%", textAlign: "right" }]}>Desc.</Text>
        <Text style={[s.th, { width: "14%", textAlign: "right" }]}>Total</Text>
      </View>
      {items.map((i) => (
        <View key={i.id} style={s.tr} wrap={false}>
          <Text style={{ width: "46%" }}>{i.description}</Text>
          <Text style={{ width: "9%", textAlign: "center" }}>{i.tooth ?? "—"}</Text>
          <Text style={{ width: "7%", textAlign: "center" }}>{i.quantity}</Text>
          <Text style={{ width: "13%", textAlign: "right" }}>{formatMoney(i.unitPrice)}</Text>
          <Text style={{ width: "11%", textAlign: "right" }}>{Number(i.discount) ? formatMoney(i.discount) : "—"}</Text>
          <Text style={{ width: "14%", textAlign: "right" }}>{formatMoney(i.total)}</Text>
        </View>
      ))}
      <View style={s.totals} wrap={false}>
        <View style={s.totalRow}>
          <Text>Subtotal</Text>
          <Text>{formatMoney(p.subtotal)}</Text>
        </View>
        {Number(p.discount) > 0 && (
          <View style={s.totalRow}>
            <Text>Desconto</Text>
            <Text>- {formatMoney(p.discount)}</Text>
          </View>
        )}
        <View style={[s.totalRow, { borderTopWidth: 1, borderTopColor: COLORS.text, marginTop: 2, paddingTop: 4 }]}>
          <Text style={{ fontFamily: "Helvetica-Bold", fontSize: 12 }}>Total</Text>
          <Text style={{ fontFamily: "Helvetica-Bold", fontSize: 12 }}>{formatMoney(p.total)}</Text>
        </View>
      </View>
      {p.paymentConditions && (
        <View>
          <Text style={s.section}>Condições de pagamento</Text>
          <Text>{p.paymentConditions}</Text>
        </View>
      )}
      {p.notes && (
        <View>
          <Text style={s.section}>Observações</Text>
          <Text>{p.notes}</Text>
        </View>
      )}
      {p.status === "APPROVED" && (
        <Text style={[s.small, { marginTop: 10 }]}>
          Aprovado em {formatDateTime(p.approvedAt)}
          {p.approvedByName ? ` (registrado por ${p.approvedByName})` : ""}.
        </Text>
      )}
      <Signatures left="De acordo — paciente ou responsável" right={p.professionalName} />
    </Sheet>
  );
}

/** Recibo de pagamento: valor em número e por extenso, referência, forma de pagamento, data e assinatura. */
export function ReceiptPdf({ ctx, receipt: r }: { ctx: PdfContext; receipt: ReceiptData }) {
  const net = Number(r.amount) - Number(r.refundedAmount);
  const payer = ctx.patient.guardianName
    ? `${ctx.patient.guardianName}, responsável por ${ctx.patient.fullName}`
    : ctx.patient.fullName;
  const cpf = ctx.patient.guardianName ? ctx.patient.guardianCpf : ctx.patient.cpf;
  // Dia do pagamento no fuso do navegador (o mesmo usado no restante do recibo).
  const paidDay = formatDateTime(r.paidAt).split(",")[0] ?? "";
  return (
    <Sheet ctx={ctx} title="Recibo" subtitle={`Nº ${r.id.slice(0, 8).toUpperCase()} · ${formatDateTime(r.paidAt)}`}>
      <View style={[s.box, { alignItems: "center", paddingVertical: 12 }]}>
        <Text style={{ fontFamily: "Helvetica-Bold", fontSize: 18 }}>{formatMoney(r.amount)}</Text>
        <Text style={[s.small, { marginTop: 10 }]}>({moneyInWords(r.amount)})</Text>
      </View>
      <Text style={{ marginTop: 6 }}>
        Recebemos de {payer}
        {cpf ? `, CPF ${formatCpf(cpf)},` : ""} a importância de {formatMoney(r.amount)} ({moneyInWords(r.amount)}),
        referente a {r.description}
        {r.installment ? ` — parcela ${r.installment}` : ""}, paga em {methodLabel(r.method)} no dia {paidDay}. Para
        clareza, firmamos o presente recibo.
      </Text>
      {Number(r.refundedAmount) > 0 && (
        <Text style={[s.small, { marginTop: 8 }]}>
          Estorno registrado: {formatMoney(r.refundedAmount)}. Valor líquido recebido: {formatMoney(net)}.
        </Text>
      )}
      {r.notes && <Text style={[s.small, { marginTop: 8 }]}>Observações: {r.notes}</Text>}
      <Text style={{ marginTop: 24 }}>
        {ctx.clinic.city ? `${ctx.clinic.city}, ` : ""}
        {paidDay}.
      </Text>
      <Signatures
        left={`${ctx.clinic.legalName || ctx.clinic.name}${ctx.clinic.document ? ` — CNPJ/CPF ${ctx.clinic.document}` : ""}`}
        right={r.createdByName ? `Recebido por ${r.createdByName}` : undefined}
      />
    </Sheet>
  );
}
