package br.com.odontosaas.portal.dto.response;

import java.util.UUID;

public record PortalProfile(UUID tenantId, UUID patientId, String clinicName, String patientName) {}
