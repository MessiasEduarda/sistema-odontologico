package br.com.odontosaas.finance.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 18) String document,
        @Size(max = 20) String phone,
        @Email @Size(max = 254) String email,
        @Size(max = 500) String notes,
        Boolean active) {}
