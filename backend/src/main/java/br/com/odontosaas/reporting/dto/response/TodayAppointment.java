package br.com.odontosaas.reporting.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TodayAppointment(
        UUID id,
        Instant startsAt,
        Instant endsAt,
        String patientName,
        UUID patientId,
        String professionalName,
        String professionalColor,
        String procedureName,
        String status) {}
