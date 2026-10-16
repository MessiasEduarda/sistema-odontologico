package br.com.odontosaas.audit;

import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.RequestInfo;
import br.com.odontosaas.tenancy.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registra eventos de auditoria na mesma transação da operação auditada (se a operação falhar, o evento também não é
 * gravado). A tabela é somente-inclusão para o papel da aplicação.
 *
 * <p>Nunca inclua em {@code metadata} senhas, tokens, dados clínicos ou documentos pessoais.
 */
@Service
public class AuditService {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public AuditService(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Evento no contexto atual (clínica ativa e usuário do {@link TenantContext}). */
    public void record(AuditAction action, String resourceType, Object resourceId, Map<String, ?> metadata) {
        TenantContext.State state = TenantContext.current();
        insert(state.tenantId(), state.userId(), action, resourceType, resourceId, metadata);
    }

    /** Evento sem clínica (autenticação, plataforma), com ator explícito (pode ser nulo). */
    public void recordGlobal(
            AuditAction action, UUID actorUserId, String resourceType, Object resourceId, Map<String, ?> metadata) {
        insert(null, actorUserId, action, resourceType, resourceId, metadata);
    }

    private void insert(
            UUID tenantId,
            UUID actorUserId,
            AuditAction action,
            String resourceType,
            Object resourceId,
            Map<String, ?> metadata) {
        jdbc.sql("""
                        insert into audit_events
                            (id, tenant_id, actor_user_id, action, resource_type, resource_id, ip, metadata)
                        values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
                        """)
                .params(
                        Ids.newId(),
                        tenantId,
                        actorUserId,
                        action.name(),
                        resourceType,
                        resourceId == null ? null : resourceId.toString(),
                        RequestInfo.clientIp(),
                        metadata == null || metadata.isEmpty() ? null : json.writeValueAsString(metadata))
                .update();
    }
}
