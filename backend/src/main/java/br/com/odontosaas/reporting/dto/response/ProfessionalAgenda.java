package br.com.odontosaas.reporting.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record ProfessionalAgenda(
        UUID professionalId,
        String professionalName,
        long appointments,
        long completed,
        long noShows,
        long cancelled,
        long bookedMinutes,
        long availableMinutes,
        BigDecimal occupancyPercent) {}
