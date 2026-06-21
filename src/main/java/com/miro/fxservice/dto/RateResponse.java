package com.miro.fxservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RateResponse(
        String from,
        String to,
        BigDecimal rate,
        Instant timestamp
) {
}
