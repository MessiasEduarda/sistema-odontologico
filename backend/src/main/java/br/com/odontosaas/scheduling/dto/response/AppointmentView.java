package br.com.odontosaas.scheduling.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AppointmentView(
        UUID id,
        UUID patientId,
        String patientName,
        String patientMobile,
        UUID professionalId,
        String professionalName,
        String professionalColor,
        UUID roomId,
        String roomName,
        UUID procedureId,
        String procedureName,
        Instant startsAt,
        Instant endsAt,
        String status,
        String notes,
        String cancellationReason,
        Instant confirmedAt,
        Instant reminderSentAt,
        long version) {}
