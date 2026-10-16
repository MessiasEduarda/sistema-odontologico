package br.com.odontosaas.scheduling;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SchedulingDtos {

    private SchedulingDtos() {}

    public record AppointmentView(
            UUID id,
            UUID patientId,
            String patientName,
            String patientMobile,
            UUID professionalId,
            String professionalName,
            String professionalColor,
            UUID roomId,
            String roomName,
            UUID procedureId,
            String procedureName,
            Instant startsAt,
            Instant endsAt,
            String status,
            String notes,
            String cancellationReason,
            Instant confirmedAt,
            Instant reminderSentAt,
            long version) {}

    public record AppointmentEventView(
            String action, Map<String, Object> details, String actorName, Instant occurredAt) {}

    public record AppointmentDetail(AppointmentView appointment, List<AppointmentEventView> history) {}

    public record AppointmentRequest(
            @NotNull UUID patientId,
            @NotNull UUID professionalId,
            UUID roomId,
            UUID procedureId,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @Size(max = 2000) String notes,
            Long version) {}

    public record StatusRequest(
            @NotBlank @Pattern(regexp = "SCHEDULED|CONFIRMED|ARRIVED|IN_PROGRESS|COMPLETED|NO_SHOW|CANCELLED")
            String status,

            @Size(max = 300) String reason) {}

    public record FreeSlot(Instant startsAt, Instant endsAt) {}

    public record AvailabilityRequest(
            @NotNull UUID professionalId, @Min(5) @Max(480) int durationMinutes) {}

    /** Visão pública da consulta (link enviado ao paciente). Sem dados clínicos. */
    public record PublicAppointmentView(
            String clinicName,
            String patientFirstName,
            String professionalName,
            Instant startsAt,
            Instant endsAt,
            String status,
            boolean canRespond) {}

    public record PublicResponseRequest(
            @NotBlank @Size(max = 64) String token,
            @NotBlank @Pattern(regexp = "CONFIRM|CANCEL") String action) {}

    public record TokenRequest(@NotBlank @Size(max = 64) String token) {}
}
