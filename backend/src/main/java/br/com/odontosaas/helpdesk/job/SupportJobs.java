package br.com.odontosaas.helpdesk.job;

import br.com.odontosaas.helpdesk.service.SupportNotifier;
import br.com.odontosaas.helpdesk.service.SupportService;
import br.com.odontosaas.helpdesk.service.SupportService.Maintenance;
import br.com.odontosaas.tenancy.TenantContext;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SupportJobs {

    private static final Logger log = LoggerFactory.getLogger(SupportJobs.class);

    private final SupportService support;
    private final SupportNotifier notifier;
    private final TransactionTemplate tx;

    public SupportJobs(SupportService support, SupportNotifier notifier, TransactionTemplate tx) {
        this.support = support;
        this.notifier = notifier;
        this.tx = tx;
    }

    public Maintenance maintenance(Instant now) {
        return TenantContext.runAsPlatform(() -> support.runMaintenance(now));
    }

    public int dispatch() {
        return TenantContext.runAsPlatform(() -> tx.execute(status -> notifier.dispatch()));
    }

    @Component
    @ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
    static class Schedule {

        private final SupportJobs jobs;

        Schedule(SupportJobs jobs) {
            this.jobs = jobs;
        }

        @Scheduled(fixedDelayString = "${app.jobs.support-maintenance-delay:PT15M}", initialDelayString = "PT1M")
        void maintenance() {
            try {
                Maintenance m = jobs.maintenance(Instant.now());
                if (m.reminded() + m.autoResolved() + m.closed() + m.breached() + m.purged() > 0) {
                    log.info(
                            "Suporte: {} lembretes, {} resolvidos sem resposta, {} encerrados, {} prazos estourados,"
                                    + " {} anexos apagados",
                            m.reminded(),
                            m.autoResolved(),
                            m.closed(),
                            m.breached(),
                            m.purged());
                }
            } catch (RuntimeException e) {
                log.error("Falha na rotina do suporte", e);
            }
        }

        @Scheduled(fixedDelayString = "${app.jobs.dispatch-delay:PT1M}", initialDelayString = "PT25S")
        void dispatch() {
            try {
                jobs.dispatch();
            } catch (RuntimeException e) {
                log.error("Falha ao enviar avisos do suporte", e);
            }
        }
    }
}
