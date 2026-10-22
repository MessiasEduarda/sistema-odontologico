package br.com.odontosaas.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.odontosaas.identity.job.SessionMaintenance;
import br.com.odontosaas.support.PostgresIntegrationTest;
import br.com.odontosaas.support.TestData;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SessionMaintenanceTest extends PostgresIntegrationTest {

    @Autowired
    TestData data;

    @Autowired
    SessionMaintenance maintenance;

    private static String hash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }

    @Test
    void removesOnlyOldTemporaryAccessData() {
        UUID user = data.user().id();
        var db = superuserJdbc();
        UUID oldSession = UUID.randomUUID();
        UUID activeSession = UUID.randomUUID();
        db.update(
                "insert into odonto.sessions (id, token_hash, user_id, expires_at) values (?, ?, ?, now() - interval '40 days')",
                oldSession,
                hash(),
                user);
        db.update(
                "insert into odonto.sessions (id, token_hash, user_id, expires_at) values (?, ?, ?, now() + interval '1 day')",
                activeSession,
                hash(),
                user);
        String email = "limpeza" + TestData.unique() + "@teste.example";
        db.update(
                "insert into odonto.login_attempts (email, success, attempted_at) values (?, false, now() - interval '31 days')",
                email);
        db.update("insert into odonto.login_attempts (email, success) values (?, false)", email);

        maintenance.cleanup();

        assertThat(db.queryForObject("select count(*) from odonto.sessions where id = ?", Integer.class, oldSession))
                .isZero();
        assertThat(db.queryForObject("select count(*) from odonto.sessions where id = ?", Integer.class, activeSession))
                .isEqualTo(1);
        assertThat(db.queryForObject(
                        "select count(*) from odonto.login_attempts where email = ?", Integer.class, email))
                .isEqualTo(1);
    }
}
