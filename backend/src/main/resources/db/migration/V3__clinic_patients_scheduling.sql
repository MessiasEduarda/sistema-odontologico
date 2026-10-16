-- V3: cadastros da clínica, pacientes e agenda.

-- ---------------------------------------------------------------------------------------------
-- Utilitários
-- ---------------------------------------------------------------------------------------------

-- Hash de token público de acesso (link do paciente: portal, assinatura de termo, confirmação de consulta).
-- Permite ler APENAS o registro daquele token sem clínica ativa.
CREATE FUNCTION app_current_access_token_hash() RETURNS text
    LANGUAGE sql STABLE
AS $$
    SELECT nullif(current_setting('app.access_token_hash', true), '')
$$;

-- Ativa RLS forçado com a política padrão de isolamento por clínica.
CREATE FUNCTION enable_tenant_rls(tbl regclass) RETURNS void
    LANGUAGE plpgsql
AS $$
BEGIN
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', tbl);
    EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', tbl);
    EXECUTE format('CREATE POLICY tenant_isolation ON %s USING (tenant_id = app_current_tenant_id()) '
                   'WITH CHECK (tenant_id = app_current_tenant_id())', tbl);
END
$$;
REVOKE EXECUTE ON FUNCTION enable_tenant_rls(regclass) FROM ${app_db_user};

-- ---------------------------------------------------------------------------------------------
-- Configurações da clínica
-- ---------------------------------------------------------------------------------------------
CREATE TABLE clinic_settings (
    tenant_id                   uuid PRIMARY KEY REFERENCES tenants (id),
    legal_name                  varchar(160),
    document                    varchar(18),
    phone                       varchar(20),
    email                       varchar(254),
    zip_code                    varchar(9),
    address                     varchar(200),
    city                        varchar(100),
    state                       char(2),
    default_appointment_minutes integer NOT NULL DEFAULT 30 CHECK (default_appointment_minutes BETWEEN 5 AND 480),
    reminders_enabled           boolean NOT NULL DEFAULT true,
    reminder_hours_before       integer NOT NULL DEFAULT 24 CHECK (reminder_hours_before BETWEEN 1 AND 168),
    updated_at                  timestamptz NOT NULL DEFAULT now()
);
SELECT enable_tenant_rls('clinic_settings');

-- ---------------------------------------------------------------------------------------------
-- Cadastros: profissionais, horários, salas, procedimentos
-- ---------------------------------------------------------------------------------------------
CREATE TABLE professionals (
    id                 uuid PRIMARY KEY,
    tenant_id          uuid NOT NULL REFERENCES tenants (id),
    user_id            uuid REFERENCES users (id),
    name               varchar(160) NOT NULL,
    cro                varchar(20),
    cro_state          char(2),
    specialty          varchar(80),
    color              varchar(7) NOT NULL DEFAULT '#9C7A5B' CHECK (color ~ '^#[0-9A-Fa-f]{6}$'),
    phone              varchar(20),
    email              varchar(254),
    commission_percent numeric(5, 2) CHECK (commission_percent BETWEEN 0 AND 100),
    active             boolean NOT NULL DEFAULT true,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    version            bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id)
);
SELECT enable_tenant_rls('professionals');

CREATE TABLE professional_schedules (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    professional_id uuid NOT NULL,
    weekday         smallint NOT NULL CHECK (weekday BETWEEN 1 AND 7), -- ISO: 1 = segunda ... 7 = domingo
    start_time      time NOT NULL,
    end_time        time NOT NULL,
    CHECK (start_time < end_time),
    FOREIGN KEY (tenant_id, professional_id) REFERENCES professionals (tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX professional_schedules_prof_idx ON professional_schedules (tenant_id, professional_id, weekday);
SELECT enable_tenant_rls('professional_schedules');

CREATE TABLE rooms (
    id         uuid PRIMARY KEY,
    tenant_id  uuid NOT NULL REFERENCES tenants (id),
    name       varchar(80) NOT NULL,
    active     boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, name)
);
SELECT enable_tenant_rls('rooms');

CREATE TABLE procedures (
    id               uuid PRIMARY KEY,
    tenant_id        uuid NOT NULL REFERENCES tenants (id),
    code             varchar(20),
    name             varchar(160) NOT NULL,
    category         varchar(60),
    default_price    numeric(12, 2) NOT NULL DEFAULT 0 CHECK (default_price >= 0),
    duration_minutes integer NOT NULL DEFAULT 30 CHECK (duration_minutes BETWEEN 5 AND 480),
    active           boolean NOT NULL DEFAULT true,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, name)
);
SELECT enable_tenant_rls('procedures');

-- ---------------------------------------------------------------------------------------------
-- Pacientes
-- ---------------------------------------------------------------------------------------------
CREATE TABLE patients (
    id               uuid PRIMARY KEY,
    tenant_id        uuid NOT NULL REFERENCES tenants (id),
    full_name        varchar(160) NOT NULL,
    social_name      varchar(160),
    birth_date       date,
    cpf              varchar(11) CHECK (cpf ~ '^[0-9]{11}$'),
    rg               varchar(20),
    sex              varchar(20) NOT NULL DEFAULT 'NOT_INFORMED'
                     CHECK (sex IN ('FEMALE', 'MALE', 'OTHER', 'NOT_INFORMED')),
    phone            varchar(20),
    mobile           varchar(20),
    email            varchar(254),
    zip_code         varchar(9),
    street           varchar(160),
    number           varchar(20),
    complement       varchar(80),
    district         varchar(80),
    city             varchar(100),
    state            char(2),
    occupation       varchar(100),
    referral_source  varchar(100),
    health_insurance varchar(100),
    insurance_number varchar(50),
    guardian_name    varchar(160),
    guardian_cpf     varchar(11) CHECK (guardian_cpf ~ '^[0-9]{11}$'),
    guardian_phone   varchar(20),
    notes            text,
    allow_email      boolean NOT NULL DEFAULT true,
    allow_sms        boolean NOT NULL DEFAULT false,
    allow_whatsapp   boolean NOT NULL DEFAULT false,
    active           boolean NOT NULL DEFAULT true,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    version          bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX patients_cpf_per_clinic ON patients (tenant_id, cpf) WHERE cpf IS NOT NULL;
CREATE INDEX patients_name_idx ON patients (tenant_id, lower(full_name));
SELECT enable_tenant_rls('patients');

-- ---------------------------------------------------------------------------------------------
-- Agenda
-- ---------------------------------------------------------------------------------------------
CREATE TABLE appointments (
    id                      uuid PRIMARY KEY,
    tenant_id               uuid NOT NULL REFERENCES tenants (id),
    patient_id              uuid NOT NULL,
    professional_id         uuid NOT NULL,
    room_id                 uuid,
    procedure_id            uuid,
    starts_at               timestamptz NOT NULL,
    ends_at                 timestamptz NOT NULL,
    status                  varchar(20) NOT NULL DEFAULT 'SCHEDULED'
                            CHECK (status IN ('SCHEDULED', 'CONFIRMED', 'ARRIVED', 'IN_PROGRESS',
                                              'COMPLETED', 'NO_SHOW', 'CANCELLED')),
    notes                   text,
    cancellation_reason     varchar(300),
    confirmed_at            timestamptz,
    reminder_sent_at        timestamptz,
    confirmation_token_hash char(64) UNIQUE,
    created_by              uuid,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    version                 bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, id),
    CHECK (ends_at > starts_at),
    FOREIGN KEY (tenant_id, patient_id) REFERENCES patients (tenant_id, id),
    FOREIGN KEY (tenant_id, professional_id) REFERENCES professionals (tenant_id, id),
    FOREIGN KEY (tenant_id, room_id) REFERENCES rooms (tenant_id, id),
    FOREIGN KEY (tenant_id, procedure_id) REFERENCES procedures (tenant_id, id),
    -- O banco impede dois atendimentos ativos sobrepostos para o mesmo profissional ou a mesma sala.
    CONSTRAINT appointments_professional_overlap EXCLUDE USING gist (
        tenant_id WITH =, professional_id WITH =, tstzrange(starts_at, ends_at) WITH &&
    ) WHERE (status NOT IN ('CANCELLED', 'NO_SHOW')),
    CONSTRAINT appointments_room_overlap EXCLUDE USING gist (
        tenant_id WITH =, room_id WITH =, tstzrange(starts_at, ends_at) WITH &&
    ) WHERE (room_id IS NOT NULL AND status NOT IN ('CANCELLED', 'NO_SHOW'))
);
CREATE INDEX appointments_period_idx ON appointments (tenant_id, starts_at);
CREATE INDEX appointments_patient_idx ON appointments (tenant_id, patient_id, starts_at DESC);
ALTER TABLE appointments ENABLE ROW LEVEL SECURITY;
ALTER TABLE appointments FORCE ROW LEVEL SECURITY;
-- Além da clínica ativa, o paciente com o link de confirmação lê/atualiza apenas a própria consulta.
CREATE POLICY tenant_isolation ON appointments
    USING (tenant_id = app_current_tenant_id() OR confirmation_token_hash = app_current_access_token_hash())
    WITH CHECK (tenant_id = app_current_tenant_id());

-- Histórico de alterações do agendamento (somente inclusão).
CREATE TABLE appointment_events (
    id             uuid PRIMARY KEY,
    tenant_id      uuid NOT NULL,
    appointment_id uuid NOT NULL,
    action         varchar(40) NOT NULL,
    details        jsonb,
    actor_user_id  uuid,
    occurred_at    timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id, appointment_id) REFERENCES appointments (tenant_id, id)
);
CREATE INDEX appointment_events_idx ON appointment_events (tenant_id, appointment_id, occurred_at);
SELECT enable_tenant_rls('appointment_events');
REVOKE UPDATE, DELETE ON appointment_events FROM ${app_db_user};
