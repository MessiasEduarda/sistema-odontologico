package br.com.odontosaas.tenancy.support;

import br.com.odontosaas.tenancy.TenantContext;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;

public class TenantAwareDataSource extends DelegatingDataSource {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareDataSource.class);

    private static final String APPLY_SQL = "select set_config('app.tenant_id', ?, false),"
            + " set_config('app.user_id', ?, false),"
            + " set_config('app.invitation_token_hash', ?, false),"
            + " set_config('app.access_token_hash', ?, false),"
            + " set_config('app.platform_admin', ?, false)";

    private static final Set<String> STATEMENT_METHODS = Set.of("createStatement", "prepareStatement", "prepareCall");

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(obtainTargetDataSource().getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(obtainTargetDataSource().getConnection(username, password));
    }

    private static Connection wrap(Connection target) {
        return (Connection) Proxy.newProxyInstance(
                TenantAwareDataSource.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                new ContextHandler(target));
    }

    private static final class ContextHandler implements InvocationHandler {

        private final Connection target;
        private String appliedKey;
        private boolean dirty;

        ContextHandler(Connection target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            switch (name) {
                case "unwrap" -> {
                    if (((Class<?>) args[0]).isInstance(proxy)) return proxy;
                }
                case "isWrapperFor" -> {
                    if (((Class<?>) args[0]).isInstance(proxy)) return true;
                }
                case "close" -> {
                    resetQuietly();
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                default -> {
                    if (STATEMENT_METHODS.contains(name)) {
                        ensureContext();
                    }
                }
            }
            try {
                Object result = method.invoke(target, args);
                if (name.equals("rollback")) {
                    appliedKey = null;
                }
                return result;
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }

        private void ensureContext() throws SQLException {
            TenantContext.State state = TenantContext.current();
            String key = state.key();
            if (key.equals(appliedKey)) return;
            apply(
                    state.tenantId() == null ? "" : state.tenantId().toString(),
                    state.userId() == null ? "" : state.userId().toString(),
                    state.accessTokenHash() == null ? "" : state.accessTokenHash(),
                    state.platformAdmin() ? "true" : "");
            appliedKey = key;
            dirty = true;
        }

        private void resetQuietly() {
            if (!dirty) return;
            try {
                if (!target.isClosed()) {
                    boolean manualCommit = !target.getAutoCommit();
                    if (manualCommit) {
                        target.rollback();
                    }
                    apply("", "", "", "");
                    if (manualCommit) {
                        target.commit();
                    }
                }
            } catch (SQLException e) {
                log.warn("Falha ao limpar contexto de tenant da conexão", e);
            }
            appliedKey = null;
            dirty = false;
        }

        private void apply(String tenantId, String userId, String tokenHash, String platformAdmin) throws SQLException {
            try (PreparedStatement ps = target.prepareStatement(APPLY_SQL)) {
                ps.setString(1, tenantId);
                ps.setString(2, userId);
                ps.setString(3, tokenHash);
                ps.setString(4, tokenHash);
                ps.setString(5, platformAdmin);
                ps.execute();
            }
        }
    }
}
