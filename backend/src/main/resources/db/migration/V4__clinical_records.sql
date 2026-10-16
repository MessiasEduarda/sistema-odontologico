-- V4: prontuário odontológico.
-- Princípio: registros clínicos NÃO são editados nem apagados. Correção = novo registro (evolução que retifica
-- outra) ou anulação com motivo (odontograma, procedimento, anexo), preservando autoria e histórico.

-- ---------------------------------------------------------------------------------------------
-- Anamnese
-- ---------------------------------------------------------------------------------------------
-- Modelo de questionário da clínica (se não houver, a aplicação usa o modelo padrão embutido).
CREATE TABLE anamnesis_templates (
    tenant_id  uuid PRIMARY KEY REFERENCES tenants (id),
    questions  jsonb NOT NULL,
    updated_by uuid,
    updated_at timestamptz NOT NULL DEFAULT now()
);
SELECT enable_tenant_rls('anamnesis_templates');

-- Cada preenchimento é uma nova versão (histórico completo).
CREATE TABLE anamneses (
    id          uuid PRIMARY KEY,
    tenant_id   uuid NOT NULL,
    patient_id  uuid NOT NULL,
    questions   jsonb NOT NULL, -- cópia do questionário usado
    answers     jsonb NOT NULL,
    alerts      jsonb NOT NULL DEFAULT '[]', -- respostas que exigem atenção (alergias, anticoagulantes...)
    notes       text,
    filled_by   varchar(20) NOT NULL DEFAULT 'PROFESSIONAL' CHECK (filled_by IN ('PROFESSIONAL', 'PATIENT')),
    created_by  uuid,
    created_at  timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, patient_id) REFERENCES patients (tenant_id, id)
);
CREATE INDEX anamneses_patient_idx ON anamneses (tenant_id, patient_id, created_at DESC);
SELECT enable_tenant_rls('anamneses');
REVOKE UPDATE, DELETE ON anamneses FROM ${app_db_user};

-- ---------------------------------------------------------------------------------------------
-- Odontograma (notação FDI; dentes permanentes e decíduos)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE dental_chart_entries (
    id          uuid PRIMARY KEY,
    tenant_id   uuid NOT NULL,
    patient_id  uuid NOT NULL,
    tooth       smallint NOT NULL CHECK (
                    (tooth BETWEEN 11 AND 18) OR (tooth BETWEEN 21 AND 28) OR (tooth BETWEEN 31 AND 38) OR
                    (tooth BETWEEN 41 AND 48) OR (tooth BETWEEN 51 AND 55) OR (tooth BETWEEN 61 AND 65) OR
                    (tooth BETWEEN 71 AND 75) OR (tooth BETWEEN 81 AND 85)),
    surfaces    varchar(6) CHECK (surfaces ~ '^[MODVLPI]{1,6}$'),
    condition   varchar(30) NOT NULL,
    status      varchar(20) NOT NULL DEFAULT 'EXISTING' CHECK (status IN ('EXISTING', 'PLANNED', 'DONE')),
    notes       varchar(500),
    created_by  uuid,
    created_at  timestamptz NOT NULL DEFAULT now(),
    voided_at   timestamptz,
    voided_by   uuid,
    void_reason varchar(300),
    FOREIGN KEY (tenant_id, patient_id) REFERENCES patients (tenant_id, id)
);
CREATE INDEX dental_chart_patient_idx ON dental_chart_entries (tenant_id, patient_id, created_at);
SELECT enable_tenant_rls('dental_chart_entries');
REVOKE UPDATE, DELETE ON dental_chart_entries FROM ${app_db_user};
GRANT UPDATE (voided_at, voided_by, void_reason) ON dental_chart_entries TO ${app_db_user};

-- ---------------------------------------------------------------------------------------------
-- Evoluções clínicas (somente inclusão; retificação = nova evolução que aponta para a anterior)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE clinical_notes (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    patient_id      uuid NOT NULL,
    professional_id uuid,
    appointment_id  uuid,
    content         text NOT NULL,
    amends_note_id  uuid,
    created_by      uuid,
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, patient_id) REFERENCES patients (tenant_id, id),
    FOREIGN KEY (tenant_id, professional_id) REFERENCES professionals (tenant_id, id),
    FOREIGN KEY (tenant_id, appointment_id) REFERENCES appointments (tenant_id, id),
    FOREIGN KEY (tenant_id, amends_note_id) REFERENCES clinical_notes (tenant_id, id)
);
CREATE INDEX clinical_notes_patient_idx ON clinical_notes (tenant_id, patient_id, created_at DESC);
SELECT enable_tenant_rls('clinical_notes');
REVOKE UPDATE, DELETE ON clinical_notes FROM ${app_db_user};

-- ---------------------------------------------------------------------------------------------
-- Procedimentos realizados
-- ---------------------------------------------------------------------------------------------
CREATE TABLE performed_procedures (
    id                uuid PRIMARY KEY,
    tenant_id         uuid NOT NULL,
    patient_id        uuid NOT NULL,
    professional_id   uuid NOT NULL,
    procedure_id      uuid,
    description       varchar(200) NOT NULL,
    tooth             smallint,
    surfaces          varchar(6),
    treatment_item_id uuid,
    performed_at      timestamptz NOT NULL DEFAULT now(),
    notes             varchar(1000),
    created_by        uuid,
    created_at        timestamptz NOT NULL DEFAULT now(),
    voided_at         timestamptz,
    voided_by         uuid,
    void_reason       varchar(300),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, patient_id) REFERENCES patients (tenant_id, id),
    FOREIGN KEY (tenant_id, professional_id) REFERENCES professionals (tenant_id, id),
    FOREIGN KEY (tenant_id, procedure_id) REFERENCES procedures (tenant_id, id)
);
CREATE INDEX performed_procedures_patient_idx ON performed_procedures (tenant_id, patient_id, performed_at DESC);
CREATE INDEX performed_procedures_prof_idx ON performed_procedures (tenant_id, professional_id, performed_at);
SELECT enable_tenant_rls('performed_procedures');
REVOKE UPDATE, DELETE ON performed_procedures FROM ${app_db_user};
GRANT UPDATE (voided_at, voided_by, void_reason) ON performed_procedures TO ${app_db_user};

-- ---------------------------------------------------------------------------------------------
-- Termos de consentimento
-- ---------------------------------------------------------------------------------------------
-- Modelos versionados: editar um modelo cria nova versão; termos já emitidos guardam o texto exato que foi aceito.
CREATE TABLE consent_templates (
    id         uuid PRIMARY KEY,
    tenant_id  uuid NOT NULL REFERENCES tenants (id),
    family_id  uuid NOT NULL, -- agrupa as versões do mesmo modelo
    title      varchar(160) NOT NULL,
    body       text NOT NULL,
    version    integer NOT NULL DEFAULT 1,
    active     boolean NOT NULL DEFAULT true, -- somente a versão mais recente fica ativa
    created_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, family_id, version)
);
SELECT enable_tenant_rls('consent_templates');

CREATE TABLE patient_consents (
    id                   uuid PRIMARY KEY,
    tenant_id            uuid NOT NULL,
    patient_id           uuid NOT NULL,
    template_id          uuid,
    template_version     integer,
    title                varchar(160) NOT NULL,
    body                 text NOT NULL, -- texto final exibido ao paciente
    body_sha256          char(64) NOT NULL, -- integridade do texto aceito
    procedure_description varchar(200),
    professional_id      uuid,
    status               varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SIGNED', 'REVOKED')),
    sign_token_hash      char(64) UNIQUE,
    sign_token_expires_at timestamptz,
    signed_at            timestamptz,
    signer_name          varchar(160),
    signer_document      varchar(20),
    signature_image      text, -- PNG em data URL (assinatura desenhada)
    signed_ip            varchar(64),
    signed_user_agent    varchar(255),
    revoked_at           timestamptz,
    revoke_reason        varchar(300),
    created_by           uuid,
    created_at           timestamptz NOT NULL DEFAULT now(),
    version              bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, patient_id) REFERENCES patients (tenant_id, id),
    FOREIGN KEY (tenant_id, template_id) REFERENCES consent_templates (tenant_id, id),
    FOREIGN KEY (tenant_id, professional_id) REFERENCES professionals (tenant_id, id),
    CHECK (signature_image IS NULL OR length(signature_image) <= 400000)
);
CREATE INDEX patient_consents_patient_idx ON patient_consents (tenant_id, patient_id, created_at DESC);
ALTER TABLE patient_consents ENABLE ROW LEVEL SECURITY;
ALTER TABLE patient_consents FORCE ROW LEVEL SECURITY;
-- O paciente com o link de assinatura acessa somente o próprio termo.
CREATE POLICY tenant_isolation ON patient_consents
    USING (tenant_id = app_current_tenant_id() OR sign_token_hash = app_current_access_token_hash())
    WITH CHECK (tenant_id = app_current_tenant_id());
REVOKE DELETE ON patient_consents FROM ${app_db_user};

-- ---------------------------------------------------------------------------------------------
-- Documentos clínicos emitidos (receituário, atestado, encaminhamento...)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE clinical_documents (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    patient_id      uuid NOT NULL,
    professional_id uuid NOT NULL,
    type            varchar(20) NOT NULL CHECK (type IN ('PRESCRIPTION', 'CERTIFICATE', 'REFERRAL', 'OTHER')),
    title           varchar(160) NOT NULL,
    content         text NOT NULL,
    issued_at       timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    FOREIGN KEY (tenant_id, patient_id) REFERENCES patients (tenant_id, id),
    FOREIGN KEY (tenant_id, professional_id) REFERENCES professionals (tenant_id, id)
);
CREATE INDEX clinical_documents_patient_idx ON clinical_documents (tenant_id, patient_id, issued_at DESC);
SELECT enable_tenant_rls('clinical_documents');
REVOKE UPDATE, DELETE ON clinical_documents FROM ${app_db_user};

-- ---------------------------------------------------------------------------------------------
-- Anexos (radiografias, fotos, exames). O arquivo fica no armazenamento; aqui só metadados.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE attachments (
    id           uuid PRIMARY KEY,
    tenant_id    uuid NOT NULL,
    patient_id   uuid NOT NULL,
    category     varchar(20) NOT NULL CHECK (category IN ('XRAY', 'PHOTO', 'EXAM', 'DOCUMENT', 'OTHER')),
    description  varchar(300),
    filename     varchar(255) NOT NULL,
    content_type varchar(100) NOT NULL,
    size_bytes   bigint NOT NULL CHECK (size_bytes > 0),
    sha256       char(64) NOT NULL,
    storage_key  varchar(300) NOT NULL UNIQUE,
    uploaded_by  uuid,
    created_at   timestamptz NOT NULL DEFAULT now(),
    deleted_at   timestamptz,
    deleted_by   uuid,
    FOREIGN KEY (tenant_id, patient_id) REFERENCES patients (tenant_id, id)
);
CREATE INDEX attachments_patient_idx ON attachments (tenant_id, patient_id, created_at DESC);
SELECT enable_tenant_rls('attachments');
REVOKE UPDATE, DELETE ON attachments FROM ${app_db_user};
GRANT UPDATE (deleted_at, deleted_by) ON attachments TO ${app_db_user};
