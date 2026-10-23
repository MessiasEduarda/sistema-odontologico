package br.com.odontosaas.patients.event;

import java.util.UUID;

public record PatientRegistered(UUID tenantId, UUID patientId) {}
