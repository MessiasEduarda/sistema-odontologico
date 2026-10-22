package br.com.odontosaas.medicalrecord.event;

import java.util.UUID;

public record CareInstructionsSent(UUID tenantId, UUID patientId, String title, String content) {}
