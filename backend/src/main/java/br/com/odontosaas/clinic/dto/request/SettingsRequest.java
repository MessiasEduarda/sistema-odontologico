package br.com.odontosaas.clinic.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SettingsRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 160) String legalName,
        @Size(max = 18) String document,
        @Size(max = 20) String phone,
        @Email @Size(max = 254) String email,
        @Size(max = 9) String zipCode,
        @Size(max = 200) String address,
        @Size(max = 100) String city,

        @Pattern(regexp = "^[A-Z]{2}$", message = "use a sigla do estado (ex.: SP)")
        String state,

        @Min(5) @Max(480) int defaultAppointmentMinutes,
        @NotNull Boolean remindersEnabled,
        @Min(1) @Max(168) int reminderHoursBefore,
        @NotNull Boolean aestheticsEnabled) {}
