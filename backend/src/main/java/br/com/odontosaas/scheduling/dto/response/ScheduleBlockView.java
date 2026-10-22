package br.com.odontosaas.scheduling.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ScheduleBlockView(
        UUID id,
        UUID professionalId,
        String professionalName,
        Instant startsAt,
        Instant endsAt,
        String reason,
        long appointmentsInside) {}
