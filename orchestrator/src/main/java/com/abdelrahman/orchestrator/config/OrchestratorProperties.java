package com.abdelrahman.orchestrator.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "orchestrator")
public record OrchestratorProperties(
        @Valid @NotNull Timing timing,
        @Valid @NotNull Fee fee,
        @Valid @NotNull Credentials credentials) {

    public record Timing(
            @NotNull Duration fastPollInterval,
            @NotNull Duration fastPollWindow,
            @NotNull Duration slowPollInterval,
            @NotNull Duration hardStop,
            @NotNull Duration paymentDeadline,
            @NotNull Duration captureDelay,
            @NotNull Duration captureAlert,
            @NotNull Duration authorizeAlert,
            @NotNull Duration voidAlert,
            @Min(1) int attemptCap,
            @NotNull Duration firstAttemptDelay,
            @NotNull Duration secondAttemptDelay,
            @NotNull Duration idempotencyTtl) {
    }

    public record Fee(
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal rate,
            @NotNull RoundingMode rounding,
            @NotBlank String accountId) {
    }

    public record Credentials(
            @Valid @NotNull Basic client,
            @Valid @NotNull Basic psp,
            @Valid @NotNull Basic ledger) {
    }

    public record Basic(@NotBlank String username, @NotBlank String password) {
    }
}
