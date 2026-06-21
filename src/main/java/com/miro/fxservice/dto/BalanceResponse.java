package com.miro.fxservice.dto;

import java.math.BigDecimal;

public record BalanceResponse(
        String currency,
        BigDecimal amount
) {
}
