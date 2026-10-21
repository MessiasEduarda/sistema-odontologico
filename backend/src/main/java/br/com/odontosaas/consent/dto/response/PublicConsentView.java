package br.com.odontosaas.consent.dto.response;

import java.time.Instant;

public record PublicConsentView(
        String clinicName, String patientName, String title, String body, String status, Instant signedAt) {}
