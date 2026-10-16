/** Tipos dos contratos da API (espelham os DTOs do back-end; ver /v3/api-docs). */

export type Uuid = string;
export type IsoInstant = string;
export type IsoDate = string;

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

// ------------------------------------------------------------------ Cadastros
export interface ClinicSettings {
  name: string;
  legalName: string | null;
  document: string | null;
  phone: string | null;
  email: string | null;
  zipCode: string | null;
  address: string | null;
  city: string | null;
  state: string | null;
  defaultAppointmentMinutes: number;
  remindersEnabled: boolean;
  reminderHoursBefore: number;
  timeZone: string;
  /** A clínica realiza harmonização orofacial (exibe ficha, termos e registro de aplicações). */
  aestheticsEnabled: boolean;
  /** Quando a logo foi enviada (nulo = sem logo). */
  logoUpdatedAt: IsoInstant | null;
}

export interface Professional {
  id: Uuid;
  userId: Uuid | null;
  name: string;
  cro: string | null;
  croState: string | null;
  specialty: string | null;
  color: string;
  phone: string | null;
  email: string | null;
  commissionPercent: number | null;
  active: boolean;
  version: number;
}

export interface ScheduleSlot {
  weekday: number;
  startTime: string;
  endTime: string;
}

export interface Room {
  id: Uuid;
  name: string;
  active: boolean;
}

export interface Procedure {
  id: Uuid;
  code: string | null;
  name: string;
  category: string | null;
  defaultPrice: number;
  durationMinutes: number;
  active: boolean;
}

// ------------------------------------------------------------------ Pacientes
export interface PatientSummary {
  id: Uuid;
  fullName: string;
  socialName: string | null;
  birthDate: IsoDate | null;
  cpf: string | null;
  mobile: string | null;
  phone: string | null;
  email: string | null;
  active: boolean;
  nextAppointment: IsoInstant | null;
  lastAppointment: IsoInstant | null;
}

export interface Patient {
  id: Uuid;
  fullName: string;
  socialName: string | null;
  birthDate: IsoDate | null;
  cpf: string | null;
  rg: string | null;
  sex: "FEMALE" | "MALE" | "OTHER" | "NOT_INFORMED";
  phone: string | null;
  mobile: string | null;
  email: string | null;
  zipCode: string | null;
  street: string | null;
  number: string | null;
  complement: string | null;
  district: string | null;
  city: string | null;
  state: string | null;
  occupation: string | null;
  referralSource: string | null;
  healthInsurance: string | null;
  insuranceNumber: string | null;
  guardianName: string | null;
  guardianCpf: string | null;
  guardianPhone: string | null;
  notes: string | null;
  allowEmail: boolean;
  allowSms: boolean;
  allowWhatsapp: boolean;
  active: boolean;
  createdAt: IsoInstant;
  updatedAt: IsoInstant;
  version: number;
}

export type PatientInput = Omit<Patient, "id" | "active" | "createdAt" | "updatedAt" | "version"> & {
  version?: number;
};

// ------------------------------------------------------------------ Agenda
export type AppointmentStatus =
  "SCHEDULED" | "CONFIRMED" | "ARRIVED" | "IN_PROGRESS" | "COMPLETED" | "NO_SHOW" | "CANCELLED";

export interface Appointment {
  id: Uuid;
  patientId: Uuid;
  patientName: string;
  patientMobile: string | null;
  professionalId: Uuid;
  professionalName: string;
  professionalColor: string;
  roomId: Uuid | null;
  roomName: string | null;
  procedureId: Uuid | null;
  procedureName: string | null;
  startsAt: IsoInstant;
  endsAt: IsoInstant;
  status: AppointmentStatus;
  notes: string | null;
  cancellationReason: string | null;
  confirmedAt: IsoInstant | null;
  reminderSentAt: IsoInstant | null;
  version: number;
}

export interface AppointmentDetail {
  appointment: Appointment;
  history: { action: string; details: Record<string, unknown>; actorName: string | null; occurredAt: IsoInstant }[];
}

export interface AppointmentInput {
  patientId: Uuid;
  professionalId: Uuid;
  roomId?: Uuid | null;
  procedureId?: Uuid | null;
  startsAt: IsoInstant;
  endsAt: IsoInstant;
  notes?: string | null;
  version?: number;
}

// ------------------------------------------------------------------ Prontuário
export interface Question {
  id: string;
  section: string;
  text: string;
  type: "YES_NO" | "YES_NO_TEXT" | "TEXT";
  alertOnYes: boolean;
}

export interface Answer {
  answer?: "YES" | "NO" | null;
  detail?: string | null;
}

export interface ClinicalAlert {
  questionId: string;
  text: string;
  detail: string | null;
}

export type FormKind = "DENTAL" | "AESTHETIC" | "OTHER";

export interface AnamnesisForm {
  id: Uuid;
  name: string;
  kind: FormKind;
  questions: Question[];
  active: boolean;
  updatedAt: IsoInstant;
}

export interface Anamnesis {
  id: Uuid;
  formId: Uuid | null;
  formName: string;
  formKind: FormKind;
  questions: Question[];
  answers: Record<string, Answer>;
  alerts: ClinicalAlert[];
  notes: string | null;
  filledBy: string;
  createdByName: string | null;
  createdAt: IsoInstant;
}

export interface RecentAnamnesis {
  id: Uuid;
  patientId: Uuid;
  patientName: string;
  formName: string;
  formKind: FormKind;
  alerts: ClinicalAlert[];
  filledBy: string;
  createdByName: string | null;
  createdAt: IsoInstant;
}

export interface RecordSummary {
  patientId: Uuid;
  patientName: string;
  birthDate: IsoDate | null;
  alerts: ClinicalAlert[];
  lastAnamnesisAt: IsoInstant | null;
  notesCount: number;
  pendingConsents: number;
  attachmentsCount: number;
}

export interface ChartEntry {
  id: Uuid;
  tooth: number;
  surfaces: string | null;
  condition: string;
  status: "EXISTING" | "PLANNED" | "DONE";
  notes: string | null;
  createdByName: string | null;
  createdAt: IsoInstant;
  voidedAt: IsoInstant | null;
  voidReason: string | null;
}

export interface ClinicalNote {
  id: Uuid;
  content: string;
  professionalId: Uuid | null;
  professionalName: string | null;
  appointmentId: Uuid | null;
  amendsNoteId: Uuid | null;
  createdByName: string | null;
  createdAt: IsoInstant;
}

export interface PerformedProcedure {
  id: Uuid;
  professionalId: Uuid;
  professionalName: string;
  procedureId: Uuid | null;
  description: string;
  tooth: number | null;
  surfaces: string | null;
  treatmentItemId: Uuid | null;
  performedAt: IsoInstant;
  notes: string | null;
  voidedAt: IsoInstant | null;
  voidReason: string | null;
}

export interface ClinicalDocument {
  id: Uuid;
  professionalId: Uuid;
  professionalName: string;
  professionalCro: string | null;
  type: "PRESCRIPTION" | "CERTIFICATE" | "REFERRAL" | "INSTRUCTIONS" | "OTHER";
  title: string;
  content: string;
  issuedAt: IsoInstant;
}

export interface IssuedDocument {
  id: Uuid;
  patientId: Uuid;
  patientName: string;
  professionalName: string;
  type: ClinicalDocument["type"];
  title: string;
  issuedAt: IsoInstant;
}

export type CareCategory = "DENTAL" | "AESTHETIC" | "GENERAL";

export interface CareTemplate {
  id: Uuid;
  title: string;
  category: CareCategory;
  moment: "BEFORE" | "AFTER" | "GENERAL";
  body: string;
  active: boolean;
  updatedAt: IsoInstant;
}

export type CareTemplateInput = Omit<CareTemplate, "id" | "updatedAt">;

export interface AestheticRegion {
  region: string;
  quantity: number;
  notes?: string | null;
}

export interface AestheticRecord {
  id: Uuid;
  patientId: Uuid;
  patientName: string;
  professionalId: Uuid;
  professionalName: string;
  performedAt: IsoInstant;
  procedureType: string;
  productName: string;
  manufacturer: string | null;
  anvisaRegistry: string | null;
  batch: string;
  expiryDate: IsoDate | null;
  unit: "U" | "ML" | "UN" | "MG";
  regions: AestheticRegion[];
  totalQuantity: number;
  technique: string | null;
  notes: string | null;
  returnDate: IsoDate | null;
  consentId: Uuid | null;
  consentStatus: string | null;
  createdByName: string | null;
  voidedAt: IsoInstant | null;
  voidReason: string | null;
}

export interface AestheticInput {
  professionalId: Uuid;
  performedAt?: IsoInstant;
  procedureType: string;
  productName: string;
  manufacturer?: string | null;
  anvisaRegistry?: string | null;
  batch: string;
  expiryDate?: IsoDate | null;
  unit: string;
  regions: AestheticRegion[];
  technique?: string | null;
  notes?: string | null;
  returnDate?: IsoDate | null;
  consentId?: Uuid | null;
}

export interface Attachment {
  id: Uuid;
  category: "XRAY" | "PHOTO" | "EXAM" | "DOCUMENT" | "OTHER";
  description: string | null;
  filename: string;
  contentType: string;
  sizeBytes: number;
  uploadedByName: string | null;
  createdAt: IsoInstant;
}

export type ConsentCategory = "DENTAL" | "AESTHETIC" | "IMAGE" | "PRIVACY" | "GENERAL";

export interface ConsentTemplate {
  id: Uuid;
  familyId: Uuid;
  title: string;
  category: ConsentCategory;
  body: string;
  version: number;
  active: boolean;
  createdAt: IsoInstant;
}

export interface ConsentListItem {
  id: Uuid;
  patientId: Uuid;
  patientName: string;
  title: string;
  category: ConsentCategory;
  status: "PENDING" | "SIGNED" | "REVOKED";
  professionalName: string | null;
  procedureDescription: string | null;
  createdAt: IsoInstant;
  signedAt: IsoInstant | null;
}

export interface Consent {
  id: Uuid;
  title: string;
  category: ConsentCategory;
  body: string;
  bodySha256: string;
  templateVersion: number | null;
  procedureDescription: string | null;
  professionalName: string | null;
  status: "PENDING" | "SIGNED" | "REVOKED";
  signedAt: IsoInstant | null;
  signerName: string | null;
  signerDocument: string | null;
  signatureImage: string | null;
  signedIp: string | null;
  revokedAt: IsoInstant | null;
  revokeReason: string | null;
  createdAt: IsoInstant;
}

// ------------------------------------------------------------------ Tratamentos
export type PlanStatus = "DRAFT" | "PROPOSED" | "APPROVED" | "REJECTED" | "COMPLETED" | "CANCELLED";

export interface PlanItem {
  id: Uuid;
  procedureId: Uuid | null;
  description: string;
  tooth: number | null;
  surfaces: string | null;
  quantity: number;
  unitPrice: number;
  discount: number;
  total: number;
  status: "PLANNED" | "DONE" | "CANCELLED";
  doneAt: IsoInstant | null;
}

export interface PlanSummary {
  id: Uuid;
  patientId: Uuid;
  patientName: string;
  professionalId: Uuid;
  professionalName: string;
  title: string;
  status: PlanStatus;
  total: number;
  itemsTotal: number;
  itemsDone: number;
  createdAt: IsoInstant;
  approvedAt: IsoInstant | null;
}

export interface Plan {
  id: Uuid;
  patientId: Uuid;
  patientName: string;
  professionalId: Uuid;
  professionalName: string;
  title: string;
  status: PlanStatus;
  subtotal: number;
  itemsDiscount: number;
  discount: number;
  total: number;
  paymentConditions: string | null;
  notes: string | null;
  validUntil: IsoDate | null;
  approvedAt: IsoInstant | null;
  approvedByName: string | null;
  createdAt: IsoInstant;
  version: number;
  receivableId: Uuid | null;
  items: PlanItem[];
}

export interface PlanItemInput {
  procedureId?: Uuid | null;
  description: string;
  tooth?: number | null;
  surfaces?: string | null;
  quantity: number;
  unitPrice: number;
  discount?: number;
}

export interface PlanInput {
  patientId: Uuid;
  professionalId: Uuid;
  title: string;
  discount?: number;
  paymentConditions?: string | null;
  notes?: string | null;
  validUntil?: IsoDate | null;
  items: PlanItemInput[];
  version?: number;
}

// ------------------------------------------------------------------ Financeiro
export type PaymentMethod = "CASH" | "PIX" | "CREDIT_CARD" | "DEBIT_CARD" | "BANK_TRANSFER" | "BOLETO" | "OTHER";

export interface ReceivableSummary {
  id: Uuid;
  patientId: Uuid;
  patientName: string;
  treatmentPlanId: Uuid | null;
  description: string;
  total: number;
  paid: number;
  balance: number;
  status: "OPEN" | "PAID" | "CANCELLED";
  nextDueDate: IsoDate | null;
  overdueInstallments: number;
  createdAt: IsoInstant;
}

export interface Payment {
  id: Uuid;
  amount: number;
  refundedAmount: number;
  method: PaymentMethod;
  paidAt: IsoInstant;
  notes: string | null;
  createdByName: string | null;
}

export interface Installment {
  id: Uuid;
  number: number;
  dueDate: IsoDate;
  amount: number;
  paidAmount: number;
  balance: number;
  status: "OPEN" | "PAID" | "CANCELLED";
  overdue: boolean;
  payments: Payment[];
}

export interface Receivable {
  id: Uuid;
  patientId: Uuid;
  patientName: string;
  treatmentPlanId: Uuid | null;
  description: string;
  total: number;
  paid: number;
  balance: number;
  status: "OPEN" | "PAID" | "CANCELLED";
  cancelReason: string | null;
  createdAt: IsoInstant;
  installments: Installment[];
}

export interface InstallmentRow {
  id: Uuid;
  receivableId: Uuid;
  patientId: Uuid;
  patientName: string;
  description: string;
  number: number;
  installmentsCount: number;
  dueDate: IsoDate;
  amount: number;
  paidAmount: number;
  balance: number;
  status: "OPEN" | "PAID" | "CANCELLED";
  overdue: boolean;
}

export interface Payable {
  id: Uuid;
  supplierId: Uuid | null;
  supplierName: string | null;
  description: string;
  category: string;
  amount: number;
  dueDate: IsoDate;
  status: "OPEN" | "PAID" | "CANCELLED";
  overdue: boolean;
  paidAt: IsoInstant | null;
  paymentMethod: PaymentMethod | null;
  notes: string | null;
}

export interface Supplier {
  id: Uuid;
  name: string;
  document: string | null;
  phone: string | null;
  email: string | null;
  notes: string | null;
  active: boolean;
}

export interface FinanceSummary {
  receivedThisMonth: number;
  paidOutThisMonth: number;
  openReceivables: number;
  overdueReceivables: number;
  overdueInstallments: number;
  openPayables: number;
  payablesDueNext7Days: number;
}

export interface CashFlow {
  from: IsoDate;
  to: IsoDate;
  totalInflow: number;
  totalOutflow: number;
  net: number;
  days: { date: IsoDate; inflow: number; outflow: number; net: number }[];
  byMethod: { method: PaymentMethod; amount: number }[];
}

// ------------------------------------------------------------------ Estoque
export interface Product {
  id: Uuid;
  name: string;
  category: string | null;
  unit: string;
  minStock: number;
  currentStock: number;
  lowStock: boolean;
  supplierId: Uuid | null;
  supplierName: string | null;
  active: boolean;
  updatedAt: IsoInstant;
}

export interface StockMovement {
  id: Uuid;
  type: "IN" | "OUT" | "ADJUST";
  quantity: number;
  unitCost: number | null;
  lotNumber: string | null;
  expiryDate: IsoDate | null;
  reason: string | null;
  balanceAfter: number;
  createdByName: string | null;
  createdAt: IsoInstant;
}

export interface InventoryAlerts {
  lowStock: Product[];
  expiring: { productId: Uuid; productName: string; lotNumber: string | null; expiryDate: IsoDate }[];
}

// ------------------------------------------------------------------ Comunicação
export interface MessageTemplate {
  type: string;
  subject: string;
  body: string;
  active: boolean;
  custom: boolean;
}

export interface OutboundMessage {
  id: Uuid;
  type: string;
  toAddress: string;
  subject: string;
  status: "PENDING" | "SENT" | "FAILED";
  attempts: number;
  lastError: string | null;
  patientName: string | null;
  createdAt: IsoInstant;
  sentAt: IsoInstant | null;
}

// ------------------------------------------------------------------ Dashboard e relatórios
export interface Dashboard {
  appointmentsToday: number | null;
  confirmedToday: number | null;
  completedToday: number | null;
  activePatients: number | null;
  newPatientsThisMonth: number | null;
  receivedThisMonth: number | null;
  overdueAmount: number | null;
  lowStockProducts: number | null;
  today: {
    id: Uuid;
    startsAt: IsoInstant;
    endsAt: IsoInstant;
    patientName: string;
    patientId: Uuid;
    professionalName: string;
    professionalColor: string;
    procedureName: string | null;
    status: AppointmentStatus;
  }[];
  alerts: { type: string; severity: "info" | "warning" | "danger"; message: string; count: number }[];
}

export interface AgendaReport {
  from: IsoDate;
  to: IsoDate;
  total: number;
  byStatus: { status: AppointmentStatus; total: number }[];
  noShowRate: number;
  cancellationRate: number;
  byProfessional: {
    professionalId: Uuid;
    professionalName: string;
    appointments: number;
    completed: number;
    noShows: number;
    cancelled: number;
    bookedMinutes: number;
    availableMinutes: number;
    occupancyPercent: number;
  }[];
}

export interface ProductionReport {
  from: IsoDate;
  to: IsoDate;
  totalValue: number;
  byProfessional: { professionalId: Uuid; professionalName: string; procedures: number; value: number }[];
  topProcedures: { description: string; total: number; value: number }[];
}

export interface FinancialReport {
  from: IsoDate;
  to: IsoDate;
  months: { month: string; received: number; refunded: number; paidOut: number }[];
  overdueAging: { bucket: string; installments: number; amount: number }[];
}

export interface PatientsReport {
  active: number;
  inactive: number;
  newByMonth: { month: string; total: number }[];
  birthdaysThisMonth: { id: Uuid; fullName: string; birthDate: IsoDate; mobile: string | null }[];
}

// ------------------------------------------------------------------ Equipe, auditoria e plataforma
export interface Member {
  membershipId: Uuid;
  userId: Uuid;
  name: string;
  email: string;
  status: "ACTIVE" | "DISABLED";
  roles: string[];
}

export interface RoleInfo {
  id: Uuid;
  code: string;
  name: string;
  systemRole: boolean;
  permissions: string[];
}

export interface Invitation {
  id: Uuid;
  email: string;
  roleCode: string;
  status: string;
  expiresAt: IsoInstant;
  createdAt: IsoInstant;
}

export interface AuditEvent {
  id: Uuid;
  action: string;
  actorName: string | null;
  actorEmail: string | null;
  resourceType: string | null;
  resourceId: string | null;
  ip: string | null;
  metadata: string | null;
  occurredAt: IsoInstant;
}

export interface SessionInfo {
  id: Uuid;
  createdAt: IsoInstant;
  lastSeenAt: IsoInstant;
  ip: string | null;
  userAgent: string | null;
  current: boolean;
}

export interface Subscription {
  tenantId: Uuid;
  planCode: string;
  planName: string;
  priceMonthly: number;
  status: "TRIALING" | "ACTIVE" | "PAST_DUE" | "CANCELLED";
  trialEndsAt: IsoDate | null;
  currentPeriodEnd: IsoDate | null;
  features: string[];
  maxProfessionals: number | null;
  maxPatients: number | null;
  professionals: number;
  patients: number;
  notes: string | null;
  updatedAt: IsoInstant;
  /** Link de pagamento (PIX, boleto ou cobrança) aberto pelo botão "Pagar agora". */
  paymentUrl: string | null;
  billing: SubscriptionBilling;
}

/** OK; DUE = vencida, ainda dentro da tolerância (aviso); BLOCKED = acesso suspenso até o pagamento. */
export interface SubscriptionBilling {
  state: "OK" | "DUE" | "BLOCKED";
  dueDate: IsoDate | null;
  blockedFrom: IsoDate | null;
}

export interface PlatformClinic {
  id: Uuid;
  name: string;
  slug: string;
  status: "ACTIVE" | "SUSPENDED" | "CANCELLED";
  createdAt: IsoInstant;
  planCode: string | null;
  planName: string | null;
  subscriptionStatus: Subscription["status"] | null;
  trialEndsAt: IsoDate | null;
  priceMonthly: number | null;
}

export interface PlatformPlan {
  id: Uuid;
  code: string;
  name: string;
  priceMonthly: number;
  maxProfessionals: number | null;
  maxPatients: number | null;
  features: string[];
  active: boolean;
  tenants: number;
}

export interface PlatformMetrics {
  tenants: number;
  activeTenants: number;
  suspendedTenants: number;
  trialing: number;
  payingSubscriptions: number;
  pastDue: number;
  monthlyRecurringRevenue: number;
  users: number;
  activeSessions24h: number;
}

export interface PlatformEvent {
  id: Uuid;
  action: string;
  actorEmail: string | null;
  resourceType: string | null;
  resourceId: string | null;
  ip: string | null;
  occurredAt: IsoInstant;
}

/** Linha da planilha de importação de pacientes (valores em texto, como vieram do arquivo). */
export interface PatientImportRow {
  line: number;
  fullName?: string;
  socialName?: string;
  birthDate?: string;
  cpf?: string;
  rg?: string;
  sex?: string;
  phone?: string;
  mobile?: string;
  email?: string;
  zipCode?: string;
  street?: string;
  number?: string;
  complement?: string;
  district?: string;
  city?: string;
  state?: string;
  occupation?: string;
  referralSource?: string;
  healthInsurance?: string;
  insuranceNumber?: string;
  guardianName?: string;
  guardianCpf?: string;
  guardianPhone?: string;
  notes?: string;
  allowEmail?: string;
  allowWhatsapp?: string;
}

export interface PatientImportResult {
  total: number;
  ready: number;
  created: number;
  issues: { line: number; name: string; kind: "ERROR" | "DUPLICATE"; message: string }[];
}

/** Bloqueio de agenda. professionalId nulo = clínica inteira. */
export interface ScheduleBlock {
  id: Uuid;
  professionalId: Uuid | null;
  professionalName: string | null;
  startsAt: IsoInstant;
  endsAt: IsoInstant;
  reason: string;
  /** Atendimentos já marcados dentro do bloqueio (para remarcar). */
  appointmentsInside: number;
}

/** Dados para o recibo de um pagamento (montados a partir da conta a receber). */
export interface ReceiptData {
  id: Uuid;
  amount: number;
  refundedAmount: number;
  method: PaymentMethod;
  paidAt: IsoInstant;
  notes: string | null;
  createdByName: string | null;
  /** Descrição da conta (ex.: orçamento aprovado). */
  description: string;
  /** "2/3" quando parcelado. */
  installment: string | null;
}

export type WaitlistStatus = "WAITING" | "SCHEDULED" | "REMOVED";

/** Paciente na lista de espera (encaixe). */
export interface WaitlistEntry {
  id: Uuid;
  patientId: Uuid;
  patientName: string;
  patientMobile: string | null;
  patientAllowWhatsapp: boolean;
  professionalId: Uuid | null;
  professionalName: string | null;
  procedureId: Uuid | null;
  procedureName: string | null;
  preference: string | null;
  notes: string | null;
  urgent: boolean;
  status: WaitlistStatus;
  appointmentId: Uuid | null;
  createdAt: IsoInstant;
  resolvedAt: IsoInstant | null;
}

export interface WaitlistInput {
  patientId: Uuid;
  professionalId: Uuid | null;
  procedureId: Uuid | null;
  preference: string | null;
  notes: string | null;
  urgent: boolean;
}
