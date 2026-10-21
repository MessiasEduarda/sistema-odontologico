package br.com.odontosaas.subscription.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.subscription.dto.request.PlanRequest;
import br.com.odontosaas.subscription.dto.request.SubscriptionRequest;
import br.com.odontosaas.subscription.dto.response.Billing;
import br.com.odontosaas.subscription.dto.response.PlanView;
import br.com.odontosaas.subscription.dto.response.SubscriptionView;
import br.com.odontosaas.tenancy.TenantContext;
import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Planos comerciais, assinaturas e funcionalidades por plano. Módulos essenciais (agenda, pacientes, prontuário,
 * tratamentos, financeiro) existem em todos os planos; os demais dependem do plano.
 */
@Service
public class SubscriptionService {

    public static final Set<String> FEATURES = Set.of(
            "AGENDA",
            "PATIENTS",
            "CLINICAL",
            "TREATMENTS",
            "FINANCE",
            "INVENTORY",
            "COMMUNICATION",
            "REPORTS",
            "PATIENT_PORTAL");

    public static final String DEFAULT_PLAN = "PROFISSIONAL";

    /** Dias de tolerância depois do vencimento: a clínica é avisada e, passado esse prazo, o acesso é bloqueado. */
    public static final int GRACE_DAYS = 5;

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    /**
     * Vencimento: inadimplente (marcado pela plataforma), período pago encerrado ou avaliação terminada sem
     * pagamento. Cancelada é tratada à parte (bloqueio definitivo).
     */
    public static Billing billing(
            String status, LocalDate trialEndsAt, LocalDate currentPeriodEnd, Instant updatedAt, LocalDate today) {
        LocalDate due = switch (status) {
            case "PAST_DUE" -> currentPeriodEnd != null ? currentPeriodEnd : LocalDate.ofInstant(updatedAt, ZONE);
            case "ACTIVE" -> currentPeriodEnd;
            // Com período pago informado, vale ele (a avaliação já virou cobrança); senão, o fim da avaliação.
            case "TRIALING" -> currentPeriodEnd != null ? currentPeriodEnd : trialEndsAt;
            default -> null;
        };
        if (due == null || (!"PAST_DUE".equals(status) && !due.isBefore(today))) return Billing.OK;
        LocalDate blockedFrom = due.plusDays(GRACE_DAYS + 1L);
        return new Billing(today.isBefore(blockedFrom) ? "DUE" : "BLOCKED", due, blockedFrom);
    }

    private final JdbcClient jdbc;
    private final AuditService audit;

    public SubscriptionService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    private static List<String> features(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }

    @Transactional(readOnly = true)
    public List<PlanView> plans() {
        return jdbc.sql("""
                        select p.id, p.code, p.name, p.price_monthly, p.max_professionals, p.max_patients, p.features,
                               p.active, (select count(*) from subscriptions s where s.plan_id = p.id
                                   and s.status <> 'CANCELLED') as tenants
                        from plans p order by p.price_monthly, p.name
                        """)
                .query((rs, n) -> new PlanView(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getBigDecimal("price_monthly"),
                        (Integer) rs.getObject("max_professionals"),
                        (Integer) rs.getObject("max_patients"),
                        features(rs.getArray("features")),
                        rs.getBoolean("active"),
                        rs.getLong("tenants")))
                .list();
    }

    @Transactional
    public PlanView savePlan(UUID id, PlanRequest r) {
        for (String f : r.features()) {
            if (!FEATURES.contains(f)) throw ApiException.invalidField("features", "Funcionalidade inválida: " + f);
        }
        String[] features = r.features().toArray(String[]::new);
        if (id == null) {
            id = Ids.newId();
            jdbc.sql("""
                            insert into plans (id, code, name, price_monthly, max_professionals, max_patients, features,
                                active) values (?, ?, ?, ?, ?, ?, ?, ?)
                            """)
                    .params(
                            id,
                            r.code(),
                            r.name().strip(),
                            r.priceMonthly(),
                            r.maxProfessionals(),
                            r.maxPatients(),
                            features,
                            r.active())
                    .update();
        } else {
            int updated = jdbc.sql("""
                            update plans set code = ?, name = ?, price_monthly = ?, max_professionals = ?,
                                max_patients = ?, features = ?, active = ? where id = ?
                            """)
                    .params(
                            r.code(),
                            r.name().strip(),
                            r.priceMonthly(),
                            r.maxProfessionals(),
                            r.maxPatients(),
                            features,
                            r.active(),
                            id)
                    .update();
            if (updated == 0) throw ApiException.notFound("Plano");
        }
        audit.recordGlobal(AuditAction.RECORD_UPDATED, TenantContext.userId().orElse(null), "plan", id, Map.of());
        UUID saved = id;
        return plans().stream().filter(p -> p.id().equals(saved)).findFirst().orElseThrow();
    }

    /** Assinatura inicial (avaliação de 30 dias) de uma clínica nova. */
    @Transactional
    public void startTrial(UUID tenantId, String planCode) {
        UUID planId = planId(planCode == null ? DEFAULT_PLAN : planCode);
        jdbc.sql("""
                        insert into subscriptions (tenant_id, plan_id, status, trial_ends_at)
                        values (?, ?, 'TRIALING', current_date + 30) on conflict (tenant_id) do nothing
                        """).params(tenantId, planId).update();
    }

    private UUID planId(String code) {
        return jdbc.sql("select id from plans where code = ?")
                .param(code)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> ApiException.invalidField("planCode", "Plano inexistente."));
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionView> subscription(UUID tenantId) {
        return jdbc.sql("""
                        select s.tenant_id, p.code as plan_code, p.name as plan_name, p.price_monthly, s.status,
                               s.trial_ends_at, s.current_period_end, p.features, p.max_professionals, p.max_patients,
                               s.notes, s.updated_at, s.payment_url
                        from subscriptions s join plans p on p.id = s.plan_id where s.tenant_id = ?
                        """)
                .param(tenantId)
                .query((rs, n) -> {
                    String status = rs.getString("status");
                    LocalDate trialEndsAt = rs.getObject("trial_ends_at", LocalDate.class);
                    LocalDate periodEnd = rs.getObject("current_period_end", LocalDate.class);
                    Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
                    return new SubscriptionView(
                            rs.getObject("tenant_id", UUID.class),
                            rs.getString("plan_code"),
                            rs.getString("plan_name"),
                            rs.getBigDecimal("price_monthly"),
                            status,
                            trialEndsAt,
                            periodEnd,
                            features(rs.getArray("features")),
                            (Integer) rs.getObject("max_professionals"),
                            (Integer) rs.getObject("max_patients"),
                            0,
                            0,
                            rs.getString("notes"),
                            updatedAt,
                            rs.getString("payment_url"),
                            billing(status, trialEndsAt, periodEnd, updatedAt, LocalDate.now(ZONE)));
                })
                .optional();
    }

    /** Assinatura da clínica ATIVA, com o uso atual dos limites. */
    @Transactional(readOnly = true)
    public Optional<SubscriptionView> currentClinic() {
        UUID tenantId = TenantContext.requireTenantId();
        return subscription(tenantId).map(s -> {
            long professionals = jdbc.sql("select count(*) from professionals where tenant_id = ? and active")
                    .param(tenantId)
                    .query(Long.class)
                    .single();
            long patients = jdbc.sql("select count(*) from patients where tenant_id = ? and active")
                    .param(tenantId)
                    .query(Long.class)
                    .single();
            return new SubscriptionView(
                    s.tenantId(),
                    s.planCode(),
                    s.planName(),
                    s.priceMonthly(),
                    s.status(),
                    s.trialEndsAt(),
                    s.currentPeriodEnd(),
                    s.features(),
                    s.maxProfessionals(),
                    s.maxPatients(),
                    professionals,
                    patients,
                    null,
                    s.updatedAt(),
                    s.paymentUrl(),
                    s.billing());
        });
    }

    @Transactional
    public SubscriptionView update(UUID tenantId, SubscriptionRequest r) {
        UUID planId = planId(r.planCode());
        jdbc.sql("""
                        insert into subscriptions (tenant_id, plan_id, status, trial_ends_at, current_period_end, notes,
                            payment_url)
                        values (?, ?, ?, ?, ?, ?, ?)
                        on conflict (tenant_id) do update set plan_id = excluded.plan_id, status = excluded.status,
                            trial_ends_at = excluded.trial_ends_at, current_period_end = excluded.current_period_end,
                            notes = excluded.notes, payment_url = excluded.payment_url, updated_at = now()
                        """)
                .params(
                        tenantId,
                        planId,
                        r.status(),
                        r.trialEndsAt(),
                        r.currentPeriodEnd(),
                        r.notes(),
                        r.paymentUrl() == null || r.paymentUrl().isBlank()
                                ? null
                                : r.paymentUrl().strip())
                .update();
        audit.recordGlobal(
                AuditAction.SUBSCRIPTION_CHANGED,
                TenantContext.userId().orElse(null),
                "tenant",
                tenantId,
                Map.of("plan", r.planCode(), "status", r.status()));
        return subscription(tenantId).orElseThrow();
    }
}
