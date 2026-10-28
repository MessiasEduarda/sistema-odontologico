package br.com.odontosaas.subscription.config;

import br.com.odontosaas.shared.web.ProblemResponseWriter;
import br.com.odontosaas.subscription.dto.response.SubscriptionView;
import br.com.odontosaas.subscription.service.SubscriptionService;
import br.com.odontosaas.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.PathContainer;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.UriUtils;

@Configuration
class PlanEnforcement implements WebMvcConfigurer {

    private static final Map<String, String> GATED = Map.of(
            "/api/v1/inventory", "INVENTORY",
            "/api/v1/communication", "COMMUNICATION",
            "/api/v1/reports", "REPORTS");

    static final String SUBSCRIPTION_PATH = "/api/v1/clinic/subscription";
    static final String SUPPORT_PATH = "/api/v1/support";

    private final SubscriptionService subscriptions;

    PlanEnforcement(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
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
        String path = normalizedPath(request);
        if (tenant.isEmpty() || path.equals(SUBSCRIPTION_PATH) || isUnder(path, SUPPORT_PATH)) return true;

        Optional<SubscriptionView> sub = subscriptions.subscription(tenant.get());
        if (sub.isPresent() && sub.get().billing().blocked()) {
            ProblemResponseWriter.write(
                    response,
                    402,
                    "SUBSCRIPTION_PAST_DUE",
                    "Pagamento pendente",
                    "O acesso da clínica está suspenso por falta de pagamento da assinatura.");
            return false;
        }
        if (sub.isPresent() && "CANCELLED".equals(sub.get().status())) {
            return deny(response, "SUBSCRIPTION_INACTIVE", "A assinatura desta clínica está cancelada.");
        }
        List<String> features = sub.map(SubscriptionView::features).orElse(List.of());
        for (var gate : GATED.entrySet()) {
            if (isUnder(path, gate.getKey()) && !features.contains(gate.getValue())) {
                return deny(response, "FEATURE_NOT_IN_PLAN", "Este recurso não está incluído no plano da clínica.");
            }
        }
        if (path.contains("/portal-access") && !features.contains("PATIENT_PORTAL")) {
            return deny(response, "FEATURE_NOT_IN_PLAN", "O portal do paciente não está incluído no plano.");
        }
        return true;
    }

    static String normalizedPath(HttpServletRequest request) {
        if (ServletRequestPathUtils.hasParsedRequestPath(request)) {
            StringBuilder path = new StringBuilder();
            for (PathContainer.Element element : ServletRequestPathUtils.getParsedRequestPath(request)
                    .pathWithinApplication()
                    .elements()) {
                path.append(
                        element instanceof PathContainer.PathSegment segment
                                ? segment.valueToMatch()
                                : element.value());
            }
            return path.toString();
        }
        return StringUtils.cleanPath(UriUtils.decode(request.getRequestURI(), StandardCharsets.UTF_8));
    }

    private static boolean isUnder(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static boolean deny(HttpServletResponse response, String code, String detail) throws Exception {
        ProblemResponseWriter.write(response, 403, code, "Indisponível no plano", detail);
        return false;
    }
}
