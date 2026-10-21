package br.com.odontosaas.treatment.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ApproveRequest(
        @NotBlank @Size(max = 160) String approvedByName,
        boolean generateReceivable,
        @Min(1) @Max(48) int installments,
        LocalDate firstDueDate) {}
