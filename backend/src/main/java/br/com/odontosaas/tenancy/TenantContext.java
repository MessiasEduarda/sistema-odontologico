package br.com.odontosaas.tenancy;

import br.com.odontosaas.shared.ApiException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class TenantContext {

    public record State(
            UUID tenantId, UUID userId, String accessTokenHash, boolean platformAdmin, UUID portalAccountId) {
        static final State EMPTY = new State(null, null, null, false, null);

        public State(UUID tenantId, UUID userId, String accessTokenHash, boolean platformAdmin) {
            this(tenantId, userId, accessTokenHash, platformAdmin, null);
        }

        public String key() {
            return tenantId + "|" + userId + "|" + accessTokenHash + "|" + platformAdmin + "|" + portalAccountId;
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

    public static void set(UUID tenantId, UUID userId, boolean platformAdmin) {
        CURRENT.set(new State(tenantId, userId, null, platformAdmin));
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static <T> T runAs(UUID tenantId, UUID userId, Supplier<T> action) {
        return runWith(new State(tenantId, userId, null, false), action);
    }

    public static <T> T runAsPlatform(Supplier<T> action) {
        return runWith(new State(null, null, null, true), action);
    }

    public static <T> T runAsPortalAccount(UUID accountId, Supplier<T> action) {
        return runWith(new State(null, null, null, false, accountId), action);
    }

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
