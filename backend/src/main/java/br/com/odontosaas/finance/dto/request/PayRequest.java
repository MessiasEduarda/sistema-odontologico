package br.com.odontosaas.finance.dto.request;

import br.com.odontosaas.finance.support.PaymentMethods;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

public record PayRequest(
        @NotBlank @Pattern(regexp = PaymentMethods.REGEX) String method, Instant paidAt) {}
