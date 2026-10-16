import { apiPost } from "@/lib/api/client";
import type { IsoInstant, Uuid } from "@/lib/api/types";

/** Contratos das páginas públicas do paciente (acesso pelo token do link, sem sessão). */

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

export type PatientAction = "CONFIRM" | "CANCEL";

export const publicApi = {
  appointment: (token: string) => apiPost<PublicAppointment>("/api/v1/public/appointments/lookup", { token }),
  respondAppointment: (token: string, action: PatientAction) =>
    apiPost<PublicAppointment>("/api/v1/public/appointments/respond", { token, action }),
  consent: (token: string) => apiPost<PublicConsent>("/api/v1/public/consents/lookup", { token }),
  signConsent: (token: string, body: SignatureInput) =>
    apiPost<PublicConsent>("/api/v1/public/consents/sign", { token, ...body }),
  portal: (token: string) => apiPost<PortalOverview>("/api/v1/public/portal/overview", { token }),
  portalRespond: (token: string, appointmentId: Uuid, action: PatientAction) =>
    apiPost<PortalOverview>("/api/v1/public/portal/appointments/respond", { token, appointmentId, action }),
  portalSign: (token: string, consentId: Uuid, body: SignatureInput) =>
    apiPost<PortalOverview>("/api/v1/public/portal/consents/sign", { token, consentId, ...body }),
  portalContact: (token: string, body: Omit<PortalContact, "fullName">) =>
    apiPost<PortalOverview>("/api/v1/public/portal/contact", { token, ...body }),
};
