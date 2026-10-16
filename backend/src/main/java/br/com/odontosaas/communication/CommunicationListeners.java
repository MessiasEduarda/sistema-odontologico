package br.com.odontosaas.communication;

import br.com.odontosaas.clinical.CareInstructionsSent;
import br.com.odontosaas.clinical.ConsentLinkRequested;
import br.com.odontosaas.scheduling.AppointmentChanged;
import br.com.odontosaas.scheduling.AppointmentLinks;
import br.com.odontosaas.tenancy.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reage a eventos de outros módulos enfileirando mensagens (na mesma transação do evento). */
@Component
class CommunicationListeners {

    private final CommunicationService communication;
    private final AppointmentLinks links;
    private final JdbcClient jdbc;
    private final String publicUrl;

    CommunicationListeners(
            CommunicationService communication,
            AppointmentLinks links,
            JdbcClient jdbc,
            @Value("${app.public-url}") String publicUrl) {
        this.communication = communication;
        this.links = links;
        this.jdbc = jdbc;
        this.publicUrl = publicUrl;
    }

    @EventListener
    void onAppointmentChanged(AppointmentChanged event) {
        TenantContext.runAs(event.tenantId(), TenantContext.userId().orElse(null), () -> {
            UUID patientId = jdbc.sql("select patient_id from appointments where tenant_id = ? and id = ?")
                    .params(event.tenantId(), event.appointmentId())
                    .query(UUID.class)
                    .single();
            Map<String, String> fields = communication.appointmentFields(event.tenantId(), event.appointmentId());
            switch (event.kind()) {
                case CREATED, RESCHEDULED -> {
                    fields.put("link", publicUrl + "/consulta#token=" + links.issueToken(event.appointmentId()));
                    communication.enqueue(patientId, event.appointmentId(), "APPOINTMENT_CREATED", fields);
                }
                case CANCELLED ->
                    communication.enqueue(patientId, event.appointmentId(), "APPOINTMENT_CANCELLED", fields);
            }
            return null;
        });
    }

    @EventListener
    void onConsentLink(ConsentLinkRequested event) {
        if (!event.sendEmail()) return;
        TenantContext.runAs(
                event.tenantId(),
                TenantContext.userId().orElse(null),
                () -> communication.enqueue(
                        event.patientId(),
                        null,
                        "CONSENT_REQUEST",
                        Map.of("link", event.url(), "titulo", event.title())));
    }

    @EventListener
    void onCareInstructions(CareInstructionsSent event) {
        TenantContext.runAs(
                event.tenantId(),
                TenantContext.userId().orElse(null),
                () -> communication.enqueue(
                        event.patientId(),
                        null,
                        "CARE_INSTRUCTIONS",
                        Map.of("titulo", event.title(), "conteudo", event.content())));
    }
}
