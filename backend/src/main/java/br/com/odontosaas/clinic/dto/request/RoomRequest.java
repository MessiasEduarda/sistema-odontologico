package br.com.odontosaas.clinic.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoomRequest(@NotBlank @Size(max = 80) String name, Boolean active) {}
