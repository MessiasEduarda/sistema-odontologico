package br.com.odontosaas.clinic.dto.response;

import java.util.UUID;

public record RoomView(UUID id, String name, boolean active) {}
