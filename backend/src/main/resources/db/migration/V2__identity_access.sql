-- V2: identidade, acesso por clínica, sessões, auditoria e Row-Level Security.
-- Tabelas GLOBAIS (sem tenant): users, sessions, password_reset_tokens, login_attempts.
-- Tabelas DA CLÍNICA (tenant_id + RLS): memberships, roles, role_permissions, membership_roles,
-- invitations, audit_events.

-- ---------------------------------------------------------------------------------------------
-- Contexto da requisição (definido pela aplicação por conexão; ver TenantAwareDataSource)
-- ---------------------------------------------------------------------------------------------
CREATE FUNCTION app_current_user_id() RETURNS uuid
    LANGUAGE sql STABLE
AS $$
    SELECT nullif(current_setting('app.user_id', true), '')::uuid
$$;

-- Hash (hex) do token de convite sendo aceito; permite ler APENAS aquele convite sem tenant ativo.
CREATE FUNCTION app_current_invitation_hash() RETURNS text
    LANGUAGE sql STABLE
AS $$
    SELECT nullif(current_setting('app.invitation_token_hash', true), '')
$$;

-- ---------------------------------------------------------------------------------------------
-- Global
-- ---------------------------------------------------------------------------------------------
CREATE TABLE users (
    id                  uuid         PRIMARY KEY,
    email               citext       NOT NULL UNIQUE,
    name                varchar(160) NOT NULL,
    password_hash       varchar(255) NOT NULL,
    status              varchar(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    platform_role       varchar(30)  CHECK (platform_role IN ('PLATFORM_ADMIN')),
    password_changed_at timestamptz  NOT NULL DEFAULT now(),
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    version             bigint       NOT NULL DEFAULT 0
);

CREATE TABLE sessions (
    id               uuid         PRIMARY KEY,
    token_hash       char(64)     NOT NULL UNIQUE,
    user_id          uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    active_tenant_id uuid         REFERENCES tenants (id),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    last_seen_at     timestamptz  NOT NULL DEFAULT now(),
    expires_at       timestamptz  NOT NULL,
    revoked_at       timestamptz,
    revoke_reason    varchar(40),
    ip               varchar(64),
    user_agent       varchar(255)
);
CREATE INDEX sessions_user_idx ON sessions (user_id) WHERE revoked_at IS NULL;

CREATE TABLE password_reset_tokens (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash char(64)    NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX password_reset_tokens_user_idx ON password_reset_tokens (user_id, created_at);

CREATE TABLE login_attempts (
    id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email        citext      NOT NULL,
    ip           varchar(64),
    success      boolean     NOT NULL,
    attempted_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX login_attempts_email_idx ON login_attempts (email, attempted_at);
CREATE INDEX login_attempts_ip_idx ON login_attempts (ip, attempted_at);

-- ---------------------------------------------------------------------------------------------
-- Da clínica (chaves compostas (tenant_id, id) impedem referências entre clínicas)
-- ---------------------------------------------------------------------------------------------
ALTER TABLE tenants ADD CONSTRAINT tenants_id_unique UNIQUE (id);

CREATE TABLE memberships (
    id         uuid        PRIMARY KEY,
    tenant_id  uuid        NOT NULL REFERENCES tenants (id),
    user_id    uuid        NOT NULL REFERENCES users (id),
    status     varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version    bigint      NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, user_id),
    UNIQUE (tenant_id, id)
);
CREATE INDEX memberships_user_idx ON memberships (user_id);

CREATE TABLE roles (
    id          uuid        PRIMARY KEY,
    tenant_id   uuid        NOT NULL REFERENCES tenants (id),
    code        varchar(40) NOT NULL,
    name        varchar(80) NOT NULL,
    system_role boolean     NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, code),
    UNIQUE (tenant_id, id)
);

CREATE TABLE role_permissions (
    tenant_id  uuid        NOT NULL,
    role_id    uuid        NOT NULL,
    permission varchar(60) NOT NULL,
    PRIMARY KEY (role_id, permission),
    FOREIGN KEY (tenant_id, role_id) REFERENCES roles (tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE membership_roles (
    tenant_id     uuid NOT NULL,
    membership_id uuid NOT NULL,
    role_id       uuid NOT NULL,
    PRIMARY KEY (membership_id, role_id),
    FOREIGN KEY (tenant_id, membership_id) REFERENCES memberships (tenant_id, id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, role_id) REFERENCES roles (tenant_id, id)
);

CREATE TABLE invitations (
    id          uuid        PRIMARY KEY,
    tenant_id   uuid        NOT NULL REFERENCES tenants (id),
    email       citext      NOT NULL,
    role_id     uuid        NOT NULL,
    token_hash  char(64)    NOT NULL UNIQUE,
    status      varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED')),
    invited_by  uuid        REFERENCES users (id),
    expires_at  timestamptz NOT NULL,
    accepted_at timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    version     bigint      NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, role_id) REFERENCES roles (tenant_id, id)
);
CREATE UNIQUE INDEX invitations_one_pending_per_email ON invitations (tenant_id, email) WHERE status = 'PENDING';

-- Trilha de auditoria: somente inclusão. tenant_id nulo = evento de plataforma/autenticação.
CREATE TABLE audit_events (
    id            uuid        PRIMARY KEY,
    tenant_id     uuid        REFERENCES tenants (id),
    actor_user_id uuid,
    action        varchar(60) NOT NULL,
    resource_type varchar(60),
    resource_id   varchar(64),
    ip            varchar(64),
    metadata      jsonb,
    occurred_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_events_tenant_idx ON audit_events (tenant_id, occurred_at DESC);

REVOKE UPDATE, DELETE ON audit_events FROM ${app_db_user};

-- ---------------------------------------------------------------------------------------------
-- Row-Level Security
-- ---------------------------------------------------------------------------------------------
ALTER TABLE memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE memberships FORCE ROW LEVEL SECURITY;
-- O usuário enxerga os PRÓPRIOS vínculos (para listar suas clínicas) e os da clínica ativa.
CREATE POLICY memberships_isolation ON memberships
    USING (tenant_id = app_current_tenant_id() OR user_id = app_current_user_id())
    WITH CHECK (tenant_id = app_current_tenant_id());

ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE roles FORCE ROW LEVEL SECURITY;
CREATE POLICY roles_isolation ON roles
    USING (tenant_id = app_current_tenant_id())
    WITH CHECK (tenant_id = app_current_tenant_id());

ALTER TABLE role_permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_permissions FORCE ROW LEVEL SECURITY;
CREATE POLICY role_permissions_isolation ON role_permissions
    USING (tenant_id = app_current_tenant_id())
    WITH CHECK (tenant_id = app_current_tenant_id());

ALTER TABLE membership_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE membership_roles FORCE ROW LEVEL SECURITY;
CREATE POLICY membership_roles_isolation ON membership_roles
    USING (tenant_id = app_current_tenant_id())
    WITH CHECK (tenant_id = app_current_tenant_id());

ALTER TABLE invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE invitations FORCE ROW LEVEL SECURITY;
CREATE POLICY invitations_isolation ON invitations
    USING (tenant_id = app_current_tenant_id() OR token_hash = app_current_invitation_hash())
    WITH CHECK (tenant_id = app_current_tenant_id());

ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_events FORCE ROW LEVEL SECURITY;
CREATE POLICY audit_events_read ON audit_events FOR SELECT
    USING (tenant_id = app_current_tenant_id());
CREATE POLICY audit_events_insert ON audit_events FOR INSERT
    WITH CHECK (tenant_id IS NULL OR tenant_id = app_current_tenant_id());
