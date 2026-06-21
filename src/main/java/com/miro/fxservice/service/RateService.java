package com.miro.fxservice.service;

import com.miro.fxservice.dto.RateResponse;
import com.miro.fxservice.exception.ApiException;
import com.miro.fxservice.exception.ErrorCode;
import com.miro.fxservice.provider.FxRateProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateService {

    private static final int RATE_SCALE = 8;

    private final FxRateProvider fxRateProvider;
    private final long cacheTtlSeconds;
    private final Map<String, CachedRate> rateCache = new ConcurrentHashMap<>();

    public RateService(
            FxRateProvider fxRateProvider,
            @Value("${fx.rates.cache-ttl-seconds}") long cacheTtlSeconds
    ) {
        this.fxRateProvider = fxRateProvider;
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public RateResponse getRate(String from, String to) {
        String fromCurrency = normalizeCurrency(from);
        String toCurrency = normalizeCurrency(to);

        if (fromCurrency.equals(toCurrency)) {
            return new RateResponse(
                    fromCurrency,
                    toCurrency,
                    BigDecimal.ONE.setScale(RATE_SCALE, RoundingMode.HALF_UP),
                    Instant.now()
            );
        }

        String cacheKey = fromCurrency + "_" + toCurrency;
        Instant now = Instant.now();

        CachedRate cachedRate = rateCache.get(cacheKey);

        if (cachedRate != null && cachedRate.expiresAt().isAfter(now)) {
            return new RateResponse(
                    fromCurrency,
                    toCurrency,
                    cachedRate.rate(),
                    cachedRate.fetchedAt()
            );
        }

        BigDecimal rate = fxRateProvider.fetchRate(fromCurrency, toCurrency)
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);

        CachedRate newCachedRate = new CachedRate(
                rate,
                now,
                now.plusSeconds(cacheTtlSeconds)
        );

        rateCache.put(cacheKey, newCachedRate);

        return new RateResponse(
                fromCurrency,
                toCurrency,
                rate,
                now
        );
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "Currency must not be blank"
            );
        }

        String normalized = currency.trim().toUpperCase(Locale.ROOT);

        try {
            Currency.getInstance(normalized);
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "Invalid ISO-4217 currency code: " + normalized
            );
        }
    }

    private record CachedRate(
            BigDecimal rate,
            Instant fetchedAt,
            Instant expiresAt
    ) {
    }
}