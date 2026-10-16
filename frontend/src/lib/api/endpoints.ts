import { apiDelete, apiFetch, apiPost, apiPut } from "./client";
import type {
  AestheticInput,
  AestheticRecord,
  AgendaReport,
  Anamnesis,
  AnamnesisForm,
  Answer,
  Appointment,
  AppointmentDetail,
  AppointmentInput,
  Attachment,
  AuditEvent,
  CareTemplate,
  CareTemplateInput,
  CashFlow,
  ChartEntry,
  ClinicalDocument,
  ClinicalNote,
  ClinicSettings,
  Consent,
  ConsentCategory,
  ConsentListItem,
  ConsentTemplate,
  Dashboard,
  FinanceSummary,
  FinancialReport,
  InstallmentRow,
  InventoryAlerts,
  IssuedDocument,
  Invitation,
  Member,
  MessageTemplate,
  OutboundMessage,
  Page,
  Patient,
  PatientInput,
  PatientsReport,
  PatientSummary,
  Payable,
  PaymentMethod,
  PerformedProcedure,
  Plan,
  PlanInput,
  PlanSummary,
  PlatformClinic,
  PlatformEvent,
  PlatformMetrics,
  PlatformPlan,
  Procedure,
  ProductionReport,
  Product,
  Professional,
  Question,
  Receivable,
  ReceivableSummary,
  RecentAnamnesis,
  RecordSummary,
  RoleInfo,
  Room,
  ScheduleSlot,
  SessionInfo,
  StockMovement,
  Subscription,
  Supplier,
  Uuid,
} from "./types";

/** Monta query string ignorando valores vazios. */
export function qs(params: Record<string, string | number | boolean | null | undefined>): string {
  const entries = Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== "");
  if (entries.length === 0) return "";
  return "?" + new URLSearchParams(entries.map(([k, v]) => [k, String(v)])).toString();
}

export const api = {
  dashboard: () => apiFetch<Dashboard>("/api/v1/dashboard"),

  clinic: {
    settings: () => apiFetch<ClinicSettings>("/api/v1/clinic/settings"),
    saveSettings: (body: Omit<ClinicSettings, "timeZone" | "logoUpdatedAt">) =>
      apiPut<ClinicSettings>("/api/v1/clinic/settings", body),
    /** URL da logo; `version` (logoUpdatedAt) renova o cache quando a logo muda. */
    logoUrl: (version: string) => `/api/v1/clinic/logo?v=${encodeURIComponent(version)}`,
    uploadLogo: (file: File) => {
      const form = new FormData();
      form.append("file", file);
      return apiFetch<{ logoUpdatedAt: string }>("/api/v1/clinic/logo", { method: "PUT", body: form });
    },
    deleteLogo: () => apiDelete<void>("/api/v1/clinic/logo"),
    subscription: () => apiFetch<Subscription>("/api/v1/clinic/subscription"),
    professionals: (active?: boolean) => apiFetch<Professional[]>(`/api/v1/clinic/professionals${qs({ active })}`),
    createProfessional: (body: Partial<Professional>) => apiPost<Professional>("/api/v1/clinic/professionals", body),
    updateProfessional: (id: Uuid, body: Partial<Professional>) =>
      apiPut<Professional>(`/api/v1/clinic/professionals/${id}`, body),
    schedule: (id: Uuid) => apiFetch<ScheduleSlot[]>(`/api/v1/clinic/professionals/${id}/schedule`),
    saveSchedule: (id: Uuid, slots: ScheduleSlot[]) =>
      apiPut<ScheduleSlot[]>(`/api/v1/clinic/professionals/${id}/schedule`, { slots }),
    rooms: () => apiFetch<Room[]>("/api/v1/clinic/rooms"),
    createRoom: (body: { name: string; active: boolean }) => apiPost<Room>("/api/v1/clinic/rooms", body),
    updateRoom: (id: Uuid, body: { name: string; active: boolean }) => apiPut<Room>(`/api/v1/clinic/rooms/${id}`, body),
    procedures: (q?: string, active?: boolean) =>
      apiFetch<Procedure[]>(`/api/v1/clinic/procedures${qs({ q, active })}`),
    createProcedure: (body: Omit<Procedure, "id">) => apiPost<Procedure>("/api/v1/clinic/procedures", body),
    updateProcedure: (id: Uuid, body: Omit<Procedure, "id">) =>
      apiPut<Procedure>(`/api/v1/clinic/procedures/${id}`, body),
    anamnesisForms: (includeInactive = false) =>
      apiFetch<AnamnesisForm[]>(`/api/v1/clinic/anamnesis-forms${qs({ includeInactive })}`),
    createAnamnesisForm: (body: { name: string; kind: string; questions: Question[] }) =>
      apiPost<AnamnesisForm>("/api/v1/clinic/anamnesis-forms", body),
    updateAnamnesisForm: (id: Uuid, body: { name: string; kind: string; questions: Question[] }) =>
      apiPut<AnamnesisForm>(`/api/v1/clinic/anamnesis-forms/${id}`, body),
    setAnamnesisFormActive: (id: Uuid, active: boolean) =>
      apiPost<AnamnesisForm>(`/api/v1/clinic/anamnesis-forms/${id}/active`, { active }),
    consentTemplates: (allVersions = false) =>
      apiFetch<ConsentTemplate[]>(`/api/v1/clinic/consent-templates${qs({ allVersions })}`),
    createConsentTemplate: (body: { title: string; category: ConsentCategory; body: string }) =>
      apiPost<ConsentTemplate>("/api/v1/clinic/consent-templates", body),
    updateConsentTemplate: (id: Uuid, body: { title: string; category: ConsentCategory; body: string }) =>
      apiPut<ConsentTemplate>(`/api/v1/clinic/consent-templates/${id}`, body),
    addConsentExamples: (categories?: ConsentCategory[]) =>
      apiPost<{ created: number }>("/api/v1/clinic/consent-templates/examples", { categories }),
    careTemplates: (includeInactive = false) =>
      apiFetch<CareTemplate[]>(`/api/v1/clinic/care-templates${qs({ includeInactive })}`),
    createCareTemplate: (body: CareTemplateInput) => apiPost<CareTemplate>("/api/v1/clinic/care-templates", body),
    updateCareTemplate: (id: Uuid, body: CareTemplateInput) =>
      apiPut<CareTemplate>(`/api/v1/clinic/care-templates/${id}`, body),
    archiveConsentTemplate: (id: Uuid) => apiPost<void>(`/api/v1/clinic/consent-templates/${id}/archive`),
    members: () => apiFetch<Member[]>("/api/v1/clinic/members"),
    updateMemberRoles: (id: Uuid, roleCodes: string[]) =>
      apiPut<Member>(`/api/v1/clinic/members/${id}/roles`, { roleCodes }),
    deactivateMember: (id: Uuid) => apiPost<void>(`/api/v1/clinic/members/${id}/deactivate`),
    roles: () => apiFetch<RoleInfo[]>("/api/v1/clinic/roles"),
    invitations: () => apiFetch<Invitation[]>("/api/v1/clinic/invitations"),
    invite: (email: string, roleCode: string) => apiPost<Invitation>("/api/v1/clinic/invitations", { email, roleCode }),
    revokeInvitation: (id: Uuid) => apiDelete<void>(`/api/v1/clinic/invitations/${id}`),
    audit: (params: { from?: string; to?: string; action?: string; resourceType?: string }) =>
      apiFetch<AuditEvent[]>(`/api/v1/clinic/audit${qs(params)}`),
  },

  patients: {
    search: (params: { q?: string; active?: boolean; page?: number; size?: number }) =>
      apiFetch<Page<PatientSummary>>(`/api/v1/patients${qs(params)}`),
    get: (id: Uuid) => apiFetch<Patient>(`/api/v1/patients/${id}`),
    create: (body: PatientInput) => apiPost<Patient>("/api/v1/patients", body),
    update: (id: Uuid, body: PatientInput) => apiPut<Patient>(`/api/v1/patients/${id}`, body),
    setActive: (id: Uuid, active: boolean) =>
      apiPost<Patient>(`/api/v1/patients/${id}/${active ? "activate" : "deactivate"}`),
    portalAccess: (id: Uuid, sendEmail: boolean) =>
      apiPost<{ url: string; expiresAt: string; emailQueued: boolean }>(`/api/v1/patients/${id}/portal-access`, {
        sendEmail,
      }),
    revokePortal: (id: Uuid) => apiPost<void>(`/api/v1/patients/${id}/portal-access/revoke`),
  },

  appointments: {
    list: (params: { from?: string; to?: string; professionalId?: string; patientId?: string; status?: string }) =>
      apiFetch<Appointment[]>(`/api/v1/appointments${qs(params)}`),
    detail: (id: Uuid) => apiFetch<AppointmentDetail>(`/api/v1/appointments/${id}`),
    create: (body: AppointmentInput) => apiPost<Appointment>("/api/v1/appointments", body),
    update: (id: Uuid, body: AppointmentInput) => apiPut<Appointment>(`/api/v1/appointments/${id}`, body),
    status: (id: Uuid, status: string, reason?: string) =>
      apiPost<Appointment>(`/api/v1/appointments/${id}/status`, { status, reason }),
    availability: (professionalId: Uuid, date: string, durationMinutes: number) =>
      apiFetch<{ startsAt: string; endsAt: string }[]>(
        `/api/v1/appointments/availability${qs({ professionalId, date, durationMinutes })}`,
      ),
  },

  clinical: {
    summary: (patientId: Uuid) => apiFetch<RecordSummary>(`/api/v1/patients/${patientId}/record`),
    anamneses: (patientId: Uuid) => apiFetch<Anamnesis[]>(`/api/v1/patients/${patientId}/anamneses`),
    fillAnamnesis: (
      patientId: Uuid,
      body: { formId?: Uuid | null; answers: Record<string, Answer>; notes?: string; filledBy?: string },
    ) => apiPost<Anamnesis>(`/api/v1/patients/${patientId}/anamneses`, body),
    recentAnamneses: (params: { kind?: string; alertsOnly?: boolean; q?: string }) =>
      apiFetch<RecentAnamnesis[]>(`/api/v1/anamneses${qs(params)}`),
    allConsents: (params: { status?: string; category?: string; q?: string }) =>
      apiFetch<ConsentListItem[]>(`/api/v1/consents${qs(params)}`),
    recentDocuments: (type?: string) => apiFetch<IssuedDocument[]>(`/api/v1/clinical-documents${qs({ type })}`),
    issueCare: (patientId: Uuid, body: { professionalId: Uuid; title: string; content: string; sendEmail: boolean }) =>
      apiPost<ClinicalDocument>(`/api/v1/patients/${patientId}/care-instructions`, body),
    aesthetics: (patientId: Uuid) => apiFetch<AestheticRecord[]>(`/api/v1/patients/${patientId}/aesthetic-procedures`),
    addAesthetic: (patientId: Uuid, body: AestheticInput) =>
      apiPost<AestheticRecord>(`/api/v1/patients/${patientId}/aesthetic-procedures`, body),
    voidAesthetic: (patientId: Uuid, id: Uuid, reason: string) =>
      apiPost<void>(`/api/v1/patients/${patientId}/aesthetic-procedures/${id}/void`, { reason }),
    searchAesthetics: (params: { batch?: string; q?: string; returnsUntil?: string }) =>
      apiFetch<AestheticRecord[]>(`/api/v1/aesthetic-procedures${qs(params)}`),
    chart: (patientId: Uuid) => apiFetch<ChartEntry[]>(`/api/v1/patients/${patientId}/dental-chart`),
    addChart: (
      patientId: Uuid,
      body: { tooth: number; surfaces?: string | null; condition: string; status: string; notes?: string },
    ) => apiPost<ChartEntry>(`/api/v1/patients/${patientId}/dental-chart`, body),
    voidChart: (patientId: Uuid, entryId: Uuid, reason: string) =>
      apiPost<void>(`/api/v1/patients/${patientId}/dental-chart/${entryId}/void`, { reason }),
    notes: (patientId: Uuid) => apiFetch<ClinicalNote[]>(`/api/v1/patients/${patientId}/notes`),
    addNote: (patientId: Uuid, body: { content: string; professionalId?: Uuid | null; amendsNoteId?: Uuid | null }) =>
      apiPost<ClinicalNote>(`/api/v1/patients/${patientId}/notes`, body),
    performed: (patientId: Uuid) =>
      apiFetch<PerformedProcedure[]>(`/api/v1/patients/${patientId}/performed-procedures`),
    addPerformed: (
      patientId: Uuid,
      body: {
        professionalId: Uuid;
        procedureId?: Uuid | null;
        description: string;
        tooth?: number | null;
        surfaces?: string | null;
        treatmentItemId?: Uuid | null;
        notes?: string;
      },
    ) => apiPost<PerformedProcedure>(`/api/v1/patients/${patientId}/performed-procedures`, body),
    voidPerformed: (patientId: Uuid, id: Uuid, reason: string) =>
      apiPost<void>(`/api/v1/patients/${patientId}/performed-procedures/${id}/void`, { reason }),
    documents: (patientId: Uuid) => apiFetch<ClinicalDocument[]>(`/api/v1/patients/${patientId}/documents`),
    addDocument: (patientId: Uuid, body: { professionalId: Uuid; type: string; title: string; content: string }) =>
      apiPost<ClinicalDocument>(`/api/v1/patients/${patientId}/documents`, body),
    attachments: (patientId: Uuid) => apiFetch<Attachment[]>(`/api/v1/patients/${patientId}/attachments`),
    attachmentUrl: (patientId: Uuid, id: Uuid) => `/api/v1/patients/${patientId}/attachments/${id}/download`,
    deleteAttachment: (patientId: Uuid, id: Uuid) => apiDelete<void>(`/api/v1/patients/${patientId}/attachments/${id}`),
    consents: (patientId: Uuid) => apiFetch<Consent[]>(`/api/v1/patients/${patientId}/consents`),
    consent: (patientId: Uuid, id: Uuid) => apiFetch<Consent>(`/api/v1/patients/${patientId}/consents/${id}`),
    issueConsent: (
      patientId: Uuid,
      body: { templateId: Uuid; professionalId?: Uuid | null; procedureDescription?: string },
    ) => apiPost<Consent>(`/api/v1/patients/${patientId}/consents`, body),
    signConsent: (
      patientId: Uuid,
      id: Uuid,
      body: { signerName: string; signerDocument?: string; signatureImage: string; accepted: boolean },
    ) => apiPost<Consent>(`/api/v1/patients/${patientId}/consents/${id}/sign`, body),
    consentLink: (patientId: Uuid, id: Uuid, sendEmail: boolean) =>
      apiPost<{ url: string; expiresAt: string }>(`/api/v1/patients/${patientId}/consents/${id}/link`, { sendEmail }),
    revokeConsent: (patientId: Uuid, id: Uuid, reason: string) =>
      apiPost<Consent>(`/api/v1/patients/${patientId}/consents/${id}/revoke`, { reason }),
  },

  plans: {
    list: (params: { patientId?: Uuid; status?: string }) =>
      apiFetch<PlanSummary[]>(`/api/v1/treatment-plans${qs(params)}`),
    get: (id: Uuid) => apiFetch<Plan>(`/api/v1/treatment-plans/${id}`),
    create: (body: PlanInput) => apiPost<Plan>("/api/v1/treatment-plans", body),
    update: (id: Uuid, body: PlanInput) => apiPut<Plan>(`/api/v1/treatment-plans/${id}`, body),
    propose: (id: Uuid) => apiPost<Plan>(`/api/v1/treatment-plans/${id}/propose`),
    approve: (
      id: Uuid,
      body: { approvedByName: string; generateReceivable: boolean; installments: number; firstDueDate?: string },
    ) => apiPost<Plan>(`/api/v1/treatment-plans/${id}/approve`, body),
    reject: (id: Uuid, reason: string) => apiPost<Plan>(`/api/v1/treatment-plans/${id}/reject`, { reason }),
    cancel: (id: Uuid, reason: string) => apiPost<Plan>(`/api/v1/treatment-plans/${id}/cancel`, { reason }),
  },

  finance: {
    summary: () => apiFetch<FinanceSummary>("/api/v1/finance/summary"),
    cashFlow: (from: string, to: string) => apiFetch<CashFlow>(`/api/v1/finance/cash-flow${qs({ from, to })}`),
    receivables: (params: { status?: string; patientId?: Uuid; q?: string }) =>
      apiFetch<ReceivableSummary[]>(`/api/v1/finance/receivables${qs(params)}`),
    receivable: (id: Uuid) => apiFetch<Receivable>(`/api/v1/finance/receivables/${id}`),
    createReceivable: (body: {
      patientId: Uuid;
      description: string;
      total: number;
      installments: number;
      firstDueDate: string;
    }) => apiPost<Receivable>("/api/v1/finance/receivables", body),
    cancelReceivable: (id: Uuid, reason: string) =>
      apiPost<Receivable>(`/api/v1/finance/receivables/${id}/cancel`, { reason }),
    installments: (params: { from?: string; to?: string; status?: string }) =>
      apiFetch<InstallmentRow[]>(`/api/v1/finance/installments${qs(params)}`),
    pay: (installmentId: Uuid, body: { amount: number; method: PaymentMethod; paidAt?: string; notes?: string }) =>
      apiPost<Receivable>(`/api/v1/finance/installments/${installmentId}/payments`, body),
    refund: (paymentId: Uuid, amount: number, reason: string) =>
      apiPost<Receivable>(`/api/v1/finance/payments/${paymentId}/refunds`, { amount, reason }),
    payables: (params: { status?: string; from?: string; to?: string }) =>
      apiFetch<Payable[]>(`/api/v1/finance/payables${qs(params)}`),
    createPayable: (body: Partial<Payable>) => apiPost<Payable>("/api/v1/finance/payables", body),
    updatePayable: (id: Uuid, body: Partial<Payable>) => apiPut<Payable>(`/api/v1/finance/payables/${id}`, body),
    payPayable: (id: Uuid, method: PaymentMethod) => apiPost<Payable>(`/api/v1/finance/payables/${id}/pay`, { method }),
    cancelPayable: (id: Uuid, reason: string) => apiPost<Payable>(`/api/v1/finance/payables/${id}/cancel`, { reason }),
    suppliers: () => apiFetch<Supplier[]>("/api/v1/suppliers"),
    createSupplier: (body: Omit<Supplier, "id">) => apiPost<Supplier>("/api/v1/suppliers", body),
    updateSupplier: (id: Uuid, body: Omit<Supplier, "id">) => apiPut<Supplier>(`/api/v1/suppliers/${id}`, body),
  },

  inventory: {
    products: (q?: string, lowStock?: boolean) =>
      apiFetch<Product[]>(`/api/v1/inventory/products${qs({ q, lowStock })}`),
    create: (body: Partial<Product>) => apiPost<Product>("/api/v1/inventory/products", body),
    update: (id: Uuid, body: Partial<Product>) => apiPut<Product>(`/api/v1/inventory/products/${id}`, body),
    movements: (id: Uuid) => apiFetch<StockMovement[]>(`/api/v1/inventory/products/${id}/movements`),
    move: (
      id: Uuid,
      body: {
        type: string;
        quantity: number;
        unitCost?: number | null;
        lotNumber?: string;
        expiryDate?: string;
        reason?: string;
      },
    ) => apiPost<Product>(`/api/v1/inventory/products/${id}/movements`, body),
    alerts: () => apiFetch<InventoryAlerts>("/api/v1/inventory/alerts"),
  },

  communication: {
    templates: () => apiFetch<MessageTemplate[]>("/api/v1/communication/templates"),
    saveTemplate: (type: string, body: { subject: string; body: string; active: boolean }) =>
      apiPut<MessageTemplate>(`/api/v1/communication/templates/${type}`, body),
    messages: (status?: string) => apiFetch<OutboundMessage[]>(`/api/v1/communication/messages${qs({ status })}`),
    retry: (id: Uuid) => apiPost<void>(`/api/v1/communication/messages/${id}/retry`),
  },

  reports: {
    agenda: (from: string, to: string) => apiFetch<AgendaReport>(`/api/v1/reports/agenda${qs({ from, to })}`),
    production: (from: string, to: string) =>
      apiFetch<ProductionReport>(`/api/v1/reports/production${qs({ from, to })}`),
    financial: (from: string, to: string) => apiFetch<FinancialReport>(`/api/v1/reports/financial${qs({ from, to })}`),
    patients: () => apiFetch<PatientsReport>("/api/v1/reports/patients"),
    exportUrl: (type: string, from: string, to: string) => `/api/v1/reports/${type}/export${qs({ from, to })}`,
  },

  account: {
    sessions: () => apiFetch<SessionInfo[]>("/api/v1/auth/sessions"),
    revokeSession: (id: Uuid) => apiDelete<void>(`/api/v1/auth/sessions/${id}`),
    revokeOthers: () => apiPost<void>("/api/v1/auth/sessions/revoke-others"),
    changePassword: (currentPassword: string, newPassword: string) =>
      apiPost<void>("/api/v1/auth/password/change", { currentPassword, newPassword }),
  },

  platform: {
    metrics: () => apiFetch<PlatformMetrics>("/api/v1/platform/metrics"),
    clinics: (q?: string) => apiFetch<PlatformClinic[]>(`/api/v1/platform/clinics${qs({ q })}`),
    createClinic: (body: { name: string; slug: string; ownerEmail: string; planCode?: string }) =>
      apiPost<unknown>("/api/v1/platform/tenants", body),
    resendOwnerInvite: (id: Uuid, ownerEmail: string) =>
      apiPost<void>(`/api/v1/platform/tenants/${id}/owner-invitation`, { ownerEmail }),
    setClinicStatus: (id: Uuid, status: string) => apiPost<void>(`/api/v1/platform/clinics/${id}/status`, { status }),
    subscription: (id: Uuid) => apiFetch<Subscription>(`/api/v1/platform/clinics/${id}/subscription`),
    updateSubscription: (
      id: Uuid,
      body: {
        planCode: string;
        status: string;
        trialEndsAt?: string | null;
        currentPeriodEnd?: string | null;
        notes?: string;
      },
    ) => apiPut<Subscription>(`/api/v1/platform/clinics/${id}/subscription`, body),
    plans: () => apiFetch<PlatformPlan[]>("/api/v1/platform/plans"),
    createPlan: (body: Omit<PlatformPlan, "id" | "tenants">) => apiPost<PlatformPlan>("/api/v1/platform/plans", body),
    updatePlan: (id: Uuid, body: Omit<PlatformPlan, "id" | "tenants">) =>
      apiPut<PlatformPlan>(`/api/v1/platform/plans/${id}`, body),
    audit: (action?: string) => apiFetch<PlatformEvent[]>(`/api/v1/platform/audit${qs({ action })}`),
  },
};
