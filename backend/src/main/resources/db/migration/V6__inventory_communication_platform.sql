-- V6: estoque, comunicação, portal do paciente e planos/assinaturas da plataforma.

-- ---------------------------------------------------------------------------------------------
-- Estoque
-- ---------------------------------------------------------------------------------------------
CREATE TABLE products (
    id            uuid PRIMARY KEY,
    tenant_id     uuid NOT NULL REFERENCES tenants (id),
    name          varchar(160) NOT NULL,
    category      varchar(60),
    unit          varchar(20) NOT NULL DEFAULT 'un',
    min_stock     numeric(12, 3) NOT NULL DEFAULT 0 CHECK (min_stock >= 0),
    current_stock numeric(12, 3) NOT NULL DEFAULT 0 CHECK (current_stock >= 0),
    supplier_id   uuid,
    active        boolean NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    version       bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, name),
    FOREIGN KEY (tenant_id, supplier_id) REFERENCES suppliers (tenant_id, id)
);
SELECT enable_tenant_rls('products');

-- Movimentações (somente inclusão); o saldo do produto é atualizado na mesma transação.
CREATE TABLE stock_movements (
    id          uuid PRIMARY KEY,
    tenant_id   uuid NOT NULL,
    product_id  uuid NOT NULL,
    type        varchar(10) NOT NULL CHECK (type IN ('IN', 'OUT', 'ADJUST')),
    quantity    numeric(12, 3) NOT NULL CHECK (quantity <> 0),
    unit_cost   numeric(12, 2) CHECK (unit_cost >= 0),
    lot_number  varchar(60),
    expiry_date date,
    reason      varchar(300),
    balance_after numeric(12, 3) NOT NULL,
    created_by  uuid,
    created_at  timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, product_id) REFERENCES products (tenant_id, id)
);
CREATE INDEX stock_movements_product_idx ON stock_movements (tenant_id, product_id, created_at DESC);
SELECT enable_tenant_rls('stock_movements');
REVOKE UPDATE, DELETE ON stock_movements FROM ${app_db_user};

-- ---------------------------------------------------------------------------------------------
-- Comunicação
-- ---------------------------------------------------------------------------------------------
CREATE TABLE message_templates (
    tenant_id  uuid NOT NULL REFERENCES tenants (id),
    type       varchar(40) NOT NULL CHECK (type IN ('APPOINTMENT_REMINDER', 'APPOINTMENT_CREATED',
                                                   'APPOINTMENT_CANCELLED', 'PORTAL_ACCESS', 'CONSENT_REQUEST')),
    subject    varchar(200) NOT NULL,
    body       text NOT NULL,
    active     boolean NOT NULL DEFAULT true,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, type)
);
SELECT enable_tenant_rls('message_templates');

-- Fila de envio (padrão outbox): registra cada envio, tentativas e falhas.
CREATE TABLE outbound_messages (
    id             uuid PRIMARY KEY,
    tenant_id      uuid NOT NULL REFERENCES tenants (id),
    patient_id     uuid,
    appointment_id uuid,
    type           varchar(40) NOT NULL,
    channel        varchar(20) NOT NULL DEFAULT 'EMAIL' CHECK (channel IN ('EMAIL')),
    to_address     varchar(254) NOT NULL,
    subject        varchar(200) NOT NULL,
    body           text NOT NULL,
    status         varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts       integer NOT NULL DEFAULT 0,
    last_error     varchar(300),
    created_at     timestamptz NOT NULL DEFAULT now(),
    sent_at        timestamptz,
    FOREIGN KEY (tenant_id, patient_id) REFERENCES patients (tenant_id, id),
    FOREIGN KEY (tenant_id, appointment_id) REFERENCES appointments (tenant_id, id)
);
CREATE INDEX outbound_messages_pending_idx ON outbound_messages (status, created_at) WHERE status = 'PENDING';
CREATE INDEX outbound_messages_tenant_idx ON outbound_messages (tenant_id, created_at DESC);
SELECT enable_tenant_rls('outbound_messages');
REVOKE DELETE ON outbound_messages FROM ${app_db_user};

-- ---------------------------------------------------------------------------------------------
-- Portal do paciente: acesso por link com token (sem senha), emitido pela clínica.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE portal_access_tokens (
    id         uuid PRIMARY KEY,
    tenant_id  uuid NOT NULL,
    patient_id uuid NOT NULL,
    token_hash char(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    last_used_at timestamptz,
    created_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, patient_id) REFERENCES patients (tenant_id, id)
);
ALTER TABLE portal_access_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE portal_access_tokens FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON portal_access_tokens
    USING (tenant_id = app_current_tenant_id() OR token_hash = app_current_access_token_hash())
    WITH CHECK (tenant_id = app_current_tenant_id());

-- ---------------------------------------------------------------------------------------------
-- Plataforma: planos e assinaturas (tabelas globais, acessadas só pela administração da plataforma)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE plans (
    id                uuid PRIMARY KEY,
    code              varchar(40) NOT NULL UNIQUE,
    name              varchar(80) NOT NULL,
    price_monthly     numeric(12, 2) NOT NULL DEFAULT 0 CHECK (price_monthly >= 0),
    max_professionals integer CHECK (max_professionals > 0),
    max_patients      integer CHECK (max_patients > 0),
    features          text[] NOT NULL DEFAULT '{}',
    active            boolean NOT NULL DEFAULT true,
    created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE subscriptions (
    tenant_id          uuid PRIMARY KEY REFERENCES tenants (id),
    plan_id            uuid NOT NULL REFERENCES plans (id),
    status             varchar(20) NOT NULL DEFAULT 'TRIALING'
                       CHECK (status IN ('TRIALING', 'ACTIVE', 'PAST_DUE', 'CANCELLED')),
    trial_ends_at      date,
    current_period_end date,
    notes              varchar(500),
    updated_at         timestamptz NOT NULL DEFAULT now()
);

INSERT INTO plans (id, code, name, price_monthly, max_professionals, max_patients, features) VALUES
    ('01900000-0000-7000-8000-000000000001', 'ESSENCIAL', 'Essencial', 0, 2, 500,
     '{AGENDA,PATIENTS,CLINICAL,TREATMENTS,FINANCE}'),
    ('01900000-0000-7000-8000-000000000002', 'PROFISSIONAL', 'Profissional', 0, 10, NULL,
     '{AGENDA,PATIENTS,CLINICAL,TREATMENTS,FINANCE,INVENTORY,COMMUNICATION,REPORTS,PATIENT_PORTAL}'),
    ('01900000-0000-7000-8000-000000000003', 'CLINICA_PLUS', 'Clínica Plus', 0, NULL, NULL,
     '{AGENDA,PATIENTS,CLINICAL,TREATMENTS,FINANCE,INVENTORY,COMMUNICATION,REPORTS,PATIENT_PORTAL}');

-- Clínicas já existentes recebem o plano mais completo em período de avaliação.
INSERT INTO subscriptions (tenant_id, plan_id, status, trial_ends_at)
SELECT id, '01900000-0000-7000-8000-000000000003', 'TRIALING', (now() + interval '30 days')::date FROM tenants;
