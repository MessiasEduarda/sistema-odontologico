-- V12: lista de espera (encaixe). Pacientes que querem um horário mais cedo ou que não acharam vaga; quando abre
-- um horário (cancelamento, falta), a recepção chama alguém da lista. status: WAITING (aguardando),
-- SCHEDULED (encaixado; appointment_id aponta o atendimento) ou REMOVED (desistiu / não precisa mais).

CREATE TABLE waitlist_entries (
    id              uuid         PRIMARY KEY,
    tenant_id       uuid         NOT NULL REFERENCES tenants (id),
    patient_id      uuid         NOT NULL,
    professional_id uuid,
    procedure_id    uuid,
    preference      varchar(200),
    notes           varchar(500),
    urgent          boolean      NOT NULL DEFAULT false,
    status          varchar(20)  NOT NULL DEFAULT 'WAITING' CHECK (status IN ('WAITING', 'SCHEDULED', 'REMOVED')),
    appointment_id  uuid,
    created_by      uuid,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    resolved_at     timestamptz,
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, patient_id) REFERENCES patients (tenant_id, id),
    FOREIGN KEY (tenant_id, professional_id) REFERENCES professionals (tenant_id, id),
    FOREIGN KEY (tenant_id, procedure_id) REFERENCES procedures (tenant_id, id),
    FOREIGN KEY (tenant_id, appointment_id) REFERENCES appointments (tenant_id, id)
);
CREATE INDEX waitlist_entries_status_idx ON waitlist_entries (tenant_id, status, created_at);
CREATE INDEX waitlist_entries_patient_idx ON waitlist_entries (tenant_id, patient_id);

SELECT enable_tenant_rls('waitlist_entries');
