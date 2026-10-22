package br.com.odontosaas.scheduling.dto.response;

import java.time.Instant;
import java.util.Map;

public record AppointmentEventView(String action, Map<String, Object> details, String actorName, Instant occurredAt) {}
