package br.com.odontosaas.scheduling.dto.response;

import java.time.Instant;
import java.util.UUID;

/** {@code appointmentsInside}: atendimentos ativos já marcados dentro do bloqueio (para remarcar). */
public record ScheduleBlockView(
        UUID id,
        UUID professionalId,
        String professionalName,
        Instant startsAt,
        Instant endsAt,
        String reason,
        long appointmentsInside) {}
