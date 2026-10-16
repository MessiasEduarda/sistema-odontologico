-- V7: leitura da trilha de auditoria da PLATAFORMA (eventos sem clínica: login, criação de clínicas, planos).
-- O administrador da plataforma lê apenas eventos com tenant_id nulo; eventos de clínica continuam isolados.
CREATE FUNCTION app_is_platform_admin() RETURNS boolean
    LANGUAGE sql STABLE
AS $$
    SELECT coalesce(current_setting('app.platform_admin', true), '') = 'true'
$$;

CREATE POLICY audit_events_platform_read ON audit_events FOR SELECT
    USING (tenant_id IS NULL AND app_is_platform_admin());

CREATE INDEX audit_events_platform_idx ON audit_events (occurred_at DESC) WHERE tenant_id IS NULL;
