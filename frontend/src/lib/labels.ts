import type { BadgeTone } from "@/components/ui/display";

type Labeled = Record<string, { label: string; tone: BadgeTone }>;

export const APPOINTMENT_STATUS: Labeled = {
  SCHEDULED: { label: "Agendado", tone: "neutral" },
  CONFIRMED: { label: "Confirmado", tone: "info" },
  ARRIVED: { label: "Chegou", tone: "primary" },
  IN_PROGRESS: { label: "Em atendimento", tone: "warning" },
  COMPLETED: { label: "Concluído", tone: "success" },
  NO_SHOW: { label: "Faltou", tone: "danger" },
  CANCELLED: { label: "Cancelado", tone: "neutral" },
};

export const APPOINTMENT_ACTIONS: Record<string, { status: string; label: string }[]> = {
  SCHEDULED: [
    { status: "CONFIRMED", label: "Confirmar" },
    { status: "ARRIVED", label: "Registrar chegada" },
    { status: "NO_SHOW", label: "Marcar falta" },
    { status: "CANCELLED", label: "Cancelar" },
  ],
  CONFIRMED: [
    { status: "ARRIVED", label: "Registrar chegada" },
    { status: "NO_SHOW", label: "Marcar falta" },
    { status: "SCHEDULED", label: "Desfazer confirmação" },
    { status: "CANCELLED", label: "Cancelar" },
  ],
  ARRIVED: [
    { status: "IN_PROGRESS", label: "Iniciar atendimento" },
    { status: "COMPLETED", label: "Concluir" },
    { status: "NO_SHOW", label: "Marcar falta" },
  ],
  IN_PROGRESS: [{ status: "COMPLETED", label: "Concluir" }],
  NO_SHOW: [{ status: "SCHEDULED", label: "Reabrir" }],
  COMPLETED: [],
  CANCELLED: [],
};

export const PLAN_STATUS: Labeled = {
  DRAFT: { label: "Rascunho", tone: "neutral" },
  PROPOSED: { label: "Apresentado", tone: "info" },
  APPROVED: { label: "Aprovado", tone: "success" },
  REJECTED: { label: "Recusado", tone: "danger" },
  COMPLETED: { label: "Concluído", tone: "primary" },
  CANCELLED: { label: "Cancelado", tone: "neutral" },
};

export const FINANCE_STATUS: Labeled = {
  OPEN: { label: "Em aberto", tone: "warning" },
  PAID: { label: "Pago", tone: "success" },
  CANCELLED: { label: "Cancelado", tone: "neutral" },
};

export const CONSENT_STATUS: Labeled = {
  PENDING: { label: "Aguardando assinatura", tone: "warning" },
  SIGNED: { label: "Assinado", tone: "success" },
  REVOKED: { label: "Revogado", tone: "neutral" },
};

export const MESSAGE_STATUS: Labeled = {
  PENDING: { label: "Na fila", tone: "warning" },
  SENT: { label: "Enviado", tone: "success" },
  FAILED: { label: "Falhou", tone: "danger" },
};

export const SUBSCRIPTION_STATUS: Labeled = {
  TRIALING: { label: "Avaliação", tone: "info" },
  ACTIVE: { label: "Ativa", tone: "success" },
  PAST_DUE: { label: "Inadimplente", tone: "danger" },
  CANCELLED: { label: "Cancelada", tone: "neutral" },
};

export const PAYMENT_METHODS: { value: string; label: string }[] = [
  { value: "PIX", label: "Pix" },
  { value: "CASH", label: "Dinheiro" },
  { value: "CREDIT_CARD", label: "Cartão de crédito" },
  { value: "DEBIT_CARD", label: "Cartão de débito" },
  { value: "BANK_TRANSFER", label: "Transferência" },
  { value: "BOLETO", label: "Boleto" },
  { value: "OTHER", label: "Outro" },
];

export const methodLabel = (m: string | null | undefined) =>
  PAYMENT_METHODS.find((p) => p.value === m)?.label ?? m ?? "—";

export const TOOTH_CONDITIONS: { value: string; label: string; color: string }[] = [
  { value: "CARIES", label: "Cárie", color: "#B3261E" },
  { value: "RESTORATION", label: "Restauração", color: "#2F5F8A" },
  { value: "ROOT_CANAL", label: "Tratamento de canal", color: "#7A3E9D" },
  { value: "CROWN", label: "Coroa", color: "#9C7A5B" },
  { value: "IMPLANT", label: "Implante", color: "#2E7D4F" },
  { value: "PROSTHESIS", label: "Prótese", color: "#6B655F" },
  { value: "SEALANT", label: "Selante", color: "#3A8FA8" },
  { value: "FRACTURE", label: "Fratura", color: "#C26A00" },
  { value: "MOBILITY", label: "Mobilidade", color: "#A35C00" },
  { value: "EXTRACTION", label: "Extração indicada", color: "#8C1D18" },
  { value: "MISSING", label: "Ausente", color: "#1E1C1A" },
  { value: "OTHER", label: "Outro", color: "#8D867F" },
];

export const conditionInfo = (code: string) =>
  TOOTH_CONDITIONS.find((c) => c.value === code) ?? { value: code, label: code, color: "#8D867F" };

export const CHART_STATUS: Record<string, string> = {
  EXISTING: "Existente",
  PLANNED: "Planejado",
  DONE: "Realizado",
};

export const DOCUMENT_TYPES: { value: string; label: string }[] = [
  { value: "PRESCRIPTION", label: "Receituário" },
  { value: "CERTIFICATE", label: "Atestado" },
  { value: "REFERRAL", label: "Encaminhamento" },
  { value: "INSTRUCTIONS", label: "Orientações" },
  { value: "OTHER", label: "Outro" },
];

export const ATTACHMENT_CATEGORIES: { value: string; label: string }[] = [
  { value: "XRAY", label: "Radiografia" },
  { value: "PHOTO", label: "Foto" },
  { value: "EXAM", label: "Exame" },
  { value: "DOCUMENT", label: "Documento" },
  { value: "OTHER", label: "Outro" },
];

export const ROLE_LABELS: Record<string, string> = {
  OWNER: "Administrador",
  DENTIST: "Dentista",
  ASSISTANT: "Auxiliar / ASB",
  RECEPTION: "Recepção",
  FINANCE: "Financeiro",
};

export const MESSAGE_TYPES: Record<string, string> = {
  APPOINTMENT_REMINDER: "Lembrete de consulta",
  APPOINTMENT_CREATED: "Consulta agendada",
  APPOINTMENT_CANCELLED: "Consulta cancelada",
  PORTAL_ACCESS: "Acesso ao portal",
  CONSENT_REQUEST: "Termo para assinatura",
  CARE_INSTRUCTIONS: "Orientações ao paciente",
};

export const FEATURE_LABELS: Record<string, string> = {
  AGENDA: "Agenda",
  PATIENTS: "Pacientes",
  CLINICAL: "Prontuário",
  TREATMENTS: "Tratamentos",
  FINANCE: "Financeiro",
  INVENTORY: "Estoque",
  COMMUNICATION: "Comunicação",
  REPORTS: "Relatórios",
  PATIENT_PORTAL: "Portal do paciente",
};

export const SEX_OPTIONS = [
  { value: "NOT_INFORMED", label: "Não informado" },
  { value: "FEMALE", label: "Feminino" },
  { value: "MALE", label: "Masculino" },
  { value: "OTHER", label: "Outro" },
];

export const UF_OPTIONS = [
  "AC",
  "AL",
  "AP",
  "AM",
  "BA",
  "CE",
  "DF",
  "ES",
  "GO",
  "MA",
  "MT",
  "MS",
  "MG",
  "PA",
  "PB",
  "PR",
  "PE",
  "PI",
  "RJ",
  "RN",
  "RS",
  "RO",
  "RR",
  "SC",
  "SP",
  "SE",
  "TO",
].map((uf) => ({ value: uf, label: uf }));

export const WEEKDAYS = ["", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo"];

export const FORM_KINDS: Record<string, { label: string; tone: "primary" | "info" | "neutral" }> = {
  DENTAL: { label: "Odontológica", tone: "primary" },
  AESTHETIC: { label: "Harmonização", tone: "info" },
  OTHER: { label: "Outra", tone: "neutral" },
};

export const CONSENT_CATEGORIES: Record<string, { label: string; tone: "primary" | "info" | "warning" | "neutral" }> = {
  DENTAL: { label: "Odontologia", tone: "primary" },
  AESTHETIC: { label: "Harmonização", tone: "info" },
  IMAGE: { label: "Uso de imagem", tone: "warning" },
  PRIVACY: { label: "Privacidade (LGPD)", tone: "neutral" },
  GENERAL: { label: "Geral", tone: "neutral" },
};

export const CARE_CATEGORIES: Record<string, string> = {
  DENTAL: "Odontologia",
  AESTHETIC: "Harmonização",
  GENERAL: "Geral",
};

export const CARE_MOMENTS: Record<string, string> = {
  BEFORE: "Antes do procedimento",
  AFTER: "Depois do procedimento",
  GENERAL: "Cuidados gerais",
};

export const AESTHETIC_TYPES: Record<string, string> = {
  TOXIN: "Toxina botulínica",
  FILLER: "Preenchimento (ácido hialurônico)",
  BIOSTIMULATOR: "Bioestimulador de colágeno",
  THREADS: "Fios de sustentação",
  SKINBOOSTER: "Skinbooster",
  PEEL: "Peeling",
  MICRONEEDLING: "Microagulhamento",
  LIPOLYSIS: "Enzima / lipo de papada",
  OTHER: "Outro",
};

export const AESTHETIC_UNITS: Record<string, string> = {
  U: "U (unidades)",
  ML: "mL",
  UN: "unidade(s)",
  MG: "mg",
};

export const FACE_REGIONS = [
  "Fronte (testa)",
  "Glabela",
  "Região periorbital (pés de galinha)",
  "Têmporas",
  "Sobrancelhas",
  "Olheiras",
  "Malar (maçãs do rosto)",
  "Nariz",
  "Sulco nasogeniano",
  "Lábio superior",
  "Lábio inferior",
  "Contorno labial",
  "Comissura labial",
  "Linhas de marionete",
  "Mento (queixo)",
  "Mandíbula",
  "Masseter",
  "Submentoniana (papada)",
  "Pescoço",
  "Colo",
];

export const TICKET_STATUS: Labeled = {
  OPEN: { label: "Aberto", tone: "info" },
  IN_PROGRESS: { label: "Em análise", tone: "primary" },
  WAITING_CLINIC: { label: "Aguardando clínica", tone: "warning" },
  RESOLVED: { label: "Resolvido", tone: "success" },
  CLOSED: { label: "Encerrado", tone: "neutral" },
};

export const TICKET_PRIORITY: Labeled = {
  URGENT: { label: "Urgente", tone: "danger" },
  HIGH: { label: "Alta", tone: "warning" },
  NORMAL: { label: "Normal", tone: "info" },
  LOW: { label: "Baixa", tone: "neutral" },
};

export const TICKET_CATEGORIES: { value: string; label: string }[] = [
  { value: "BUG", label: "Erro no sistema" },
  { value: "HOW_TO", label: "Dúvida de uso" },
  { value: "ACCESS", label: "Acesso e senha" },
  { value: "BILLING", label: "Assinatura e pagamento" },
  { value: "SUGGESTION", label: "Sugestão" },
  { value: "DATA_PRIVACY", label: "Dados e LGPD" },
];

export const TICKET_IMPACTS: { value: string; label: string; hint: string }[] = [
  { value: "BLOCKING", label: "Não consigo atender pacientes", hint: "Urgente: resposta em até 2 horas úteis" },
  { value: "DEGRADED", label: "Atrapalha, mas dá para trabalhar", hint: "Alta: resposta em até 8 horas úteis" },
  { value: "QUESTION", label: "Dúvida ou ajuste", hint: "Normal: resposta em até 1 dia útil" },
  { value: "SUGGESTION", label: "Sugestão de melhoria", hint: "Baixa: resposta em até 3 dias úteis" },
];

export const ticketCategoryLabel = (code: string) => TICKET_CATEGORIES.find((c) => c.value === code)?.label ?? code;
