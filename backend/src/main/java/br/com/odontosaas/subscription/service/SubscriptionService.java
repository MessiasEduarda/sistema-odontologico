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
import br.com.odontosaas.subscription.repository.PlanRepository;
import br.com.odontosaas.subscription.repository.PlanUsageRepository;
import br.com.odontosaas.subscription.repository.SubscriptionRepository;
import br.com.odontosaas.tenancy.TenantContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanUsageRepository usage;
    private final AuditService audit;

    public SubscriptionService(
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            PlanUsageRepository usage,
            AuditService audit) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.usage = usage;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PlanView> plans() {
        return planRepository.list();
    }

    @Transactional
    public PlanView savePlan(UUID id, PlanRequest r) {
        for (String f : r.features()) {
            if (!FEATURES.contains(f)) throw ApiException.invalidField("features", "Funcionalidade inválida: " + f);
        }
        String[] features = r.features().toArray(String[]::new);
        if (id == null) {
            id = Ids.newId();
            planRepository.insert(id, r, features);
        } else {
            int updated = planRepository.update(id, r, features);
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
        subscriptionRepository.insertTrial(tenantId, planId);
    }

    private UUID planId(String code) {
        return planRepository
                .findIdByCode(code)
                .orElseThrow(() -> ApiException.invalidField("planCode", "Plano inexistente."));
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionView> subscription(UUID tenantId) {
        return subscriptionRepository.find(tenantId).map(row -> {
            Instant updatedAt = row.updatedAt();
            return new SubscriptionView(
                    row.tenantId(),
                    row.planCode(),
                    row.planName(),
                    row.priceMonthly(),
                    row.status(),
                    row.trialEndsAt(),
                    row.currentPeriodEnd(),
                    row.features(),
                    row.maxProfessionals(),
                    row.maxPatients(),
                    0,
                    0,
                    row.notes(),
                    updatedAt,
                    row.paymentUrl(),
                    billing(row.status(), row.trialEndsAt(), row.currentPeriodEnd(), updatedAt, LocalDate.now(ZONE)));
        });
    }

    /** Assinatura da clínica ATIVA, com o uso atual dos limites. */
    @Transactional(readOnly = true)
    public Optional<SubscriptionView> currentClinic() {
        UUID tenantId = TenantContext.requireTenantId();
        return subscription(tenantId).map(s -> {
            long professionals = usage.countActiveProfessionals(tenantId);
            long patients = usage.countActivePatients(tenantId);
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
        subscriptionRepository.upsert(
                tenantId,
                planId,
                r.status(),
                r.trialEndsAt(),
                r.currentPeriodEnd(),
                r.notes(),
                r.paymentUrl() == null || r.paymentUrl().isBlank()
                        ? null
                        : r.paymentUrl().strip());
        audit.recordGlobal(
                AuditAction.SUBSCRIPTION_CHANGED,
                TenantContext.userId().orElse(null),
                "tenant",
                tenantId,
                Map.of("plan", r.planCode(), "status", r.status()));
        return subscription(tenantId).orElseThrow();
    }
}
