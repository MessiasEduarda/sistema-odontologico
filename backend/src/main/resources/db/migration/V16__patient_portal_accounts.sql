-- V16: conta do paciente no portal (login com e-mail e senha). Tabelas GLOBAIS (sem tenant), como users e
-- sessions: portal_accounts, portal_account_tokens, portal_sessions, portal_trusted_devices, portal_login_challenges e
-- portal_login_attempts. Tabela DA CLÍNICA (RLS): portal_account_links, que liga a conta ao cadastro do paciente.
-- O link mágico de 30 dias (portal_access_tokens) deixa de ser usado.

CREATE FUNCTION app_current_portal_account_id() RETURNS uuid
    LANGUAGE sql STABLE
AS $$
    SELECT nullif(current_setting('app.portal_account_id', true), '')::uuid
$$;

CREATE TABLE portal_accounts (
    id                  uuid PRIMARY KEY,
    email               varchar(254) NOT NULL,
    password_hash       varchar(255),
    status              varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVE')),
    terms_accepted_at   timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    activated_at        timestamptz,
    password_changed_at timestamptz,
    last_login_at       timestamptz
);
CREATE UNIQUE INDEX portal_accounts_email_key ON portal_accounts (lower(email));

CREATE TABLE portal_account_tokens (
    id          uuid PRIMARY KEY,
    account_id  uuid NOT NULL REFERENCES portal_accounts (id),
    purpose     varchar(20) NOT NULL CHECK (purpose IN ('ACTIVATION', 'RESET')),
    token_hash  char(64) NOT NULL UNIQUE,
    invited_tenant_id  uuid,
    invited_patient_id uuid,
    attempts    integer NOT NULL DEFAULT 0,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX portal_account_tokens_account_idx ON portal_account_tokens (account_id, purpose);

CREATE TABLE portal_sessions (
    id           uuid PRIMARY KEY,
    token_hash   char(64) NOT NULL UNIQUE,
    account_id   uuid NOT NULL REFERENCES portal_accounts (id),
    active_tenant_id  uuid,
    active_patient_id uuid,
    created_at   timestamptz NOT NULL DEFAULT now(),
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    expires_at   timestamptz NOT NULL,
    revoked_at   timestamptz,
    ip           varchar(64),
    user_agent   varchar(300)
);
CREATE INDEX portal_sessions_account_idx ON portal_sessions (account_id);

CREATE TABLE portal_trusted_devices (
    id           uuid PRIMARY KEY,
    account_id   uuid NOT NULL REFERENCES portal_accounts (id),
    token_hash   char(64) NOT NULL UNIQUE,
    created_at   timestamptz NOT NULL DEFAULT now(),
    last_used_at timestamptz NOT NULL DEFAULT now(),
    expires_at   timestamptz NOT NULL,
    revoked_at   timestamptz,
    user_agent   varchar(300)
);

CREATE TABLE portal_login_challenges (
    id         uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES portal_accounts (id),
    code_hash  char(64) NOT NULL,
    attempts   integer NOT NULL DEFAULT 0,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE portal_login_attempts (
    id         uuid PRIMARY KEY,
    email      varchar(254) NOT NULL,
    ip         varchar(64),
    success    boolean NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX portal_login_attempts_email_idx ON portal_login_attempts (lower(email), created_at);
CREATE INDEX portal_login_attempts_ip_idx ON portal_login_attempts (ip, created_at);

CREATE TABLE portal_account_links (
    tenant_id  uuid NOT NULL,
    patient_id uuid NOT NULL,
    account_id uuid NOT NULL REFERENCES portal_accounts (id),
    invited_at timestamptz NOT NULL DEFAULT now(),
    blocked_at timestamptz,
    PRIMARY KEY (tenant_id, patient_id),
    FOREIGN KEY (tenant_id, patient_id) REFERENCES patients (tenant_id, id)
);
CREATE INDEX portal_account_links_account_idx ON portal_account_links (account_id);
ALTER TABLE portal_account_links ENABLE ROW LEVEL SECURITY;
ALTER TABLE portal_account_links FORCE ROW LEVEL SECURITY;
-- A clínica vê os vínculos dos seus pacientes; o paciente logado vê os vínculos da PRÓPRIA conta (para escolher o
-- perfil) e nada além disso.
CREATE POLICY portal_account_links_isolation ON portal_account_links
    USING (tenant_id = app_current_tenant_id() OR account_id = app_current_portal_account_id())
    WITH CHECK (tenant_id = app_current_tenant_id());
