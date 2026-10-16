-- V11: bloqueios de agenda (folga, férias, feriado, almoço, curso…).
-- professional_id nulo = bloqueio da clínica inteira (ex.: feriado). Nenhum atendimento novo pode ser marcado
-- dentro de um bloqueio; atendimentos já existentes no período não são alterados (a tela avisa para remarcar).

CREATE TABLE schedule_blocks (
    id              uuid         PRIMARY KEY,
    tenant_id       uuid         NOT NULL REFERENCES tenants (id),
    professional_id uuid,
    starts_at       timestamptz  NOT NULL,
    ends_at         timestamptz  NOT NULL,
    reason          varchar(160) NOT NULL,
    created_by      uuid,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    CHECK (ends_at > starts_at),
    FOREIGN KEY (tenant_id, professional_id) REFERENCES professionals (tenant_id, id)
);
CREATE INDEX schedule_blocks_period_idx ON schedule_blocks (tenant_id, starts_at);

SELECT enable_tenant_rls('schedule_blocks');
