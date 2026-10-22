package br.com.odontosaas.medicalrecord.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecentAnamnesis(
        UUID id,
        UUID patientId,
        String patientName,
        String formName,
        String formKind,
        List<Alert> alerts,
        String filledBy,
        String createdByName,
        Instant createdAt) {}
