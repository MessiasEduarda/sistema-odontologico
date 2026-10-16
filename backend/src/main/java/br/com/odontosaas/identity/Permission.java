package br.com.odontosaas.identity;

import java.util.Arrays;
import java.util.Optional;

/**
 * Permissões granulares por recurso e ação. O código ({@code recurso:ação}) é gravado no banco e usado como
 * authority do Spring Security, por exemplo {@code @PreAuthorize("hasAuthority('users:manage')")}.
 * Matriz por papel: docs/seguranca/matriz-permissoes.md.
 */
public enum Permission {
    CLINIC_SETTINGS_MANAGE("clinic:settings:manage"),
    USERS_READ("users:read"),
    USERS_MANAGE("users:manage"),
    AUDIT_READ("audit:read"),
    PATIENTS_READ("patients:read"),
    PATIENTS_WRITE("patients:write"),
    SCHEDULE_READ("schedule:read"),
    SCHEDULE_WRITE("schedule:write"),
    CLINICAL_RECORDS_READ("clinical_records:read"),
    CLINICAL_RECORDS_WRITE("clinical_records:write"),
    TREATMENTS_READ("treatments:read"),
    TREATMENTS_WRITE("treatments:write"),
    FINANCE_READ("finance:read"),
    FINANCE_WRITE("finance:write"),
    INVENTORY_READ("inventory:read"),
    INVENTORY_WRITE("inventory:write"),
    REPORTS_READ("reports:read");

    private final String code;

    Permission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<Permission> fromCode(String code) {
        return Arrays.stream(values()).filter(p -> p.code.equals(code)).findFirst();
    }
}
