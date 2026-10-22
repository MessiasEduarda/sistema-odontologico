package br.com.odontosaas.consent.event;

import java.util.UUID;

public record ConsentLinkRequested(UUID tenantId, UUID patientId, String title, String url, boolean sendEmail) {}
