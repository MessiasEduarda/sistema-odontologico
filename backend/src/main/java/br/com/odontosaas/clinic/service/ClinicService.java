package br.com.odontosaas.clinic.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.clinic.dto.request.ProcedureRequest;
import br.com.odontosaas.clinic.dto.request.ProfessionalRequest;
import br.com.odontosaas.clinic.dto.request.RoomRequest;
import br.com.odontosaas.clinic.dto.request.ScheduleSlot;
import br.com.odontosaas.clinic.dto.request.SettingsRequest;
import br.com.odontosaas.clinic.dto.response.ProcedureView;
import br.com.odontosaas.clinic.dto.response.ProfessionalView;
import br.com.odontosaas.clinic.dto.response.RoomView;
import br.com.odontosaas.clinic.dto.response.SettingsView;
import br.com.odontosaas.clinic.repository.ClinicSettingsRepository;
import br.com.odontosaas.clinic.repository.ProcedureRepository;
import br.com.odontosaas.clinic.repository.ProfessionalRepository;
import br.com.odontosaas.clinic.repository.RoomRepository;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Flags;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.PlanLimits;
import br.com.odontosaas.shared.Versioned;
import br.com.odontosaas.tenancy.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicService {

    private final ClinicSettingsRepository settingsRepository;
    private final ProfessionalRepository professionalRepository;
    private final RoomRepository roomRepository;
    private final ProcedureRepository procedureRepository;
    private final AuditService audit;
    private final PlanLimits planLimits;

    public ClinicService(
            ClinicSettingsRepository settingsRepository,
            ProfessionalRepository professionalRepository,
            RoomRepository roomRepository,
            ProcedureRepository procedureRepository,
            AuditService audit,
            PlanLimits planLimits) {
        this.settingsRepository = settingsRepository;
        this.professionalRepository = professionalRepository;
        this.roomRepository = roomRepository;
        this.procedureRepository = procedureRepository;
        this.audit = audit;
        this.planLimits = planLimits;
    }

    private static UUID tenant() {
        return TenantContext.requireTenantId();
    }

    @Transactional(readOnly = true)
    public SettingsView settings() {
        return settingsRepository.find(tenant());
    }

    @Transactional
    public SettingsView updateSettings(SettingsRequest r) {
        UUID tenantId = tenant();
        settingsRepository.updateTenantName(r.name().strip(), tenantId);
        settingsRepository.upsert(tenantId, r);
        audit.record(AuditAction.RECORD_UPDATED, "clinic_settings", tenantId, Map.of());
        return settings();
    }

    @Transactional(readOnly = true)
    public List<ProfessionalView> professionals(Boolean active) {
        return professionalRepository.list(tenant(), active);
    }

    @Transactional(readOnly = true)
    public ProfessionalView professional(UUID id) {
        return professionalRepository.find(tenant(), id).orElseThrow(() -> ApiException.notFound("Profissional"));
    }

    @Transactional
    public ProfessionalView createProfessional(ProfessionalRequest r) {
        UUID tenantId = tenant();
        requireMemberUser(tenantId, r.userId());
        boolean active = Flags.activeIfOmitted(r.active());
        if (active) {
            planLimits.requireProfessionalSlot(tenantId);
        }
        UUID id = Ids.newId();
        professionalRepository.insert(id, tenantId, r.name().strip(), r, active);
        audit.record(AuditAction.RECORD_CREATED, "professional", id, Map.of());
        return professional(id);
    }

    @Transactional
    public ProfessionalView updateProfessional(UUID id, ProfessionalRequest r) {
        UUID tenantId = tenant();
        requireMemberUser(tenantId, r.userId());
        if (Boolean.TRUE.equals(r.active()) && professionalRepository.isInactive(tenantId, id)) {
            planLimits.requireProfessionalSlot(tenantId);
        }
        int updated = professionalRepository.update(tenantId, id, r.name().strip(), r);
        Versioned.check(updated, professionalRepository.exists(tenantId, id), "Profissional");
        audit.record(AuditAction.RECORD_UPDATED, "professional", id, Map.of());
        return professional(id);
    }

    @Transactional(readOnly = true)
    public List<ScheduleSlot> schedule(UUID professionalId) {
        professional(professionalId);
        return professionalRepository.schedule(tenant(), professionalId);
    }

    @Transactional
    public List<ScheduleSlot> replaceSchedule(UUID professionalId, List<ScheduleSlot> slots) {
        UUID tenantId = tenant();
        professional(professionalId);
        for (ScheduleSlot s : slots) {
            if (!s.startTime().isBefore(s.endTime())) {
                throw ApiException.invalidField("slots", "O início deve ser antes do fim em todos os horários.");
            }
            boolean overlaps = slots.stream()
                    .anyMatch(o -> o != s
                            && o.weekday() == s.weekday()
                            && o.startTime().isBefore(s.endTime())
                            && s.startTime().isBefore(o.endTime()));
            if (overlaps) throw ApiException.invalidField("slots", "Há horários sobrepostos no mesmo dia.");
        }
        professionalRepository.deleteSchedule(tenantId, professionalId);
        for (ScheduleSlot s : slots) {
            professionalRepository.insertScheduleSlot(Ids.newId(), tenantId, professionalId, s);
        }
        audit.record(AuditAction.RECORD_UPDATED, "professional_schedule", professionalId, Map.of());
        return schedule(professionalId);
    }

    private void requireMemberUser(UUID tenantId, UUID userId) {
        if (userId == null) return;
        boolean member = professionalRepository.isMemberUser(tenantId, userId);
        if (!member) throw ApiException.invalidField("userId", "Usuário não faz parte da equipe desta clínica.");
    }

    @Transactional(readOnly = true)
    public List<RoomView> rooms() {
        return roomRepository.list(tenant());
    }

    @Transactional
    public RoomView createRoom(RoomRequest r) {
        UUID id = Ids.newId();
        roomRepository.insert(id, tenant(), r.name().strip(), Flags.activeIfOmitted(r.active()));
        audit.record(AuditAction.RECORD_CREATED, "room", id, Map.of());
        return new RoomView(id, r.name().strip(), Flags.activeIfOmitted(r.active()));
    }

    @Transactional
    public RoomView updateRoom(UUID id, RoomRequest r) {
        int updated = roomRepository.update(tenant(), id, r.name().strip(), r.active());
        Versioned.requireFound(updated, "Sala");
        audit.record(AuditAction.RECORD_UPDATED, "room", id, Map.of());
        return roomRepository.get(tenant(), id);
    }

    @Transactional(readOnly = true)
    public List<ProcedureView> procedures(String query, Boolean active) {
        String like =
                query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase() + "%";
        return procedureRepository.list(tenant(), like, active);
    }

    @Transactional(readOnly = true)
    public ProcedureView procedure(UUID id) {
        return procedureRepository.find(tenant(), id).orElseThrow(() -> ApiException.notFound("Procedimento"));
    }

    @Transactional
    public ProcedureView createProcedure(ProcedureRequest r) {
        UUID id = Ids.newId();
        procedureRepository.insert(
                id,
                tenant(),
                blankToNull(r.code()),
                r.name().strip(),
                blankToNull(r.category()),
                r,
                Flags.activeIfOmitted(r.active()));
        audit.record(AuditAction.RECORD_CREATED, "procedure", id, Map.of());
        return procedure(id);
    }

    @Transactional
    public ProcedureView updateProcedure(UUID id, ProcedureRequest r) {
        int updated = procedureRepository.update(
                tenant(), id, blankToNull(r.code()), r.name().strip(), blankToNull(r.category()), r);
        Versioned.requireFound(updated, "Procedimento");
        audit.record(
                AuditAction.RECORD_UPDATED,
                "procedure",
                id,
                Map.of("price", r.defaultPrice().toPlainString()));
        return procedure(id);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
