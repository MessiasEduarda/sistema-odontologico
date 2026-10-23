package br.com.odontosaas.patients.event;

import java.util.UUID;

public record PatientEmailChanged(UUID tenantId, UUID patientId) {}
