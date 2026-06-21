package com.miro.fxservice.service;

import com.miro.fxservice.dto.ConversionHistoryResponse;
import com.miro.fxservice.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.time.LocalDate;
import java.time.ZoneOffset;
import com.miro.fxservice.domain.Balance;
import com.miro.fxservice.domain.Conversion;
import com.miro.fxservice.dto.BalanceResponse;
import com.miro.fxservice.dto.ConversionRequest;
import com.miro.fxservice.dto.ConversionResponse;
import com.miro.fxservice.dto.RateResponse;
import com.miro.fxservice.exception.ApiException;
import com.miro.fxservice.exception.ErrorCode;
import com.miro.fxservice.repository.BalanceRepository;
import com.miro.fxservice.repository.ClientRepository;
import com.miro.fxservice.repository.ConversionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ConversionService {

    private static final int MONEY_SCALE = 4;

    private final ClientRepository clientRepository;
    private final BalanceRepository balanceRepository;
    private final ConversionRepository conversionRepository;
    private final RateService rateService;

    public ConversionService(
            ClientRepository clientRepository,
            BalanceRepository balanceRepository,
            ConversionRepository conversionRepository,
            RateService rateService
    ) {
        this.clientRepository = clientRepository;
        this.balanceRepository = balanceRepository;
        this.conversionRepository = conversionRepository;
        this.rateService = rateService;
    }

    @Transactional
    public ConversionResponse convert(
            String clientId,
            String idempotencyKey,
            ConversionRequest request
    ) {
        String normalizedClientId = normalizeClientId(clientId);
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);

        if (normalizedIdempotencyKey != null) {
            var existingConversion = conversionRepository
                    .findByClientIdAndIdempotencyKey(normalizedClientId, normalizedIdempotencyKey);

            if (existingConversion.isPresent()) {
                return toResponse(existingConversion.get(), getCurrentBalances(normalizedClientId));
            }
        }

        if (!clientRepository.existsByClientId(normalizedClientId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    ErrorCode.CLIENT_NOT_FOUND,
                    "Client not found: " + normalizedClientId
            );
        }

        BigDecimal sourceAmount = normalizeMoney(request.sourceAmount());
        String sourceCurrency = normalizeCurrency(request.sourceCurrency());
        String targetCurrency = normalizeCurrency(request.targetCurrency());

        List<String> currenciesToLock = sourceCurrency.equals(targetCurrency)
                ? List.of(sourceCurrency)
                : List.of(sourceCurrency, targetCurrency);

        List<Balance> lockedBalances = balanceRepository.findBalancesForUpdate(
                normalizedClientId,
                currenciesToLock
        );

        Balance sourceBalance = findBalanceOrThrow(
                lockedBalances,
                normalizedClientId,
                sourceCurrency
        );

        Balance targetBalance = sourceCurrency.equals(targetCurrency)
                ? sourceBalance
                : findBalanceOrThrow(lockedBalances, normalizedClientId, targetCurrency);

        if (sourceBalance.getAmount().compareTo(sourceAmount) < 0) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ErrorCode.INSUFFICIENT_FUNDS,
                    "Insufficient funds for client " + normalizedClientId + " in " + sourceCurrency
            );
        }

        RateResponse rateResponse = rateService.getRate(sourceCurrency, targetCurrency);
        BigDecimal rate = rateResponse.rate();

        BigDecimal targetAmount = sourceAmount
                .multiply(rate)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        if (!sourceCurrency.equals(targetCurrency)) {
            sourceBalance.setAmount(
                    sourceBalance.getAmount()
                            .subtract(sourceAmount)
                            .setScale(MONEY_SCALE, RoundingMode.HALF_UP)
            );

            targetBalance.setAmount(
                    targetBalance.getAmount()
                            .add(targetAmount)
                            .setScale(MONEY_SCALE, RoundingMode.HALF_UP)
            );
        }

        Conversion conversion = new Conversion(
                UUID.randomUUID().toString(),
                normalizedClientId,
                sourceAmount,
                sourceCurrency,
                targetAmount,
                targetCurrency,
                rate,
                normalizedIdempotencyKey,
                Instant.now()
        );

        Conversion savedConversion = conversionRepository.save(conversion);

        return toResponse(savedConversion, getCurrentBalances(normalizedClientId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversionHistoryResponse> searchConversions(
            String transactionId,
            LocalDate date,
            String clientId,
            int page,
            int size
    ) {
        String normalizedTransactionId = normalizeOptionalText(transactionId);
        String normalizedClientId = normalizeOptionalClientId(clientId);

        if (normalizedTransactionId == null && date == null && normalizedClientId == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "At least one filter must be provided: transactionId, date, or clientId"
            );
        }

        if (page < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "Page must not be negative"
            );
        }

        if (size < 1 || size > 100) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "Size must be between 1 and 100"
            );
        }

        Instant startDateTime = null;
        Instant endDateTime = null;

        if (date != null) {
            startDateTime = date.atStartOfDay(ZoneOffset.UTC).toInstant();
            endDateTime = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        }

        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<ConversionHistoryResponse> result = conversionRepository.searchConversions(
                        normalizedTransactionId,
                        normalizedClientId,
                        startDateTime,
                        endDateTime,
                        pageRequest
                )
                .map(this::toHistoryResponse);

        return PageResponse.fromPage(result);
    }

    private Balance findBalanceOrThrow(
            List<Balance> balances,
            String clientId,
            String currency
    ) {
        return balances.stream()
                .filter(balance -> balance.getCurrency().equals(currency))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.BALANCE_NOT_FOUND,
                        "Balance not found for client " + clientId + " and currency " + currency
                ));
    }

    private List<BalanceResponse> getCurrentBalances(String clientId) {
        return balanceRepository.findByClient_ClientIdOrderByCurrencyAsc(clientId)
                .stream()
                .map(balance -> new BalanceResponse(
                        balance.getCurrency(),
                        balance.getAmount()
                ))
                .toList();
    }

    private ConversionResponse toResponse(
            Conversion conversion,
            List<BalanceResponse> balances
    ) {
        return new ConversionResponse(
                conversion.getTransactionId(),
                conversion.getSourceAmount(),
                conversion.getSourceCurrency(),
                conversion.getTargetAmount(),
                conversion.getTargetCurrency(),
                conversion.getRate(),
                conversion.getCreatedAt(),
                balances
        );
    }

    private String normalizeClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "Client id must not be blank"
            );
        }

        return clientId.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }

        return idempotencyKey.trim();
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

    private BigDecimal normalizeMoney(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "Amount must be positive"
            );
        }

        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private ConversionHistoryResponse toHistoryResponse(Conversion conversion) {
        return new ConversionHistoryResponse(
                conversion.getTransactionId(),
                conversion.getClientId(),
                conversion.getSourceAmount(),
                conversion.getSourceCurrency(),
                conversion.getTargetAmount(),
                conversion.getTargetCurrency(),
                conversion.getRate(),
                conversion.getCreatedAt()
        );
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String normalizeOptionalClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }

        return clientId.trim().toUpperCase(Locale.ROOT);
    }
}