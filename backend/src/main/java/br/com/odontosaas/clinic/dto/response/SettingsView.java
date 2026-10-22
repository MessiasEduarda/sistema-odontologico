package br.com.odontosaas.clinic.dto.response;

import java.time.Instant;

public record SettingsView(
        String name,
        String legalName,
        String document,
        String phone,
        String email,
        String zipCode,
        String address,
        String city,
        String state,
        int defaultAppointmentMinutes,
        boolean remindersEnabled,
        int reminderHoursBefore,
        String timeZone,
        boolean aestheticsEnabled,
        Instant logoUpdatedAt) {}
