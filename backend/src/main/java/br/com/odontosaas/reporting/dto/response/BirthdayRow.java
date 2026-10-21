package br.com.odontosaas.reporting.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record BirthdayRow(UUID id, String fullName, LocalDate birthDate, String mobile) {}
