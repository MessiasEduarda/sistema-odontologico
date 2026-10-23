import { apiFetch, apiPost } from "@/lib/api/client";
import type {
  Attachment,
  ClinicalDocument,
  ClinicSettings,
  Consent,
  IsoInstant,
  Patient,
  Plan,
  Receivable,
  Uuid,
} from "@/lib/api/types";

export interface PublicAppointment {
  clinicName: string;
  patientFirstName: string;
  professionalName: string;
  startsAt: IsoInstant;
  endsAt: IsoInstant;
  status: string;
  canRespond: boolean;
}

export interface PublicConsent {
  clinicName: string;
  patientName: string;
  title: string;
  body: string;
  status: string;
  signedAt: IsoInstant | null;
}

export interface SignatureInput {
  signerName: string;
  signerDocument?: string;
  signatureImage: string;
  accepted: boolean;
}

export interface PortalContact {
  fullName: string;
  email: string | null;
  mobile: string | null;
  phone: string | null;
  allowEmail: boolean;
  allowSms: boolean;
  allowWhatsapp: boolean;
}

export interface PortalOverview {
  clinicName: string;
  clinicPhone: string | null;
  patient: PortalContact;
  upcoming: {
    id: Uuid;
    startsAt: IsoInstant;
    endsAt: IsoInstant;
    professionalName: string;
    status: string;
    canRespond: boolean;
  }[];
  pendingConsents: { id: Uuid; title: string; body: string; status: string; createdAt: IsoInstant }[];
}

export type PortalClinic = Pick<
  ClinicSettings,
  "name" | "legalName" | "document" | "phone" | "email" | "zipCode" | "address" | "city" | "state"
>;

export type PortalPatient = Pick<
  Patient,
  "fullName" | "socialName" | "cpf" | "birthDate" | "mobile" | "phone" | "guardianName" | "guardianCpf"
>;

export interface PortalRecords {
  clinic: PortalClinic;
  patient: PortalPatient;
  documents: ClinicalDocument[];
  consents: Consent[];
  plans: Plan[];
  receivables: Receivable[];
  attachments: Attachment[];
}

export interface PortalProfile {
  tenantId: Uuid;
  patientId: Uuid;
  clinicName: string;
  patientName: string;
}

export interface PortalMe {
  email: string;
  profiles: PortalProfile[];
  activeTenantId: Uuid | null;
  activePatientId: Uuid | null;
}

export interface PortalLoginResult {
  status: "AUTHENTICATED" | "CODE_REQUIRED";
  challengeId: Uuid | null;
  maskedEmail: string | null;
  me: PortalMe | null;
}

export interface PortalActivation {
  firstName: string;
  clinicName: string;
  email: string;
}

export type PatientAction = "CONFIRM" | "CANCEL";

export const publicApi = {
  appointment: (token: string) => apiPost<PublicAppointment>("/api/v1/public/appointments/lookup", { token }),
  respondAppointment: (token: string, action: PatientAction) =>
    apiPost<PublicAppointment>("/api/v1/public/appointments/respond", { token, action }),
  consent: (token: string) => apiPost<PublicConsent>("/api/v1/public/consents/lookup", { token }),
  signConsent: (token: string, body: SignatureInput) =>
    apiPost<PublicConsent>("/api/v1/public/consents/sign", { token, ...body }),
  portal: () => apiFetch<PortalOverview>("/api/v1/public/portal/overview"),
  portalRespond: (appointmentId: Uuid, action: PatientAction) =>
    apiPost<PortalOverview>("/api/v1/public/portal/appointments/respond", { appointmentId, action }),
  portalSign: (consentId: Uuid, body: SignatureInput) =>
    apiPost<PortalOverview>("/api/v1/public/portal/consents/sign", { consentId, ...body }),
  portalContact: (body: Pick<PortalContact, "mobile" | "phone" | "allowEmail" | "allowSms" | "allowWhatsapp">) =>
    apiPost<PortalOverview>("/api/v1/public/portal/contact", body),
  portalRecords: () => apiFetch<PortalRecords>("/api/v1/public/portal/records"),
  portalAttachmentUrl: (attachmentId: Uuid) => `/api/v1/public/portal/attachments/${attachmentId}/download`,
  portalMe: () => apiFetch<PortalMe>("/api/v1/public/portal/me"),
  portalSelectProfile: (tenantId: Uuid, patientId: Uuid) =>
    apiPost<PortalMe>("/api/v1/public/portal/profile", { tenantId, patientId }),
  portalLogin: (email: string, password: string) =>
    apiPost<PortalLoginResult>("/api/v1/public/portal/login", { email, password }),
  portalVerifyCode: (challengeId: Uuid, code: string) =>
    apiPost<PortalLoginResult>("/api/v1/public/portal/login/verify", { challengeId, code }),
  portalLogout: () => apiPost<void>("/api/v1/public/portal/logout"),
  portalActivationLookup: (token: string) =>
    apiPost<PortalActivation>("/api/v1/public/portal/activation/lookup", { token }),
  portalActivate: (body: { token: string; birthDate: string; password: string; acceptedTerms: boolean }) =>
    apiPost<PortalMe>("/api/v1/public/portal/activation", body),
  portalForgotPassword: (email: string) => apiPost<void>("/api/v1/public/portal/password/forgot", { email }),
  portalResetPassword: (token: string, password: string) =>
    apiPost<void>("/api/v1/public/portal/password/reset", { token, password }),
};
