package br.com.odontosaas.medicalrecord.event;

import java.util.UUID;

public record ProcedurePerformed(UUID tenantId, UUID treatmentItemId, boolean voided) {}
