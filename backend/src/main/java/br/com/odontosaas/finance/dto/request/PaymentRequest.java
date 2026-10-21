package br.com.odontosaas.finance.dto.request;

import br.com.odontosaas.finance.support.PaymentMethods;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentRequest(
        @NotNull @DecimalMin("0.01") @DecimalMax("9999999") BigDecimal amount,

        @NotBlank @Pattern(regexp = PaymentMethods.REGEX) String method,
        Instant paidAt,
        @Size(max = 300) String notes) {}
