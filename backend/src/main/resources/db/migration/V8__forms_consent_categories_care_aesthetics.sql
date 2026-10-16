-- V8: várias fichas de anamnese por clínica, categorias de termos, orientações de cuidados ao paciente e registro de
-- procedimentos de harmonização orofacial (opcional por clínica) com rastreabilidade de lote.

-- ---------------------------------------------------------------------------------------------
-- Configuração: a clínica realiza harmonização orofacial?
-- ---------------------------------------------------------------------------------------------
ALTER TABLE clinic_settings ADD COLUMN aesthetics_enabled boolean NOT NULL DEFAULT false;

-- ---------------------------------------------------------------------------------------------
-- Fichas de anamnese (substitui o modelo único por clínica)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE anamnesis_forms (
    id         uuid PRIMARY KEY,
    tenant_id  uuid NOT NULL REFERENCES tenants (id),
    name       varchar(120) NOT NULL,
    kind       varchar(20) NOT NULL CHECK (kind IN ('DENTAL', 'AESTHETIC', 'OTHER')),
    questions  jsonb NOT NULL,
    active     boolean NOT NULL DEFAULT true,
    updated_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, name)
);
SELECT enable_tenant_rls('anamnesis_forms');

-- O questionário personalizado existente vira a ficha odontológica da clínica.
INSERT INTO anamnesis_forms (id, tenant_id, name, kind, questions, updated_by, updated_at)
SELECT gen_random_uuid(), tenant_id, 'Anamnese odontológica', 'DENTAL', questions, updated_by, updated_at
FROM anamnesis_templates;

DROP TABLE anamnesis_templates;

-- Cada preenchimento registra qual ficha foi usada (cópia do nome; as perguntas já são copiadas).
ALTER TABLE anamneses ADD COLUMN form_id uuid;
ALTER TABLE anamneses ADD COLUMN form_name varchar(120) NOT NULL DEFAULT 'Anamnese odontológica';
ALTER TABLE anamneses ADD COLUMN form_kind varchar(20) NOT NULL DEFAULT 'DENTAL'
    CHECK (form_kind IN ('DENTAL', 'AESTHETIC', 'OTHER'));
ALTER TABLE anamneses ADD FOREIGN KEY (tenant_id, form_id) REFERENCES anamnesis_forms (tenant_id, id);
CREATE INDEX anamneses_recent_idx ON anamneses (tenant_id, created_at DESC);

-- ---------------------------------------------------------------------------------------------
-- Categorias de termos
-- ---------------------------------------------------------------------------------------------
ALTER TABLE consent_templates ADD COLUMN category varchar(20) NOT NULL DEFAULT 'DENTAL'
    CHECK (category IN ('DENTAL', 'AESTHETIC', 'IMAGE', 'PRIVACY', 'GENERAL'));
ALTER TABLE patient_consents ADD COLUMN category varchar(20) NOT NULL DEFAULT 'DENTAL'
    CHECK (category IN ('DENTAL', 'AESTHETIC', 'IMAGE', 'PRIVACY', 'GENERAL'));
CREATE INDEX patient_consents_recent_idx ON patient_consents (tenant_id, created_at DESC);

-- ---------------------------------------------------------------------------------------------
-- Orientações e cuidados (pré e pós-procedimento). Modelos editáveis; o que é entregue ao paciente vira documento
-- clínico imutável (tipo INSTRUCTIONS).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE care_instruction_templates (
    id         uuid PRIMARY KEY,
    tenant_id  uuid NOT NULL REFERENCES tenants (id),
    title      varchar(160) NOT NULL,
    category   varchar(20) NOT NULL CHECK (category IN ('DENTAL', 'AESTHETIC', 'GENERAL')),
    moment     varchar(10) NOT NULL CHECK (moment IN ('BEFORE', 'AFTER', 'GENERAL')),
    body       text NOT NULL CHECK (length(body) <= 20000),
    active     boolean NOT NULL DEFAULT true,
    created_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id)
);
SELECT enable_tenant_rls('care_instruction_templates');

ALTER TABLE clinical_documents DROP CONSTRAINT clinical_documents_type_check;
ALTER TABLE clinical_documents ADD CONSTRAINT clinical_documents_type_check
    CHECK (type IN ('PRESCRIPTION', 'CERTIFICATE', 'REFERRAL', 'INSTRUCTIONS', 'OTHER'));
CREATE INDEX clinical_documents_recent_idx ON clinical_documents (tenant_id, issued_at DESC);

-- ---------------------------------------------------------------------------------------------
-- Harmonização orofacial: registro de aplicação (produto, lote, validade, regiões e quantidades).
-- Somente inclusão; correção = anulação com motivo + novo registro.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE aesthetic_procedures (
    id               uuid PRIMARY KEY,
    tenant_id        uuid NOT NULL,
    patient_id       uuid NOT NULL,
    professional_id  uuid NOT NULL,
    performed_at     timestamptz NOT NULL,
    procedure_type   varchar(20) NOT NULL CHECK (procedure_type IN (
                         'TOXIN', 'FILLER', 'BIOSTIMULATOR', 'THREADS', 'SKINBOOSTER', 'PEEL', 'MICRONEEDLING',
                         'LIPOLYSIS', 'OTHER')),
    product_name     varchar(160) NOT NULL,
    manufacturer     varchar(160),
    anvisa_registry  varchar(40),
    batch            varchar(60) NOT NULL,
    expiry_date      date,
    unit             varchar(5) NOT NULL CHECK (unit IN ('U', 'ML', 'UN', 'MG')),
    regions          jsonb NOT NULL, -- [{ "region": "...", "quantity": 4.0, "notes": "..." }]
    total_quantity   numeric(10, 2) NOT NULL CHECK (total_quantity > 0),
    technique        varchar(2000),
    notes            varchar(2000),
    return_date      date,
    consent_id       uuid,
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    voided_at        timestamptz,
    voided_by        uuid,
    void_reason      varchar(300),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, patient_id) REFERENCES patients (tenant_id, id),
    FOREIGN KEY (tenant_id, professional_id) REFERENCES professionals (tenant_id, id),
    FOREIGN KEY (tenant_id, consent_id) REFERENCES patient_consents (tenant_id, id)
);
CREATE INDEX aesthetic_patient_idx ON aesthetic_procedures (tenant_id, patient_id, performed_at DESC);
CREATE INDEX aesthetic_recent_idx ON aesthetic_procedures (tenant_id, performed_at DESC);
CREATE INDEX aesthetic_batch_idx ON aesthetic_procedures (tenant_id, lower(batch));
CREATE INDEX aesthetic_return_idx ON aesthetic_procedures (tenant_id, return_date) WHERE voided_at IS NULL;
SELECT enable_tenant_rls('aesthetic_procedures');
REVOKE UPDATE, DELETE ON aesthetic_procedures FROM ${app_db_user};
GRANT UPDATE (voided_at, voided_by, void_reason) ON aesthetic_procedures TO ${app_db_user};

-- Novo tipo de mensagem: orientações enviadas ao paciente por e-mail.
ALTER TABLE message_templates DROP CONSTRAINT message_templates_type_check;
ALTER TABLE message_templates ADD CONSTRAINT message_templates_type_check
    CHECK (type IN ('APPOINTMENT_REMINDER', 'APPOINTMENT_CREATED', 'APPOINTMENT_CANCELLED', 'PORTAL_ACCESS',
                    'CONSENT_REQUEST', 'CARE_INSTRUCTIONS'));
