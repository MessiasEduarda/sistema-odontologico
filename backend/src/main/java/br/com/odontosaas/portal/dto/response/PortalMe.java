package br.com.odontosaas.portal.dto.response;

import java.util.List;
import java.util.UUID;

public record PortalMe(String email, List<PortalProfile> profiles, UUID activeTenantId, UUID activePatientId) {}
