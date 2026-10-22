package br.com.odontosaas.clinic.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record ScheduleSlot(
        @Min(1) @Max(7) int weekday,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime) {}
