package br.com.odontosaas.medicalrecord.dto.response;

import br.com.odontosaas.medicalrecord.dto.request.Question;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FormView(
        UUID id, String name, String kind, List<Question> questions, boolean active, Instant updatedAt) {}
