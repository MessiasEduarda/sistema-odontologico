-- V15: documentos emitidos podem ser corrigidos (a versão anterior fica em clinical_document_revisions) ou anulados
-- com motivo, e só aparecem no portal do paciente quando a clínica libera. Anexos passam a aceitar correção de
-- categoria e descrição.
ALTER TABLE clinical_documents
    ADD COLUMN shared_with_patient boolean NOT NULL DEFAULT false,
    ADD COLUMN updated_at          timestamptz,
    ADD COLUMN updated_by          uuid,
    ADD COLUMN voided_at           timestamptz,
    ADD COLUMN voided_by           uuid,
    ADD COLUMN void_reason         varchar(300);
GRANT UPDATE (title, content, shared_with_patient, updated_at, updated_by, voided_at, voided_by, void_reason)
    ON clinical_documents TO ${app_db_user};

CREATE TABLE clinical_document_revisions (
    id          uuid PRIMARY KEY,
    tenant_id   uuid NOT NULL,
    document_id uuid NOT NULL REFERENCES clinical_documents (id),
    title       varchar(160) NOT NULL,
    content     text NOT NULL,
    revised_by  uuid,
    revised_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX clinical_document_revisions_doc_idx ON clinical_document_revisions (tenant_id, document_id, revised_at);
SELECT enable_tenant_rls('clinical_document_revisions');
REVOKE UPDATE, DELETE ON clinical_document_revisions FROM ${app_db_user};

GRANT UPDATE (category, description) ON attachments TO ${app_db_user};
