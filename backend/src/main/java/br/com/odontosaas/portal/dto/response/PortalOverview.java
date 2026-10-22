package br.com.odontosaas.portal.dto.response;

import java.util.List;

public record PortalOverview(
        String clinicName,
        String clinicPhone,
        PortalContact patient,
        List<PortalAppointment> upcoming,
        List<PortalConsent> pendingConsents) {}
