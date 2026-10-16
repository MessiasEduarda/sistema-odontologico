/** Códigos de permissão (espelham o enum Permission do back-end). A autorização real é feita na API. */
export const P = {
  CLINIC_SETTINGS_MANAGE: "clinic:settings:manage",
  USERS_READ: "users:read",
  USERS_MANAGE: "users:manage",
  AUDIT_READ: "audit:read",
  PATIENTS_READ: "patients:read",
  PATIENTS_WRITE: "patients:write",
  SCHEDULE_READ: "schedule:read",
  SCHEDULE_WRITE: "schedule:write",
  CLINICAL_READ: "clinical_records:read",
  CLINICAL_WRITE: "clinical_records:write",
  TREATMENTS_READ: "treatments:read",
  TREATMENTS_WRITE: "treatments:write",
  FINANCE_READ: "finance:read",
  FINANCE_WRITE: "finance:write",
  INVENTORY_READ: "inventory:read",
  INVENTORY_WRITE: "inventory:write",
  REPORTS_READ: "reports:read",
} as const;

export type PermissionCode = (typeof P)[keyof typeof P];
