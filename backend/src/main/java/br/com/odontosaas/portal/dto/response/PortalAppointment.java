package br.com.odontosaas.portal.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PortalAppointment(
        UUID id, Instant startsAt, Instant endsAt, String professionalName, String status, boolean canRespond) {}
