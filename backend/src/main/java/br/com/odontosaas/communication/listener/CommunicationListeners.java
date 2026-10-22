package br.com.odontosaas.communication.listener;

import br.com.odontosaas.communication.repository.MessageRepository;
import br.com.odontosaas.communication.service.CommunicationService;
import br.com.odontosaas.consent.event.ConsentLinkRequested;
import br.com.odontosaas.medicalrecord.event.CareInstructionsSent;
import br.com.odontosaas.scheduling.event.AppointmentChanged;
import br.com.odontosaas.scheduling.service.AppointmentLinks;
import br.com.odontosaas.tenancy.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class CommunicationListeners {

    private final CommunicationService communication;
    private final AppointmentLinks links;
    private final MessageRepository messages;
    private final String publicUrl;

    CommunicationListeners(
            CommunicationService communication,
            AppointmentLinks links,
            MessageRepository messages,
            @Value("${app.public-url}") String publicUrl) {
        this.communication = communication;
        this.links = links;
        this.messages = messages;
        this.publicUrl = publicUrl;
    }

    @EventListener
    void onAppointmentChanged(AppointmentChanged event) {
        TenantContext.runAs(event.tenantId(), TenantContext.userId().orElse(null), () -> {
            UUID patientId = messages.appointmentPatientId(event.tenantId(), event.appointmentId());
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
