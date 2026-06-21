package com.miro.fxservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record ConversionRequest(

        @NotNull
        @DecimalMin(value = "0.0001", message = "sourceAmount must be positive")
        @DecimalMax(value = "1000000000.0000", message = "sourceAmount is too large")
        BigDecimal sourceAmount,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "sourceCurrency must be a valid ISO-4217 currency code")
        String sourceCurrency,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "targetCurrency must be a valid ISO-4217 currency code")
        String targetCurrency
) {
}