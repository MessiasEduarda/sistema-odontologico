-- V1: fundação do modelo de dados.
-- Convenções: IDs uuid (v7 gerados pela aplicação), datas em timestamptz (UTC),
-- toda tabela de dados de clínica terá tenant_id + Row-Level Security (a partir da Fase 2).

-- Tenant ativo da transação corrente, definido pela aplicação com SET LOCAL app.tenant_id.
-- Retorna NULL quando não há contexto, o que faz as políticas de RLS negarem acesso.
CREATE FUNCTION app_current_tenant_id() RETURNS uuid
    LANGUAGE sql STABLE
AS $$
    SELECT nullif(current_setting('app.tenant_id', true), '')::uuid
$$;

-- Clínicas (tenants) da plataforma. Tabela global: não pertence a um tenant.
CREATE TABLE tenants (
    id          uuid        PRIMARY KEY,
    name        varchar(160) NOT NULL,
    slug        citext      NOT NULL UNIQUE,
    document    varchar(18),
    status      varchar(20) NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CANCELLED')),
    time_zone   varchar(64) NOT NULL DEFAULT 'America/Sao_Paulo',
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    version     bigint      NOT NULL DEFAULT 0,
    CONSTRAINT tenants_slug_format CHECK (slug ~ '^[a-z0-9](-?[a-z0-9])*$')
);

COMMENT ON TABLE tenants IS 'Clínicas (organizações) da plataforma';
