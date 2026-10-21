package br.com.odontosaas.medicalrecord.dto.response;

import br.com.odontosaas.medicalrecord.dto.request.Answer;
import br.com.odontosaas.medicalrecord.dto.request.Question;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AnamnesisView(
        UUID id,
        UUID formId,
        String formName,
        String formKind,
        List<Question> questions,
        Map<String, Answer> answers,
        List<Alert> alerts,
        String notes,
        String filledBy,
        String createdByName,
        Instant createdAt) {}
