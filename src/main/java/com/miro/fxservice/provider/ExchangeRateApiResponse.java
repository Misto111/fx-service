package com.miro.fxservice.provider;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Map;

public record ExchangeRateApiResponse(
        String result,

        @JsonProperty("base_code")
        String baseCode,

        Map<String, BigDecimal> rates
) {
}
