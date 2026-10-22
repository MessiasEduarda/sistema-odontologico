package br.com.odontosaas.medicalrecord.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RecordSummary(
        UUID patientId,
        String patientName,
        LocalDate birthDate,
        List<Alert> alerts,
        Instant lastAnamnesisAt,
        int notesCount,
        int pendingConsents,
        int attachmentsCount) {}
