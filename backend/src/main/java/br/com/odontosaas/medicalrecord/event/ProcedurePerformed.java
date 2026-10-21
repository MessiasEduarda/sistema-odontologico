package br.com.odontosaas.medicalrecord.event;

import java.util.UUID;

/** Publicado quando um procedimento é registrado (ou anulado) no prontuário com vínculo a item de plano. */
public record ProcedurePerformed(UUID tenantId, UUID treatmentItemId, boolean voided) {}
