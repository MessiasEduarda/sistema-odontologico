package br.com.odontosaas.clinic;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Contratos dos cadastros da clínica. */
public final class ClinicDtos {

    private ClinicDtos() {}

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
            /** Quando a logo foi enviada (nulo = sem logo). Serve também para renovar o cache da imagem. */
            Instant logoUpdatedAt) {}

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
            boolean remindersEnabled,
            @Min(1) @Max(168) int reminderHoursBefore,
            boolean aestheticsEnabled) {}

    public record ProfessionalView(
            UUID id,
            UUID userId,
            String name,
            String cro,
            String croState,
            String specialty,
            String color,
            String phone,
            String email,
            BigDecimal commissionPercent,
            boolean active,
            long version) {}

    public record ProfessionalRequest(
            UUID userId,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 20) String cro,

            @Pattern(regexp = "^[A-Z]{2}$", message = "use a sigla do estado (ex.: SP)")
            String croState,

            @Size(max = 80) String specialty,

            @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "cor inválida")
            String color,

            @Size(max = 20) String phone,
            @Email @Size(max = 254) String email,
            @DecimalMin("0") @DecimalMax("100") BigDecimal commissionPercent,
            boolean active,
            Long version) {}

    public record ScheduleSlot(
            @Min(1) @Max(7) int weekday,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime) {}

    public record ScheduleRequest(@NotNull @Size(max = 28) List<@Valid ScheduleSlot> slots) {}

    public record RoomView(UUID id, String name, boolean active) {}

    public record RoomRequest(@NotBlank @Size(max = 80) String name, boolean active) {}

    public record ProcedureView(
            UUID id,
            String code,
            String name,
            String category,
            BigDecimal defaultPrice,
            int durationMinutes,
            boolean active,
            Instant updatedAt) {}

    public record ProcedureRequest(
            @Size(max = 20) String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 60) String category,
            @NotNull @DecimalMin("0") @DecimalMax("9999999") BigDecimal defaultPrice,
            @Min(5) @Max(480) int durationMinutes,
            boolean active) {}
}
