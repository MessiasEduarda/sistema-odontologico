package br.com.odontosaas.scheduling.dto.response;

import java.time.Instant;

public record FreeSlot(Instant startsAt, Instant endsAt) {}
