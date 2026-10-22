package br.com.odontosaas.communication.service;

import br.com.odontosaas.audit.AuditAction;
import br.com.odontosaas.audit.AuditService;
import br.com.odontosaas.communication.dto.request.TemplateRequest;
import br.com.odontosaas.communication.dto.response.MessageView;
import br.com.odontosaas.communication.dto.response.TemplateView;
import br.com.odontosaas.communication.repository.MessageRepository;
import br.com.odontosaas.communication.repository.MessageRepository.AppointmentSlotRow;
import br.com.odontosaas.communication.repository.MessageRepository.ContactRow;
import br.com.odontosaas.communication.repository.MessageTemplateRepository;
import br.com.odontosaas.shared.ApiException;
import br.com.odontosaas.shared.Ids;
import br.com.odontosaas.tenancy.TenantContext;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final Map<String, Template> DEFAULTS = defaults();

    private final MessageRepository messages;
    private final MessageTemplateRepository templates;
    private final AuditService audit;

    public CommunicationService(MessageRepository messages, MessageTemplateRepository templates, AuditService audit) {
        this.messages = messages;
        this.templates = templates;
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
        return templates
                .find(tenantId, type)
                .map(r -> new Template(r.type(), r.subject(), r.body(), r.active(), r.custom()))
                .orElse(DEFAULTS.get(type));
    }

    @Transactional
    public TemplateView saveTemplate(String type, TemplateRequest r) {
        UUID tenantId = TenantContext.requireTenantId();
        if (!TYPES.contains(type)) throw ApiException.notFound("Modelo de mensagem");
        templates.upsert(tenantId, type, r.subject().strip(), r.body().strip(), r.active());
        audit.record(AuditAction.RECORD_UPDATED, "message_template", type, Map.of());
        Template t = template(tenantId, type);
        return new TemplateView(t.type(), t.subject(), t.body(), t.active(), t.custom());
    }

    @Transactional(readOnly = true)
    public List<MessageView> messages(String status) {
        return messages.list(TenantContext.requireTenantId(), status);
    }

    @Transactional
    public void retry(UUID messageId) {
        int updated = messages.requeueFailed(TenantContext.requireTenantId(), messageId);
        if (updated == 0) throw ApiException.notFound("Mensagem com falha");
    }

    @Transactional
    public Optional<UUID> enqueue(UUID patientId, UUID appointmentId, String type, Map<String, String> fields) {
        UUID tenantId = TenantContext.requireTenantId();
        Template t = template(tenantId, type);
        if (!t.active()) return Optional.empty();
        Optional<ContactRow> contact = messages.findContact(tenantId, patientId);
        if (contact.isEmpty() || contact.get().email() == null || !contact.get().allowEmail()) return Optional.empty();

        Map<String, String> all = new LinkedHashMap<>(fields);
        all.putIfAbsent("paciente", contact.get().fullName().split(" ")[0]);
        all.putIfAbsent("clinica", contact.get().clinic());
        UUID id = Ids.newId();
        messages.insert(
                id,
                tenantId,
                patientId,
                appointmentId,
                type,
                contact.get().email(),
                render(t.subject(), all),
                render(t.body(), all));
        return Optional.of(id);
    }

    static String render(String text, Map<String, String> fields) {
        String result = text;
        for (var e : fields.entrySet()) {
            result = result.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return result;
    }

    public Map<String, String> appointmentFields(UUID tenantId, UUID appointmentId) {
        AppointmentSlotRow a = messages.appointmentSlot(tenantId, appointmentId);
        var local = a.startsAt().atZone(ZoneId.of(a.zone()));
        Map<String, String> m = new LinkedHashMap<>();
        m.put("data", local.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))));
        m.put("hora", local.format(DateTimeFormatter.ofPattern("HH:mm")));
        m.put("profissional", a.professional());
        return m;
    }
}
