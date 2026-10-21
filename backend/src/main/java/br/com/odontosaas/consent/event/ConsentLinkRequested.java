package br.com.odontosaas.consent.event;

import java.util.UUID;

/** Publicado quando a clínica gera link de assinatura; a Comunicação envia o e-mail ao paciente (se permitido). */
public record ConsentLinkRequested(UUID tenantId, UUID patientId, String title, String url, boolean sendEmail) {}
