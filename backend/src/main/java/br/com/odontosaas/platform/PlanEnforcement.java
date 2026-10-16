package br.com.odontosaas.platform;

import br.com.odontosaas.platform.SubscriptionService.SubscriptionView;
import br.com.odontosaas.shared.web.ProblemResponseWriter;
import br.com.odontosaas.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Aplica o plano da clínica no SERVIDOR (esconder no front-end é só conveniência):
 * <ul>
 *   <li>assinatura cancelada bloqueia a área da clínica (403 SUBSCRIPTION_INACTIVE);
 *   <li>funcionalidade fora do plano → 403 FEATURE_NOT_IN_PLAN;
 *   <li>limites de profissionais e pacientes ativos → 403 PLAN_LIMIT_REACHED ao cadastrar.
 * </ul>
 */
@Configuration
class PlanEnforcement implements WebMvcConfigurer {

    /** Prefixo de rota → funcionalidade exigida. */
    private static final Map<String, String> GATED = Map.of(
            "/api/v1/inventory", "INVENTORY",
            "/api/v1/communication", "COMMUNICATION",
            "/api/v1/reports", "REPORTS");

    private static final List<String> CLINIC_PREFIXES = List.of(
            "/api/v1/patients",
            "/api/v1/appointments",
            "/api/v1/treatment-plans",
            "/api/v1/finance",
            "/api/v1/inventory",
            "/api/v1/communication",
            "/api/v1/reports",
            "/api/v1/dashboard",
            "/api/v1/suppliers");

    private final SubscriptionService subscriptions;
    private final JdbcClient jdbc;

    PlanEnforcement(SubscriptionService subscriptions, JdbcClient jdbc) {
        this.subscriptions = subscriptions;
        this.jdbc = jdbc;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                            throws Exception {
                        return check(request, response);
                    }
                })
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/auth/**", "/api/v1/public/**", "/api/v1/platform/**");
    }

    private boolean check(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Optional<UUID> tenant = TenantContext.tenantId();
        String path = request.getRequestURI();
        if (tenant.isEmpty() || CLINIC_PREFIXES.stream().noneMatch(path::startsWith)) return true;

        Optional<SubscriptionView> sub = subscriptions.subscription(tenant.get());
        if (sub.isPresent() && "CANCELLED".equals(sub.get().status())) {
            return deny(response, "SUBSCRIPTION_INACTIVE", "A assinatura desta clínica está cancelada.");
        }
        List<String> features = sub.map(SubscriptionView::features).orElse(List.of());
        for (var gate : GATED.entrySet()) {
            if (path.startsWith(gate.getKey()) && !features.contains(gate.getValue())) {
                return deny(response, "FEATURE_NOT_IN_PLAN", "Este recurso não está incluído no plano da clínica.");
            }
        }
        if (path.contains("/portal-access") && !features.contains("PATIENT_PORTAL")) {
            return deny(response, "FEATURE_NOT_IN_PLAN", "O portal do paciente não está incluído no plano.");
        }
        if ("POST".equals(request.getMethod()) && sub.isPresent()) {
            if (path.equals("/api/v1/patients") && reached(sub.get().maxPatients(), "patients", tenant.get())) {
                return deny(response, "PLAN_LIMIT_REACHED", "Limite de pacientes ativos do plano atingido.");
            }
        }
        return true;
    }

    private boolean reached(Integer limit, String table, UUID tenantId) {
        if (limit == null) return false;
        long count = jdbc.sql("select count(*) from " + table + " where tenant_id = ? and active")
                .param(tenantId)
                .query(Long.class)
                .single();
        return count >= limit;
    }

    /** Limite de profissionais (rota fora dos prefixos acima). */
    boolean professionalsLimitReached(UUID tenantId) {
        return subscriptions
                .subscription(tenantId)
                .map(s -> reached(s.maxProfessionals(), "professionals", tenantId))
                .orElse(false);
    }

    private static boolean deny(HttpServletResponse response, String code, String detail) throws Exception {
        ProblemResponseWriter.write(response, 403, code, "Indisponível no plano", detail);
        return false;
    }

    /** Interceptor dedicado ao cadastro de profissionais. */
    @Configuration
    static class ProfessionalsLimit implements WebMvcConfigurer {
        private final PlanEnforcement enforcement;

        ProfessionalsLimit(PlanEnforcement enforcement) {
            this.enforcement = enforcement;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new HandlerInterceptor() {
                        @Override
                        public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler)
                                throws Exception {
                            Optional<UUID> tenant = TenantContext.tenantId();
                            if ("POST".equals(req.getMethod())
                                    && tenant.isPresent()
                                    && enforcement.professionalsLimitReached(tenant.get())) {
                                return deny(
                                        res, "PLAN_LIMIT_REACHED", "Limite de profissionais ativos do plano atingido.");
                            }
                            return true;
                        }
                    })
                    .addPathPatterns("/api/v1/clinic/professionals");
        }
    }
}
