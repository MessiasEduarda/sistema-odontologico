package br.com.odontosaas.tenancy;

import br.com.odontosaas.shared.ApiException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Contexto de isolamento da thread atual: clínica (tenant) ativa e usuário autenticado. É preenchido EXCLUSIVAMENTE
 * pelo servidor (filtro de sessão ou fluxos internos validados) — nunca a partir de dados enviados pelo cliente.
 *
 * <p>O {@link TenantAwareDataSource} repassa estes valores ao PostgreSQL ({@code app.tenant_id}, {@code app.user_id}),
 * onde as políticas de Row-Level Security os aplicam.
 */
public final class TenantContext {

    public record State(UUID tenantId, UUID userId, String accessTokenHash, boolean platformAdmin) {
        static final State EMPTY = new State(null, null, null, false);

        public String key() {
            return tenantId + "|" + userId + "|" + accessTokenHash + "|" + platformAdmin;
        }
    }

    private static final ThreadLocal<State> CURRENT = ThreadLocal.withInitial(() -> State.EMPTY);

    private TenantContext() {}

    public static State current() {
        return CURRENT.get();
    }

    public static Optional<UUID> tenantId() {
        return Optional.ofNullable(CURRENT.get().tenantId());
    }

    public static Optional<UUID> userId() {
        return Optional.ofNullable(CURRENT.get().userId());
    }

    /** Clínica ativa; falha com 403 se o usuário não tiver selecionado uma clínica. */
    public static UUID requireTenantId() {
        UUID tenantId = CURRENT.get().tenantId();
        if (tenantId == null) {
            throw ApiException.businessRule("NO_ACTIVE_CLINIC", "Selecione uma clínica para continuar.");
        }
        return tenantId;
    }

    public static void set(UUID tenantId, UUID userId) {
        CURRENT.set(new State(tenantId, userId, null, false));
    }

    /** Contexto da requisição autenticada; platformAdmin libera apenas a leitura da auditoria de plataforma. */
    public static void set(UUID tenantId, UUID userId, boolean platformAdmin) {
        CURRENT.set(new State(tenantId, userId, null, platformAdmin));
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Executa uma operação interna com outro contexto e restaura o anterior. Usar apenas depois de validar no servidor
     * que a operação é legítima (ex.: convite com token válido, criação de clínica pelo administrador da plataforma).
     * Gravações JPA feitas aqui devem ser sincronizadas (flush) antes de sair.
     */
    public static <T> T runAs(UUID tenantId, UUID userId, Supplier<T> action) {
        return runWith(new State(tenantId, userId, null, false), action);
    }

    /**
     * Contexto restrito a UM registro identificado por token público (convite, portal do paciente, assinatura de
     * termo, confirmação de consulta). As políticas RLS liberam apenas a linha com esse hash.
     */
    public static <T> T runWithToken(String tokenHash, Supplier<T> action) {
        return runWith(new State(null, null, tokenHash, false), action);
    }

    private static <T> T runWith(State state, Supplier<T> action) {
        State previous = CURRENT.get();
        CURRENT.set(state);
        try {
            return action.get();
        } finally {
            CURRENT.set(previous);
        }
    }
}
