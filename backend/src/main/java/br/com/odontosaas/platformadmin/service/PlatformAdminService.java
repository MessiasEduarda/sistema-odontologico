package br.com.odontosaas.platformadmin.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.platformadmin.dto.response.Metrics;
import br.com.odontosaas.platformadmin.dto.response.PlatformEvent;
import br.com.odontosaas.platformadmin.dto.response.TenantRow;
import br.com.odontosaas.platformadmin.repository.PlatformAdminRepository;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.tenancy.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAdminService {

    private final PlatformAdminRepository platform;
    private final AuditService audit;

    public PlatformAdminService(PlatformAdminRepository platform, AuditService audit) {
        this.platform = platform;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<TenantRow> tenants(String query) {
        String like =
                query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase() + "%";
        return platform.tenants(like);
    }

    @Transactional
    public void setTenantStatus(UUID tenantId, String status) {
        int updated = platform.updateTenantStatus(tenantId, status);
        if (updated == 0) throw ApiException.notFound("Clínica");
        audit.recordGlobal(
                AuditAction.TENANT_STATUS_CHANGED,
                TenantContext.userId().orElse(null),
                "tenant",
                tenantId,
                Map.of("status", status));
    }

    @Transactional(readOnly = true)
    public Metrics metrics() {
        return platform.metrics();
    }

    @Transactional(readOnly = true)
    public List<PlatformEvent> events(String action) {
        return platform.events(action);
    }
}
