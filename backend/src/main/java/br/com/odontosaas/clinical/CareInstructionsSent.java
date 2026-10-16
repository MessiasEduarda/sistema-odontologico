package br.com.odontosaas.clinical;

import java.util.UUID;

/** Publicado quando a clínica pede o envio de orientações por e-mail; a Comunicação enfileira (se permitido). */
public record CareInstructionsSent(UUID tenantId, UUID patientId, String title, String content) {}
