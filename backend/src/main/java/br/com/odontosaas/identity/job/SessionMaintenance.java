package br.com.odontosaas.identity.job;

import br.com.odontosaas.identity.repository.AccessCleanupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionMaintenance {

    private static final Logger log = LoggerFactory.getLogger(SessionMaintenance.class);

    public record Result(int sessions, int loginAttempts, int resetTokens) {}

    private final AccessCleanupRepository cleanup;

    public SessionMaintenance(AccessCleanupRepository cleanup) {
        this.cleanup = cleanup;
    }

    @Transactional
    public Result cleanup() {
        int sessions = cleanup.deleteOldSessions();
        int attempts = cleanup.deleteOldLoginAttempts();
        int tokens = cleanup.deleteOldResetTokens();
        Result result = new Result(sessions, attempts, tokens);
        if (sessions + attempts + tokens > 0) log.info("Limpeza de dados temporários de acesso: {}", result);
        return result;
    }

    @Component
    @ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
    static class Schedule {

        private final SessionMaintenance maintenance;

        Schedule(SessionMaintenance maintenance) {
            this.maintenance = maintenance;
        }

        @Scheduled(fixedDelayString = "${app.jobs.cleanup-delay:PT24H}", initialDelayString = "PT2M")
        void run() {
            try {
                maintenance.cleanup();
            } catch (RuntimeException e) {
                log.error("Falha na limpeza de dados temporários de acesso", e);
            }
        }
    }
}
