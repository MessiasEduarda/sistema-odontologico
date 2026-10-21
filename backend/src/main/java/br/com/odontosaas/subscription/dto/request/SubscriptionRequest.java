package br.com.odontosaas.subscription.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record SubscriptionRequest(
        @NotBlank String planCode,

        @NotBlank @Pattern(regexp = "TRIALING|ACTIVE|PAST_DUE|CANCELLED")
        String status,

        LocalDate trialEndsAt,
        LocalDate currentPeriodEnd,
        @Size(max = 500) String notes,

        @Size(max = 500) @Pattern(regexp = "^$|^https://\\S+$", message = "Use um link https://.")
        String paymentUrl) {}
