package br.com.odontosaas.system.dto.response;

import java.time.Instant;

public record StatusResponse(String status, String version, Instant serverTime) {}
