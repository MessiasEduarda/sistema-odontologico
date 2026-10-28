-- V17: central de suporte. A clínica abre chamados para a equipe da plataforma; a conversa, os anexos e as notas
-- internas ficam por clínica (RLS), e o administrador da plataforma enxerga todos. Respostas prontas e a fila de
-- e-mails do suporte são globais.

CREATE SEQUENCE support_protocol_seq;

CREATE TABLE support_tickets (
    id                     uuid PRIMARY KEY,
    tenant_id              uuid         NOT NULL REFERENCES tenants (id),
    protocol               varchar(20)  NOT NULL UNIQUE,
    opened_by              uuid         NOT NULL REFERENCES users (id),
    subject                varchar(160) NOT NULL,
    category               varchar(20)  NOT NULL
        CHECK (category IN ('BUG', 'HOW_TO', 'ACCESS', 'BILLING', 'SUGGESTION', 'DATA_PRIVACY')),
    priority               varchar(10)  NOT NULL CHECK (priority IN ('URGENT', 'HIGH', 'NORMAL', 'LOW')),
    status                 varchar(20)  NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'WAITING_CLINIC', 'RESOLVED', 'CLOSED')),
    page                   varchar(300),
    trace_id               varchar(64),
    error_message          varchar(500),
    user_agent             varchar(300),
    related_ticket_id      uuid,
    first_response_due_at  timestamptz  NOT NULL,
    first_response_at      timestamptz,
    sla_breach_notified_at timestamptz,
    waiting_since          timestamptz,
    reminded_at            timestamptz,
    resolved_at            timestamptz,
    auto_resolved          boolean      NOT NULL DEFAULT false,
    closed_at              timestamptz,
    reopened_count         integer      NOT NULL DEFAULT 0,
    clinic_unread          boolean      NOT NULL DEFAULT false,
    support_unread         boolean      NOT NULL DEFAULT true,
    rating                 smallint     CHECK (rating BETWEEN 1 AND 5),
    rating_comment         varchar(500),
    rated_at               timestamptz,
    last_activity_at       timestamptz  NOT NULL DEFAULT now(),
    created_at             timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id)
);
CREATE INDEX support_tickets_tenant_idx ON support_tickets (tenant_id, created_at DESC);
CREATE INDEX support_tickets_status_idx ON support_tickets (status, first_response_due_at);

CREATE TABLE support_messages (
    id             uuid PRIMARY KEY,
    tenant_id      uuid        NOT NULL,
    ticket_id      uuid        NOT NULL,
    author_type    varchar(10) NOT NULL CHECK (author_type IN ('CLINIC', 'SUPPORT', 'SYSTEM')),
    author_user_id uuid REFERENCES users (id),
    body           text        NOT NULL,
    internal       boolean     NOT NULL DEFAULT false,
    created_at     timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, ticket_id) REFERENCES support_tickets (tenant_id, id)
);
CREATE INDEX support_messages_ticket_idx ON support_messages (tenant_id, ticket_id, created_at);
REVOKE UPDATE, DELETE ON support_messages FROM ${app_db_user};

CREATE TABLE support_attachments (
    id           uuid PRIMARY KEY,
    tenant_id    uuid         NOT NULL,
    ticket_id    uuid         NOT NULL,
    author_type  varchar(10)  NOT NULL CHECK (author_type IN ('CLINIC', 'SUPPORT')),
    uploaded_by  uuid REFERENCES users (id),
    filename     varchar(200) NOT NULL,
    content_type varchar(60)  NOT NULL,
    size_bytes   bigint       NOT NULL CHECK (size_bytes > 0),
    storage_key  varchar(300) NOT NULL UNIQUE,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    purged_at    timestamptz,
    FOREIGN KEY (tenant_id, ticket_id) REFERENCES support_tickets (tenant_id, id)
);
CREATE INDEX support_attachments_ticket_idx ON support_attachments (tenant_id, ticket_id);
REVOKE UPDATE, DELETE ON support_attachments FROM ${app_db_user};
GRANT UPDATE (purged_at) ON support_attachments TO ${app_db_user};

ALTER TABLE support_tickets ENABLE ROW LEVEL SECURITY;
ALTER TABLE support_tickets FORCE ROW LEVEL SECURITY;
CREATE POLICY support_tickets_access ON support_tickets
    USING (tenant_id = app_current_tenant_id() OR app_is_platform_admin())
    WITH CHECK (tenant_id = app_current_tenant_id() OR app_is_platform_admin());

ALTER TABLE support_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE support_messages FORCE ROW LEVEL SECURITY;
CREATE POLICY support_messages_access ON support_messages
    USING (tenant_id = app_current_tenant_id() OR app_is_platform_admin())
    WITH CHECK (tenant_id = app_current_tenant_id() OR app_is_platform_admin());

ALTER TABLE support_attachments ENABLE ROW LEVEL SECURITY;
ALTER TABLE support_attachments FORCE ROW LEVEL SECURITY;
CREATE POLICY support_attachments_access ON support_attachments
    USING (tenant_id = app_current_tenant_id() OR app_is_platform_admin())
    WITH CHECK (tenant_id = app_current_tenant_id() OR app_is_platform_admin());

CREATE TABLE support_canned_responses (
    id         uuid PRIMARY KEY,
    title      varchar(120) NOT NULL,
    body       text         NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE support_notifications (
    id         uuid PRIMARY KEY,
    ticket_id  uuid         NOT NULL REFERENCES support_tickets (id),
    recipient  varchar(254) NOT NULL,
    subject    varchar(200) NOT NULL,
    body       text         NOT NULL,
    status     varchar(10)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts   integer      NOT NULL DEFAULT 0,
    last_error varchar(300),
    created_at timestamptz  NOT NULL DEFAULT now(),
    sent_at    timestamptz
);
CREATE INDEX support_notifications_pending_idx ON support_notifications (status, created_at);
