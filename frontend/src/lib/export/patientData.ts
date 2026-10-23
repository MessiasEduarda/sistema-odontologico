import { api } from "@/lib/api/endpoints";
import type { Patient } from "@/lib/api/types";
import { formatCpf, formatDate, formatDateTime, formatPhone } from "@/lib/format";
import {
  AESTHETIC_TYPES,
  APPOINTMENT_STATUS,
  ATTACHMENT_CATEGORIES,
  CHART_STATUS,
  conditionInfo,
  CONSENT_STATUS,
  DOCUMENT_TYPES,
  FINANCE_STATUS,
  PLAN_STATUS,
  SEX_OPTIONS,
} from "@/lib/labels";
import type { ExportTable } from "./tables";

const NO_ACCESS = "Não disponível para o perfil de acesso de quem gerou o arquivo.";

async function optional<T>(load: () => Promise<T>): Promise<T | null> {
  try {
    return await load();
  } catch {
    return null;
  }
}

function table<T>(
  title: string,
  rows: T[] | null,
  columns: ExportTable["columns"],
  cells: (row: T) => ExportTable["rows"][number],
): ExportTable {
  if (rows === null) return { title, columns, rows: [], note: NO_ACCESS };
  return { title, columns, rows: rows.map(cells) };
}

export async function loadPatientData(patientId: string): Promise<{ patient: Patient; tables: ExportTable[] }> {
  await api.patients.exportData(patientId);
  const [
    patient,
    appointments,
    anamneses,
    chart,
    notes,
    performed,
    documents,
    consents,
    aesthetics,
    attachments,
    plans,
    receivables,
  ] = await Promise.all([
    api.patients.get(patientId),
    optional(() => api.appointments.list({ patientId })),
    optional(() => api.clinical.anamneses(patientId)),
    optional(() => api.clinical.chart(patientId)),
    optional(() => api.clinical.notes(patientId)),
    optional(() => api.clinical.performed(patientId)),
    optional(() => api.clinical.documents(patientId)),
    optional(() => api.clinical.consents(patientId)),
    optional(() => api.clinical.aesthetics(patientId)),
    optional(() => api.clinical.attachments(patientId)),
    optional(() => api.plans.list({ patientId })),
    optional(() => api.finance.receivables({ patientId })),
  ]);

  const p = patient;
  const contact = [p.allowEmail && "E-mail", p.allowSms && "SMS", p.allowWhatsapp && "WhatsApp"].filter(Boolean);
  const address = [
    [p.street, p.number].filter(Boolean).join(", "),
    p.complement,
    p.district,
    p.city && p.state ? `${p.city}/${p.state}` : p.city,
    p.zipCode && `CEP ${p.zipCode}`,
  ]
    .filter(Boolean)
    .join(" · ");
  const registration: [string, string | null | undefined][] = [
    ["Nome completo", p.fullName],
    ["Nome social", p.socialName],
    ["Data de nascimento", p.birthDate ? formatDate(p.birthDate) : null],
    ["Sexo", SEX_OPTIONS.find((o) => o.value === p.sex)?.label],
    ["CPF", p.cpf ? formatCpf(p.cpf) : null],
    ["RG", p.rg],
    ["Celular", p.mobile ? formatPhone(p.mobile) : null],
    ["Telefone", p.phone ? formatPhone(p.phone) : null],
    ["E-mail", p.email],
    ["Endereço", address],
    ["Profissão", p.occupation],
    ["Convênio", [p.healthInsurance, p.insuranceNumber].filter(Boolean).join(" · ")],
    ["Como conheceu a clínica", p.referralSource],
    ["Contato autorizado por", contact.length ? contact.join(", ") : "Nenhum meio"],
    ["Responsável legal", p.guardianName],
    ["CPF do responsável", p.guardianCpf ? formatCpf(p.guardianCpf) : null],
    ["Telefone do responsável", p.guardianPhone ? formatPhone(p.guardianPhone) : null],
    ["Observações", p.notes],
    ["Situação", p.active ? "Ativo" : "Inativo"],
    ["Cadastrado em", formatDateTime(p.createdAt)],
  ];

  const anamnesisRows =
    anamneses?.flatMap((a) =>
      a.questions.map((q) => {
        const ans = a.answers[q.id];
        const yesNo = ans?.answer === "YES" ? "Sim" : ans?.answer === "NO" ? "Não" : "";
        return [a.formName, formatDateTime(a.createdAt), q.text, [yesNo, ans?.detail].filter(Boolean).join(" — ")];
      }),
    ) ?? null;

  const tables: ExportTable[] = [
    {
      title: "Cadastro",
      columns: [
        { header: "Campo", width: 32 },
        { header: "Informação", width: 68 },
      ],
      rows: registration.map(([label, value]) => [label, value || "—"]),
    },
    table(
      "Consultas",
      appointments,
      [
        { header: "Data", width: 22 },
        { header: "Profissional", width: 28 },
        { header: "Procedimento", width: 30 },
        { header: "Situação", width: 20 },
      ],
      (a) => [
        formatDateTime(a.startsAt),
        a.professionalName,
        a.procedureName,
        APPOINTMENT_STATUS[a.status]?.label ?? a.status,
      ],
    ),
    table(
      "Anamneses",
      anamnesisRows,
      [
        { header: "Ficha", width: 20 },
        { header: "Preenchida em", width: 18 },
        { header: "Pergunta", width: 37 },
        { header: "Resposta", width: 25 },
      ],
      (r) => r,
    ),
    table(
      "Odontograma",
      chart,
      [
        { header: "Dente", width: 10, align: "center" },
        { header: "Faces", width: 14 },
        { header: "Condição", width: 30 },
        { header: "Situação", width: 22 },
        { header: "Registrado em", width: 24 },
      ],
      (c) => [
        c.tooth,
        c.surfaces,
        conditionInfo(c.condition).label,
        c.voidedAt ? "Anulada" : (CHART_STATUS[c.status] ?? c.status),
        formatDate(c.createdAt),
      ],
    ),
    table(
      "Evoluções",
      notes,
      [
        { header: "Data", width: 20 },
        { header: "Profissional", width: 22 },
        { header: "Anotação", width: 58 },
      ],
      (n) => [formatDateTime(n.createdAt), n.professionalName ?? n.createdByName, n.content],
    ),
    table(
      "Procedimentos realizados",
      performed,
      [
        { header: "Data", width: 16 },
        { header: "Procedimento", width: 36 },
        { header: "Dente / faces", width: 16 },
        { header: "Profissional", width: 20 },
        { header: "Situação", width: 12 },
      ],
      (x) => [
        formatDate(x.performedAt),
        x.description,
        [x.tooth, x.surfaces].filter(Boolean).join(" · "),
        x.professionalName,
        x.voidedAt ? "Anulado" : "Realizado",
      ],
    ),
    table(
      "Documentos emitidos",
      documents,
      [
        { header: "Data", width: 18 },
        { header: "Tipo", width: 20 },
        { header: "Título", width: 37 },
        { header: "Profissional", width: 25 },
      ],
      (x) => [
        formatDate(x.issuedAt),
        DOCUMENT_TYPES.find((t) => t.value === x.type)?.label,
        x.title,
        x.professionalName,
      ],
    ),
    table(
      "Termos",
      consents,
      [
        { header: "Emissão", width: 18 },
        { header: "Termo", width: 42 },
        { header: "Situação", width: 18 },
        { header: "Assinado em", width: 22 },
      ],
      (c) => [
        formatDate(c.createdAt),
        c.title,
        CONSENT_STATUS[c.status]?.label ?? c.status,
        c.signedAt ? formatDateTime(c.signedAt) : "",
      ],
    ),
  ];

  if (aesthetics && aesthetics.length > 0) {
    tables.push(
      table(
        "Harmonização",
        aesthetics,
        [
          { header: "Data", width: 15 },
          { header: "Procedimento", width: 23 },
          { header: "Produto", width: 23 },
          { header: "Lote", width: 14 },
          { header: "Profissional", width: 25 },
        ],
        (r) => [
          formatDate(r.performedAt),
          AESTHETIC_TYPES[r.procedureType] ?? r.procedureType,
          r.productName,
          r.batch,
          r.professionalName,
        ],
      ),
    );
  }

  tables.push(
    table(
      "Orçamentos",
      plans,
      [
        { header: "Data", width: 15 },
        { header: "Título", width: 35 },
        { header: "Profissional", width: 22 },
        { header: "Total", width: 14, money: true, align: "right" },
        { header: "Situação", width: 14 },
      ],
      (x) => [
        formatDate(x.createdAt),
        x.title,
        x.professionalName,
        Number(x.total),
        PLAN_STATUS[x.status]?.label ?? x.status,
      ],
    ),
    table(
      "Financeiro",
      receivables,
      [
        { header: "Data", width: 14 },
        { header: "Descrição", width: 32 },
        { header: "Total", width: 14, money: true, align: "right" },
        { header: "Pago", width: 14, money: true, align: "right" },
        { header: "Saldo", width: 14, money: true, align: "right" },
        { header: "Situação", width: 12 },
      ],
      (x) => [
        formatDate(x.createdAt),
        x.description,
        Number(x.total),
        Number(x.paid),
        Number(x.balance),
        FINANCE_STATUS[x.status]?.label ?? x.status,
      ],
    ),
    table(
      "Anexos",
      attachments,
      [
        { header: "Data", width: 16 },
        { header: "Tipo", width: 18 },
        { header: "Arquivo", width: 36 },
        { header: "Descrição", width: 30 },
      ],
      (x) => [
        formatDate(x.createdAt),
        ATTACHMENT_CATEGORIES.find((c) => c.value === x.category)?.label,
        x.filename,
        x.description,
      ],
    ),
  );

  return { patient, tables };
}
