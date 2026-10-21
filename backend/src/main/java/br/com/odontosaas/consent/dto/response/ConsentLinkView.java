package br.com.odontosaas.consent.dto.response;

import java.time.Instant;

public record ConsentLinkView(String url, Instant expiresAt) {}
