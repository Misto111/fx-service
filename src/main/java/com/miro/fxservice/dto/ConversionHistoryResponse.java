package com.miro.fxservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ConversionHistoryResponse(
        String transactionId,
        String clientId,
        BigDecimal sourceAmount,
        String sourceCurrency,
        BigDecimal targetAmount,
        String targetCurrency,
        BigDecimal rate,
        Instant timestamp
) {
}