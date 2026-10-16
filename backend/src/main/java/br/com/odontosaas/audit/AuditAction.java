package br.com.odontosaas.audit;

/** Ações auditadas. Novos valores podem ser adicionados; nunca renomeie um existente (histórico). */
public enum AuditAction {
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    LOGIN_BLOCKED,
    LOGOUT,
    SESSION_REVOKED,
    TENANT_SWITCHED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,
    PASSWORD_CHANGED,
    INVITATION_CREATED,
    INVITATION_REVOKED,
    INVITATION_ACCEPTED,
    MEMBER_ROLES_CHANGED,
    MEMBER_DEACTIVATED,
    TENANT_CREATED,
    PLATFORM_ADMIN_BOOTSTRAPPED,
    /* Genéricos para registros de negócio (o tipo vai em resource_type). */
    RECORD_CREATED,
    RECORD_UPDATED,
    RECORD_DEACTIVATED,
    RECORD_CANCELLED,
    RECORD_VOIDED,
    /* Acesso a dados sensíveis. */
    CLINICAL_RECORD_VIEWED,
    ATTACHMENT_DOWNLOADED,
    REPORT_EXPORTED,
    /* Termos, portal e financeiro. */
    CONSENT_SIGNED,
    CONSENT_REVOKED,
    PORTAL_ACCESS_GRANTED,
    PORTAL_ACCESSED,
    PAYMENT_RECEIVED,
    PAYMENT_REFUNDED,
    SUBSCRIPTION_CHANGED,
    TENANT_STATUS_CHANGED,
    /* Aceite dos Termos de uso e da Política de privacidade. */
    TERMS_ACCEPTED
}
