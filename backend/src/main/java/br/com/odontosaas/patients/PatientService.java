package br.com.odontosaas.patients;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.patients.PatientDtos.ImportIssue;
import br.com.odontosaas.patients.PatientDtos.ImportResult;
import br.com.odontosaas.patients.PatientDtos.ImportRow;
import br.com.odontosaas.patients.PatientDtos.PatientRequest;
import br.com.odontosaas.patients.PatientDtos.PatientSummary;
import br.com.odontosaas.patients.PatientDtos.PatientView;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Cpf;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.shared.PageResponse;
import br.com.odontosaas.shared.PlanLimits;
import br.com.odontosaas.shared.Versioned;
import br.com.odontosaas.tenancy.TenantContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientService {

    private static final String COLUMNS = """
            id, full_name, social_name, birth_date, cpf, rg, sex, phone, mobile, email, zip_code, street, number,
            complement, district, city, state, occupation, referral_source, health_insurance, insurance_number,
            guardian_name, guardian_cpf, guardian_phone, notes, allow_email, allow_sms, allow_whatsapp, active,
            created_at, updated_at, version
            """;

    /** Máximo de linhas por importação (planilhas maiores: dividir em partes). */
    public static final int IMPORT_MAX_ROWS = 2000;

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final Validator validator;
    private final PlanLimits planLimits;

    public PatientService(JdbcClient jdbc, AuditService audit, Validator validator, PlanLimits planLimits) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.validator = validator;
        this.planLimits = planLimits;
    }

    /** Busca por nome (parcial), CPF, telefone ou e-mail, com paginação. */
    @Transactional(readOnly = true)
    public PageResponse<PatientSummary> search(String query, Boolean active, int page, int size) {
        UUID tenantId = TenantContext.requireTenantId();
        int pageSize = Math.clamp(size, 1, 100);
        int pageIndex = Math.max(page, 0);

        StringBuilder where = new StringBuilder(" where p.tenant_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (active != null) {
            where.append(" and p.active = ?");
            params.add(active);
        }
        if (query != null && !query.isBlank()) {
            String term = query.strip().toLowerCase();
            String digits = term.replaceAll("\\D", "");
            where.append(" and (lower(p.full_name) like ? or lower(coalesce(p.social_name, '')) like ?"
                    + " or lower(coalesce(p.email, '')) like ?");
            params.add("%" + term + "%");
            params.add("%" + term + "%");
            params.add("%" + term + "%");
            if (digits.length() >= 3) {
                where.append(" or p.cpf like ? or regexp_replace(coalesce(p.mobile, '') || ' '"
                        + " || coalesce(p.phone, ''), '\\D', '', 'g') like ?");
                params.add(digits + "%");
                params.add("%" + digits + "%");
            }
            where.append(")");
        }

        long total = jdbc.sql("select count(*) from patients p" + where)
                .params(params)
                .query(Long.class)
                .single();

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add(pageIndex * pageSize);
        List<PatientSummary> items = jdbc.sql("""
                        select p.id, p.full_name, p.social_name, p.birth_date, p.cpf, p.mobile, p.phone, p.email,
                               p.active,
                               (select min(a.starts_at) from appointments a where a.tenant_id = p.tenant_id
                                  and a.patient_id = p.id and a.starts_at >= now()
                                  and a.status not in ('CANCELLED', 'NO_SHOW')) as next_appointment,
                               (select max(a.starts_at) from appointments a where a.tenant_id = p.tenant_id
                                  and a.patient_id = p.id and a.starts_at < now()
                                  and a.status = 'COMPLETED') as last_appointment
                        from patients p""" + where + " order by lower(p.full_name) limit ? offset ?")
                .params(pageParams)
                .query(PatientSummary.class)
                .list();
        int totalPages = (int) Math.ceil(total / (double) pageSize);
        return new PageResponse<>(items, pageIndex, pageSize, total, totalPages);
    }

    @Transactional(readOnly = true)
    public PatientView get(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from patients where tenant_id = ? and id = ?")
                .params(TenantContext.requireTenantId(), id)
                .query(PatientView.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Paciente"));
    }

    @Transactional
    public PatientView create(PatientRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        Normalized n = normalize(r);
        rejectDuplicateCpf(tenantId, n.cpf(), null);
        planLimits.requirePatientSlot(tenantId);
        UUID id = insert(tenantId, r, n);
        audit.record(AuditAction.RECORD_CREATED, "patient", id, Map.of());
        return get(id);
    }

    /**
     * Importação de planilha. Cada linha passa pelas mesmas regras do cadastro manual; linhas inválidas e CPFs já
     * cadastrados (ou repetidos na planilha) são listados e não entram. Com {@code dryRun}, só valida. Linhas além das
     * vagas de pacientes ativos do plano também não entram e são listadas como erro.
     */
    @Transactional
    public ImportResult importRows(List<ImportRow> rows, boolean dryRun) {
        if (rows.size() > IMPORT_MAX_ROWS) {
            throw ApiException.badRequest(
                    "IMPORT_TOO_LARGE",
                    "Envie no máximo " + IMPORT_MAX_ROWS + " pacientes por vez (divida a planilha).");
        }
        UUID tenantId = TenantContext.requireTenantId();
        int slots = planLimits.availablePatients(tenantId);
        List<ImportIssue> issues = new ArrayList<>();
        Set<String> cpfsInFile = new HashSet<>();
        int ready = 0;
        int created = 0;
        for (ImportRow row : rows) {
            String name = row.fullName() == null ? "" : row.fullName().strip();
            try {
                PatientRequest request = toRequest(row);
                Set<ConstraintViolation<PatientRequest>> violations = validator.validate(request);
                if (!violations.isEmpty()) {
                    ConstraintViolation<PatientRequest> first =
                            violations.iterator().next();
                    issues.add(new ImportIssue(
                            row.line(),
                            name,
                            "ERROR",
                            fieldLabel(first.getPropertyPath().toString()) + ": " + first.getMessage()));
                    continue;
                }
                Normalized n = normalize(request);
                if (n.cpf() != null && (!cpfsInFile.add(n.cpf()) || cpfExists(tenantId, n.cpf()))) {
                    issues.add(new ImportIssue(
                            row.line(), name, "DUPLICATE", "CPF já cadastrado ou repetido na planilha."));
                    continue;
                }
                if (ready >= slots) {
                    issues.add(new ImportIssue(
                            row.line(),
                            name,
                            "ERROR",
                            "Limite de pacientes ativos do plano atingido: a linha não foi importada."));
                    continue;
                }
                ready++;
                if (!dryRun) {
                    insert(tenantId, request, n);
                    created++;
                }
            } catch (ApiException e) {
                String message = e.fieldErrors() == null || e.fieldErrors().isEmpty()
                        ? e.getMessage()
                        : fieldLabel(e.fieldErrors().getFirst().get("field")) + ": "
                                + e.fieldErrors().getFirst().get("message");
                issues.add(new ImportIssue(row.line(), name, "ERROR", message));
            }
        }
        if (created > 0) {
            audit.record(AuditAction.RECORD_CREATED, "patient-import", null, Map.of("count", created));
        }
        return new ImportResult(rows.size(), ready, created, issues);
    }

    private UUID insert(UUID tenantId, PatientRequest r, Normalized n) {
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into patients (id, tenant_id, full_name, social_name, birth_date, cpf, rg, sex, phone,
                            mobile, email, zip_code, street, number, complement, district, city, state, occupation,
                            referral_source, health_insurance, insurance_number, guardian_name, guardian_cpf,
                            guardian_phone, notes, allow_email, allow_sms, allow_whatsapp)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                            coalesce(cast(? as boolean), false), coalesce(cast(? as boolean), false),
                            coalesce(cast(? as boolean), false))
                        """).params(values(id, tenantId, r, n)).update();
        return id;
    }

    @Transactional
    public PatientView update(UUID id, PatientRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        Normalized n = normalize(r);
        rejectDuplicateCpf(tenantId, n.cpf(), id);
        List<Object> params = new ArrayList<>(values(id, tenantId, r, n).subList(2, 29));
        params.add(tenantId);
        params.add(id);
        params.add(r.version());
        params.add(r.version());
        int updated = jdbc.sql("""
                        update patients set full_name = ?, social_name = ?, birth_date = ?, cpf = ?, rg = ?, sex = ?,
                            phone = ?, mobile = ?, email = ?, zip_code = ?, street = ?, number = ?, complement = ?,
                            district = ?, city = ?, state = ?, occupation = ?, referral_source = ?,
                            health_insurance = ?, insurance_number = ?, guardian_name = ?, guardian_cpf = ?,
                            guardian_phone = ?, notes = ?, allow_email = coalesce(cast(? as boolean), allow_email),
                            allow_sms = coalesce(cast(? as boolean), allow_sms),
                            allow_whatsapp = coalesce(cast(? as boolean), allow_whatsapp),
                            updated_at = now(), version = version + 1
                        where tenant_id = ? and id = ? and (cast(? as bigint) is null or version = ?)
                        """).params(params).update();
        Versioned.check(updated, exists(tenantId, id), "Paciente");
        audit.record(AuditAction.RECORD_UPDATED, "patient", id, Map.of());
        return get(id);
    }

    @Transactional
    public PatientView setActive(UUID id, boolean active) {
        UUID tenantId = TenantContext.requireTenantId();
        if (active && isInactive(tenantId, id)) {
            planLimits.requirePatientSlot(tenantId);
        }
        int updated = jdbc.sql("update patients set active = ?, updated_at = now(), version = version + 1"
                        + " where tenant_id = ? and id = ?")
                .params(active, tenantId, id)
                .update();
        Versioned.requireFound(updated, "Paciente");
        audit.record(active ? AuditAction.RECORD_UPDATED : AuditAction.RECORD_DEACTIVATED, "patient", id, Map.of());
        return get(id);
    }

    private boolean isInactive(UUID tenantId, UUID id) {
        return jdbc.sql("select exists(select 1 from patients where tenant_id = ? and id = ? and not active)")
                .params(tenantId, id)
                .query(Boolean.class)
                .single();
    }

    private boolean exists(UUID tenantId, UUID id) {
        return jdbc.sql("select exists(select 1 from patients where tenant_id = ? and id = ?)")
                .params(tenantId, id)
                .query(Boolean.class)
                .single();
    }

    private boolean cpfExists(UUID tenantId, String cpf) {
        return jdbc.sql("select exists(select 1 from patients where tenant_id = ? and cpf = ?)")
                .params(tenantId, cpf)
                .query(Boolean.class)
                .single();
    }

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT));

    private static PatientRequest toRequest(ImportRow r) {
        if (r.fullName() == null || r.fullName().isBlank()) {
            throw ApiException.invalidField("fullName", "informe o nome do paciente.");
        }
        return new PatientRequest(
                r.fullName().strip(),
                blank(r.socialName()),
                parseDate(r.birthDate()),
                blank(r.cpf()),
                blank(r.rg()),
                parseSex(r.sex()),
                blank(r.phone()),
                blank(r.mobile()),
                blank(r.email()),
                blank(r.zipCode()),
                blank(r.street()),
                blank(r.number()),
                blank(r.complement()),
                blank(r.district()),
                blank(r.city()),
                r.state() == null || r.state().isBlank()
                        ? null
                        : r.state().strip().toUpperCase(Locale.ROOT),
                blank(r.occupation()),
                blank(r.referralSource()),
                blank(r.healthInsurance()),
                blank(r.insuranceNumber()),
                blank(r.guardianName()),
                blank(r.guardianCpf()),
                blank(r.guardianPhone()),
                blank(r.notes()),
                parseYes(r.allowEmail()),
                false,
                parseYes(r.allowWhatsapp()),
                null);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(value.strip(), format);
            } catch (DateTimeParseException ignored) {
                // tenta o próximo formato
            }
        }
        throw ApiException.invalidField("birthDate", "data inválida (use DD/MM/AAAA).");
    }

    private static String parseSex(String value) {
        if (value == null || value.isBlank()) return null;
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "f", "fem", "feminino", "mulher", "female" -> "FEMALE";
            case "m", "masc", "masculino", "homem", "male" -> "MALE";
            case "o", "outro", "other" -> "OTHER";
            default -> "NOT_INFORMED";
        };
    }

    private static boolean parseYes(String value) {
        if (value == null) return false;
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "sim", "s", "yes", "y", "true", "1", "x" -> true;
            default -> false;
        };
    }

    private static String fieldLabel(String field) {
        return switch (field == null ? "" : field) {
            case "fullName" -> "Nome";
            case "birthDate" -> "Data de nascimento";
            case "cpf" -> "CPF";
            case "email" -> "E-mail";
            case "state" -> "UF";
            case "guardianName" -> "Responsável";
            case "guardianCpf" -> "CPF do responsável";
            default -> "Campo " + field;
        };
    }

    private void rejectDuplicateCpf(UUID tenantId, String cpf, UUID exceptId) {
        if (cpf == null) return;
        boolean duplicated = jdbc.sql("select exists(select 1 from patients where tenant_id = ? and cpf = ?"
                        + " and (cast(? as uuid) is null or id <> ?))")
                .params(tenantId, cpf, exceptId, exceptId)
                .query(Boolean.class)
                .single();
        if (duplicated) throw ApiException.conflict("DUPLICATE_CPF", "Já existe um paciente com esse CPF.");
    }

    private record Normalized(String cpf, String guardianCpf, String state) {}

    private static Normalized normalize(PatientRequest r) {
        String cpf = Cpf.normalizeOrReject(r.cpf(), "cpf");
        String guardianCpf = Cpf.normalizeOrReject(r.guardianCpf(), "guardianCpf");
        if (r.birthDate() != null && r.birthDate().isAfter(LocalDate.now().minusYears(18))) {
            if (r.guardianName() == null || r.guardianName().isBlank()) {
                throw ApiException.invalidField("guardianName", "Informe o responsável legal (paciente menor de 18).");
            }
        }
        return new Normalized(cpf, guardianCpf, blank(r.state()));
    }

    private static List<Object> values(UUID id, UUID tenantId, PatientRequest r, Normalized n) {
        List<Object> v = new ArrayList<>();
        v.add(id);
        v.add(tenantId);
        v.add(r.fullName().strip());
        v.add(blank(r.socialName()));
        v.add(r.birthDate());
        v.add(n.cpf());
        v.add(blank(r.rg()));
        v.add(r.sex() == null ? "NOT_INFORMED" : r.sex());
        v.add(blank(r.phone()));
        v.add(blank(r.mobile()));
        v.add(blank(r.email()));
        v.add(blank(r.zipCode()));
        v.add(blank(r.street()));
        v.add(blank(r.number()));
        v.add(blank(r.complement()));
        v.add(blank(r.district()));
        v.add(blank(r.city()));
        v.add(n.state());
        v.add(blank(r.occupation()));
        v.add(blank(r.referralSource()));
        v.add(blank(r.healthInsurance()));
        v.add(blank(r.insuranceNumber()));
        v.add(blank(r.guardianName()));
        v.add(n.guardianCpf());
        v.add(blank(r.guardianPhone()));
        v.add(blank(r.notes()));
        v.add(r.allowEmail());
        v.add(r.allowSms());
        v.add(r.allowWhatsapp());
        return v;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
