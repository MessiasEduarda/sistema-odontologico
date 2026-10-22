package br.com.odontosaas.scheduling.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.scheduling.dto.request.ScheduleBlockRequest;
import br.com.odontosaas.scheduling.dto.response.ScheduleBlockView;
import br.com.odontosaas.scheduling.repository.ScheduleBlockRepository;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.TenantRefs;
import br.com.odontosaas.tenancy.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleBlockService {

    private static final Duration MAX_BLOCK = Duration.ofDays(92);

    private final ScheduleBlockRepository blocks;
    private final TenantRefs refs;
    private final AuditService audit;

    public ScheduleBlockService(ScheduleBlockRepository blocks, TenantRefs refs, AuditService audit) {
        this.blocks = blocks;
        this.refs = refs;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ScheduleBlockView> list(Instant from, Instant to) {
        if (from == null || to == null) {
            throw ApiException.badRequest("PERIOD_REQUIRED", "Informe o período (from e to).");
        }
        return blocks.list(TenantContext.requireTenantId(), from, to);
    }

    @Transactional
    public ScheduleBlockView create(ScheduleBlockRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        if (!r.endsAt().isAfter(r.startsAt())) {
            throw ApiException.invalidField("endsAt", "O fim deve ser depois do início.");
        }
        if (Duration.between(r.startsAt(), r.endsAt()).compareTo(MAX_BLOCK) > 0) {
            throw ApiException.invalidField("endsAt", "Um bloqueio pode durar no máximo 92 dias.");
        }
        refs.require("professionals", tenantId, r.professionalId(), "Profissional");
        UUID id = Ids.newId();
        blocks.insert(
                id,
                tenantId,
                r.professionalId(),
                r.startsAt(),
                r.endsAt(),
                r.reason().strip(),
                TenantContext.userId().orElse(null));
        audit.record(AuditAction.RECORD_CREATED, "schedule-block", id, Map.of());
        return blocks.get(tenantId, id);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        int deleted = blocks.delete(tenantId, id);
        if (deleted == 0) throw ApiException.notFound("Bloqueio");
        audit.record(AuditAction.RECORD_DEACTIVATED, "schedule-block", id, Map.of());
    }

    Optional<String> blockingReason(UUID tenantId, UUID professionalId, Instant startsAt, Instant endsAt) {
        return blocks.blockingReason(tenantId, professionalId, startsAt, endsAt);
    }

    List<Instant[]> blockedPeriods(UUID tenantId, UUID professionalId, Instant from, Instant to) {
        return blocks.blockedPeriods(tenantId, professionalId, from, to);
    }
}
