import { apiFetch, apiPost } from "@/lib/api/client";

export interface ClinicSummary {
  id: string;
  name: string;
  slug: string;
}

export interface Me {
  user: { id: string; name: string; email: string };
  platformAdmin: boolean;
  activeClinic: ClinicSummary | null;
  permissions: string[];
  clinics: ClinicSummary[];
}

export interface InvitationPreview {
  clinicName: string;
  email: string;
  roleName: string;
  existingAccount: boolean;
}

export const authApi = {
  me: () => apiFetch<Me>("/api/v1/auth/me"),
  login: (email: string, password: string) => apiPost<Me>("/api/v1/auth/login", { email, password }),
  logout: () => apiPost<void>("/api/v1/auth/logout"),
  switchClinic: (clinicId: string) => apiPost<Me>("/api/v1/auth/switch-clinic", { clinicId }),
  forgotPassword: (email: string) => apiPost<void>("/api/v1/auth/password/forgot", { email }),
  resetPassword: (token: string, newPassword: string) =>
    apiPost<void>("/api/v1/auth/password/reset", { token, newPassword }),
  previewInvitation: (token: string) => apiPost<InvitationPreview>("/api/v1/public/invitations/preview", { token }),
  acceptInvitation: (token: string, password: string, name?: string) =>
    apiPost<void>("/api/v1/public/invitations/accept", { token, password, name }),
};
