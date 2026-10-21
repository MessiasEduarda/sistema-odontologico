package br.com.odontosaas.clinic.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ScheduleRequest(@NotNull @Size(max = 28) List<@Valid ScheduleSlot> slots) {}
