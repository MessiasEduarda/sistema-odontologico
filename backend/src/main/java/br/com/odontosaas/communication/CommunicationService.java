package br.com.odontosaas.communication;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.tenancy.TenantContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Modelos de mensagem e fila de envio (outbox). Mensagens só são enfileiradas se o paciente tiver e-mail e permitir
 * contato por e-mail. Campos disponíveis nos modelos: {{paciente}} {{clinica}} {{data}} {{hora}} {{profissional}}
 * {{link}} {{titulo}} {{conteudo}}.
 */
@Service
public class CommunicationService {

    public static final List<String> TYPES = List.of(
            "APPOINTMENT_REMINDER",
            "APPOINTMENT_CREATED",
            "APPOINTMENT_CANCELLED",
            "PORTAL_ACCESS",
            "CONSENT_REQUEST",
            "CARE_INSTRUCTIONS");

    record Template(String type, String subject, String body, boolean active, boolean custom) {}

    public record TemplateView(String type, String subject, String body, boolean active, boolean custom) {}

    public record TemplateRequest(
            @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(max = 5000) String body,
            boolean active) {}

    public record MessageView(
            UUID id,
            String type,
            String toAddress,
            String subject,
            String status,
            int attempts,
            String lastError,
            String patientName,
            Instant createdAt,
            Instant sentAt) {}

    private static final Map<String, Template> DEFAULTS = defaults();

    private final JdbcClient jdbc;
    private final AuditService audit;

    public CommunicationService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    private static Map<String, Template> defaults() {
        Map<String, Template> m = new LinkedHashMap<>();
        m.put(
                "APPOINTMENT_REMINDER",
                new Template(
                        "APPOINTMENT_REMINDER", "Lembrete: sua consulta em {{data}} às {{hora}}", """
                        Olá, {{paciente}}!

                        Lembramos da sua consulta na {{clinica}} em {{data}} às {{hora}} com {{profissional}}.

                        Para confirmar ou cancelar, acesse: {{link}}

                        Até breve!""", true, false));
        m.put(
                "APPOINTMENT_CREATED",
                new Template("APPOINTMENT_CREATED", "Consulta agendada para {{data}} às {{hora}}", """
                        Olá, {{paciente}}!

                        Sua consulta na {{clinica}} foi agendada para {{data}} às {{hora}} com {{profissional}}.

                        Para confirmar ou cancelar, acesse: {{link}}""", true, false));
        m.put(
                "APPOINTMENT_CANCELLED",
                new Template("APPOINTMENT_CANCELLED", "Consulta de {{data}} cancelada", """
                        Olá, {{paciente}}.

                        Sua consulta na {{clinica}} em {{data}} às {{hora}} foi cancelada.
                        Entre em contato com a clínica para remarcar.""", true, false));
        m.put("PORTAL_ACCESS", new Template("PORTAL_ACCESS", "Seu acesso ao portal da {{clinica}}", """
                        Olá, {{paciente}}!

                        Pelo portal você acompanha suas consultas, confirma presença, assina termos e atualiza seus
                        contatos. Acesse: {{link}}

                        O link é pessoal; não compartilhe.""", true, false));
        m.put(
                "CONSENT_REQUEST",
                new Template("CONSENT_REQUEST", "Termo para assinatura: {{titulo}}", """
                        Olá, {{paciente}}!

                        A {{clinica}} enviou o termo "{{titulo}}" para você ler e assinar: {{link}}

                        O link expira em 7 dias.""", true, false));
        m.put(
                "CARE_INSTRUCTIONS",
                new Template("CARE_INSTRUCTIONS", "Orientações da {{clinica}}: {{titulo}}", """
                        Olá, {{paciente}}!

                        Seguem as orientações passadas pela {{clinica}}:

                        {{conteudo}}

                        Em caso de dúvida ou de qualquer sintoma inesperado, entre em contato com a clínica.""", true, false));
        return Map.copyOf(m);
    }

    @Transactional(readOnly = true)
    public List<TemplateView> templates() {
        UUID tenantId = TenantContext.requireTenantId();
        return TYPES.stream()
                .map(type -> template(tenantId, type))
                .map(t -> new TemplateView(t.type(), t.subject(), t.body(), t.active(), t.custom()))
                .toList();
    }

    Template template(UUID tenantId, String type) {
        return jdbc.sql("select type, subject, body, active, true as custom from message_templates"
                        + " where tenant_id = ? and type = ?")
                .params(tenantId, type)
                .query(Template.class)
                .optional()
                .orElse(DEFAULTS.get(type));
    }

    @Transactional
    public TemplateView saveTemplate(String type, TemplateRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        if (!TYPES.contains(type)) throw ApiException.notFound("Modelo de mensagem");
        jdbc.sql("""
                        insert into message_templates (tenant_id, type, subject, body, active) values (?, ?, ?, ?, ?)
                        on conflict (tenant_id, type) do update set subject = excluded.subject, body = excluded.body,
                            active = excluded.active, updated_at = now()
                        """)
                .params(tenantId, type, r.subject().strip(), r.body().strip(), r.active())
                .update();
        audit.record(AuditAction.RECORD_UPDATED, "message_template", type, Map.of());
        Template t = template(tenantId, type);
        return new TemplateView(t.type(), t.subject(), t.body(), t.active(), t.custom());
    }

    @Transactional(readOnly = true)
    public List<MessageView> messages(String status) {
        return jdbc.sql("""
                        select m.id, m.type, m.to_address, m.subject, m.status, m.attempts, m.last_error,
                               p.full_name as patient_name, m.created_at, m.sent_at
                        from outbound_messages m
                        left join patients p on p.tenant_id = m.tenant_id and p.id = m.patient_id
                        where m.tenant_id = ? and (cast(? as text) is null or m.status = ?)
                        order by m.created_at desc limit 300
                        """)
                .params(TenantContext.requireTenantId(), status, status)
                .query(MessageView.class)
                .list();
    }

    @Transactional
    public void retry(UUID messageId) {
        int updated =
                jdbc.sql("""
                        update outbound_messages set status = 'PENDING', attempts = 0, last_error = null
                        where tenant_id = ? and id = ? and status = 'FAILED'
                        """).params(TenantContext.requireTenantId(), messageId).update();
        if (updated == 0) throw ApiException.notFound("Mensagem com falha");
    }

    /**
     * Enfileira mensagem para o paciente se: o modelo estiver ativo, o paciente tiver e-mail e permitir contato por
     * e-mail. Retorna vazio quando não enfileirou (e o motivo fica só no log de depuração).
     */
    @Transactional
    public Optional<UUID> enqueue(UUID patientId, UUID appointmentId, String type, Map<String, String> fields) {
        UUID tenantId = TenantContext.requireTenantId();
        Template t = template(tenantId, type);
        if (!t.active()) return Optional.empty();
        record Contact(String fullName, String email, boolean allowEmail, String clinic) {}
        Optional<Contact> contact =
                jdbc.sql("""
                        select p.full_name, p.email, p.allow_email, t.name as clinic from patients p
                        join tenants t on t.id = p.tenant_id where p.tenant_id = ? and p.id = ?
                        """).params(tenantId, patientId).query(Contact.class).optional();
        if (contact.isEmpty() || contact.get().email() == null || !contact.get().allowEmail()) return Optional.empty();

        Map<String, String> all = new LinkedHashMap<>(fields);
        all.putIfAbsent("paciente", contact.get().fullName().split(" ")[0]);
        all.putIfAbsent("clinica", contact.get().clinic());
        UUID id = Ids.newId();
        jdbc.sql("""
                        insert into outbound_messages (id, tenant_id, patient_id, appointment_id, type, to_address,
                            subject, body) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id,
                        tenantId,
                        patientId,
                        appointmentId,
                        type,
                        contact.get().email(),
                        render(t.subject(), all),
                        render(t.body(), all))
                .update();
        return Optional.of(id);
    }

    static String render(String text, Map<String, String> fields) {
        String result = text;
        for (var e : fields.entrySet()) {
            result = result.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return result;
    }

    /** Campos de data/hora/profissional de um agendamento, no fuso da clínica. */
    Map<String, String> appointmentFields(UUID tenantId, UUID appointmentId) {
        record A(Instant startsAt, String professional, String zone) {}
        A a = jdbc.sql("""
                        select a.starts_at, pr.name as professional, t.time_zone as zone from appointments a
                        join professionals pr on pr.tenant_id = a.tenant_id and pr.id = a.professional_id
                        join tenants t on t.id = a.tenant_id where a.tenant_id = ? and a.id = ?
                        """)
                .params(tenantId, appointmentId)
                .query((rs, n) -> new A(
                        rs.getTimestamp("starts_at").toInstant(), rs.getString("professional"), rs.getString("zone")))
                .single();
        var local = a.startsAt().atZone(ZoneId.of(a.zone()));
        Map<String, String> m = new LinkedHashMap<>();
        m.put("data", local.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))));
        m.put("hora", local.format(DateTimeFormatter.ofPattern("HH:mm")));
        m.put("profissional", a.professional());
        return m;
    }
}
