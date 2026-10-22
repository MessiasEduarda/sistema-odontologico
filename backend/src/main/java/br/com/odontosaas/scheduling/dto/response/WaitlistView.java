package br.com.odontosaas.scheduling.dto.response;

import java.time.Instant;
import java.util.UUID;

public record WaitlistView(
        UUID id,
        UUID patientId,
        String patientName,
        String patientMobile,
        boolean patientAllowWhatsapp,
        UUID professionalId,
        String professionalName,
        UUID procedureId,
        String procedureName,
        String preference,
        String notes,
        boolean urgent,
        String status,
        UUID appointmentId,
        Instant createdAt,
        Instant resolvedAt) {}
