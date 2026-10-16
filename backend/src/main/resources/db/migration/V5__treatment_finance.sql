-- V5: planos de tratamento / orçamentos e financeiro.
-- Valores monetários em numeric(12,2) (BRL). Totais são sempre calculados pelo servidor.

-- ---------------------------------------------------------------------------------------------
-- Planos de tratamento (orçamentos)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE treatment_plans (
    id                 uuid PRIMARY KEY,
    tenant_id          uuid NOT NULL,
    patient_id         uuid NOT NULL,
    professional_id    uuid NOT NULL,
    title              varchar(160) NOT NULL,
    status             varchar(20) NOT NULL DEFAULT 'DRAFT'
                       CHECK (status IN ('DRAFT', 'PROPOSED', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED')),
    discount           numeric(12, 2) NOT NULL DEFAULT 0 CHECK (discount >= 0),
    payment_conditions varchar(500),
    notes              text,
    valid_until        date,
    approved_at        timestamptz,
    approved_by_name   varchar(160),
    created_by         uuid,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    version            bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, patient_id) REFERENCES patients (tenant_id, id),
    FOREIGN KEY (tenant_id, professional_id) REFERENCES professionals (tenant_id, id)
);
CREATE INDEX treatment_plans_patient_idx ON treatment_plans (tenant_id, patient_id, created_at DESC);
CREATE INDEX treatment_plans_status_idx ON treatment_plans (tenant_id, status);
SELECT enable_tenant_rls('treatment_plans');

CREATE TABLE treatment_plan_items (
    id           uuid PRIMARY KEY,
    tenant_id    uuid NOT NULL,
    plan_id      uuid NOT NULL,
    procedure_id uuid,
    description  varchar(200) NOT NULL,
    tooth        smallint,
    surfaces     varchar(6),
    quantity     integer NOT NULL DEFAULT 1 CHECK (quantity BETWEEN 1 AND 100),
    unit_price   numeric(12, 2) NOT NULL CHECK (unit_price >= 0),
    discount     numeric(12, 2) NOT NULL DEFAULT 0 CHECK (discount >= 0),
    status       varchar(20) NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED', 'DONE', 'CANCELLED')),
    sort_order   integer NOT NULL DEFAULT 0,
    done_at      timestamptz,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, plan_id) REFERENCES treatment_plans (tenant_id, id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, procedure_id) REFERENCES procedures (tenant_id, id),
    CHECK (discount <= quantity * unit_price)
);
CREATE INDEX treatment_plan_items_plan_idx ON treatment_plan_items (tenant_id, plan_id, sort_order);
SELECT enable_tenant_rls('treatment_plan_items');

ALTER TABLE performed_procedures
    ADD FOREIGN KEY (tenant_id, treatment_item_id) REFERENCES treatment_plan_items (tenant_id, id);

-- ---------------------------------------------------------------------------------------------
-- Contas a receber, parcelas, pagamentos e estornos
-- ---------------------------------------------------------------------------------------------
CREATE TABLE receivables (
    id                uuid PRIMARY KEY,
    tenant_id         uuid NOT NULL,
    patient_id        uuid NOT NULL,
    treatment_plan_id uuid,
    description       varchar(200) NOT NULL,
    total             numeric(12, 2) NOT NULL CHECK (total > 0),
    status            varchar(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'PAID', 'CANCELLED')),
    cancel_reason     varchar(300),
    created_by        uuid,
    created_at        timestamptz NOT NULL DEFAULT now(),
    version           bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, patient_id) REFERENCES patients (tenant_id, id),
    FOREIGN KEY (tenant_id, treatment_plan_id) REFERENCES treatment_plans (tenant_id, id)
);
CREATE INDEX receivables_patient_idx ON receivables (tenant_id, patient_id);
CREATE UNIQUE INDEX receivables_one_per_plan ON receivables (tenant_id, treatment_plan_id)
    WHERE treatment_plan_id IS NOT NULL AND status <> 'CANCELLED';
SELECT enable_tenant_rls('receivables');
REVOKE DELETE ON receivables FROM ${app_db_user};

CREATE TABLE installments (
    id            uuid PRIMARY KEY,
    tenant_id     uuid NOT NULL,
    receivable_id uuid NOT NULL,
    number        integer NOT NULL CHECK (number >= 1),
    due_date      date NOT NULL,
    amount        numeric(12, 2) NOT NULL CHECK (amount > 0),
    paid_amount   numeric(12, 2) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    status        varchar(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'PAID', 'CANCELLED')),
    version       bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, receivable_id, number),
    FOREIGN KEY (tenant_id, receivable_id) REFERENCES receivables (tenant_id, id),
    CHECK (paid_amount <= amount)
);
CREATE INDEX installments_due_idx ON installments (tenant_id, status, due_date);
SELECT enable_tenant_rls('installments');
REVOKE DELETE ON installments FROM ${app_db_user};

CREATE TABLE payments (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    installment_id  uuid NOT NULL,
    amount          numeric(12, 2) NOT NULL CHECK (amount > 0),
    refunded_amount numeric(12, 2) NOT NULL DEFAULT 0 CHECK (refunded_amount >= 0),
    method          varchar(20) NOT NULL
                    CHECK (method IN ('CASH', 'PIX', 'CREDIT_CARD', 'DEBIT_CARD', 'BANK_TRANSFER', 'BOLETO', 'OTHER')),
    paid_at         timestamptz NOT NULL DEFAULT now(),
    notes           varchar(300),
    created_by      uuid,
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, installment_id) REFERENCES installments (tenant_id, id),
    CHECK (refunded_amount <= amount)
);
CREATE INDEX payments_paid_idx ON payments (tenant_id, paid_at);
SELECT enable_tenant_rls('payments');
REVOKE UPDATE, DELETE ON payments FROM ${app_db_user};
GRANT UPDATE (refunded_amount) ON payments TO ${app_db_user};

CREATE TABLE refunds (
    id          uuid PRIMARY KEY,
    tenant_id   uuid NOT NULL,
    payment_id  uuid NOT NULL,
    amount      numeric(12, 2) NOT NULL CHECK (amount > 0),
    reason      varchar(300) NOT NULL,
    refunded_at timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    FOREIGN KEY (tenant_id, payment_id) REFERENCES payments (tenant_id, id)
);
CREATE INDEX refunds_idx ON refunds (tenant_id, refunded_at);
SELECT enable_tenant_rls('refunds');
REVOKE UPDATE, DELETE ON refunds FROM ${app_db_user};

-- ---------------------------------------------------------------------------------------------
-- Fornecedores (usados por contas a pagar e estoque) e contas a pagar
-- ---------------------------------------------------------------------------------------------
CREATE TABLE suppliers (
    id         uuid PRIMARY KEY,
    tenant_id  uuid NOT NULL REFERENCES tenants (id),
    name       varchar(160) NOT NULL,
    document   varchar(18),
    phone      varchar(20),
    email      varchar(254),
    notes      varchar(500),
    active     boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id)
);
SELECT enable_tenant_rls('suppliers');

CREATE TABLE payables (
    id             uuid PRIMARY KEY,
    tenant_id      uuid NOT NULL REFERENCES tenants (id),
    supplier_id    uuid,
    description    varchar(200) NOT NULL,
    category       varchar(60) NOT NULL DEFAULT 'OUTROS',
    amount         numeric(12, 2) NOT NULL CHECK (amount > 0),
    due_date       date NOT NULL,
    status         varchar(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'PAID', 'CANCELLED')),
    paid_at        timestamptz,
    payment_method varchar(20),
    notes          varchar(500),
    created_by     uuid,
    created_at     timestamptz NOT NULL DEFAULT now(),
    version        bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, supplier_id) REFERENCES suppliers (tenant_id, id)
);
CREATE INDEX payables_due_idx ON payables (tenant_id, status, due_date);
SELECT enable_tenant_rls('payables');
REVOKE DELETE ON payables FROM ${app_db_user};
