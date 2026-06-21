package com.miro.fxservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ConversionResponse(
        String transactionId,
        BigDecimal sourceAmount,
        String sourceCurrency,
        BigDecimal targetAmount,
        String targetCurrency,
        BigDecimal rate,
        Instant timestamp,
        List<BalanceResponse> balances
) {
}