package br.com.odontosaas.aesthetics.dto.response;

import br.com.odontosaas.aesthetics.dto.request.Region;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AestheticView(
        UUID id,
        UUID patientId,
        String patientName,
        UUID professionalId,
        String professionalName,
        Instant performedAt,
        String procedureType,
        String productName,
        String manufacturer,
        String anvisaRegistry,
        String batch,
        LocalDate expiryDate,
        String unit,
        List<Region> regions,
        BigDecimal totalQuantity,
        String technique,
        String notes,
        LocalDate returnDate,
        UUID consentId,
        String consentStatus,
        String createdByName,
        Instant voidedAt,
        String voidReason) {}
