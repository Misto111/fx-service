package com.miro.fxservice.service;

import com.miro.fxservice.domain.Balance;
import com.miro.fxservice.domain.ClientAccount;
import com.miro.fxservice.domain.Conversion;
import com.miro.fxservice.dto.ConversionRequest;
import com.miro.fxservice.dto.ConversionResponse;
import com.miro.fxservice.dto.RateResponse;
import com.miro.fxservice.exception.ApiException;
import com.miro.fxservice.exception.ErrorCode;
import com.miro.fxservice.repository.BalanceRepository;
import com.miro.fxservice.repository.ClientRepository;
import com.miro.fxservice.repository.ConversionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversionServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private BalanceRepository balanceRepository;

    @Mock
    private ConversionRepository conversionRepository;

    @Mock
    private RateService rateService;

    private ConversionService conversionService;

    @BeforeEach
    void setUp() {
        conversionService = new ConversionService(
                clientRepository,
                balanceRepository,
                conversionRepository,
                rateService
        );
    }

    @Test
    void convert_shouldDebitSourceAndCreditTargetBalance() {
        ClientAccount client = new ClientAccount("CLIENT-001");

        Balance usdBalance = new Balance(client, "USD", new BigDecimal("10000.0000"));
        Balance eurBalance = new Balance(client, "EUR", new BigDecimal("8000.0000"));

        ConversionRequest request = new ConversionRequest(
                new BigDecimal("100.00"),
                "USD",
                "EUR"
        );

        when(conversionRepository.findByClientIdAndIdempotencyKey("CLIENT-001", "unit-key-001"))
                .thenReturn(Optional.empty());

        when(clientRepository.findByClientIdForUpdate("CLIENT-001"))
                .thenReturn(Optional.of(client));

        when(balanceRepository.findBalancesForUpdate(eq("CLIENT-001"), anyCollection()))
                .thenReturn(List.of(usdBalance, eurBalance));

        when(rateService.getRate("USD", "EUR"))
                .thenReturn(new RateResponse(
                        "USD",
                        "EUR",
                        new BigDecimal("0.90000000"),
                        Instant.now()
                ));

        when(conversionRepository.save(any(Conversion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(balanceRepository.findByClient_ClientIdOrderByCurrencyAsc("CLIENT-001"))
                .thenReturn(List.of(eurBalance, usdBalance));

        ConversionResponse response = conversionService.convert(
                "CLIENT-001",
                "unit-key-001",
                request
        );

        assertThat(response.sourceAmount()).isEqualByComparingTo("100.0000");
        assertThat(response.targetAmount()).isEqualByComparingTo("90.0000");
        assertThat(response.rate()).isEqualByComparingTo("0.90000000");

        assertThat(usdBalance.getAmount()).isEqualByComparingTo("9900.0000");
        assertThat(eurBalance.getAmount()).isEqualByComparingTo("8090.0000");

        verify(conversionRepository).save(any(Conversion.class));
    }

    @Test
    void convert_shouldReturnExistingConversionWhenIdempotencyKeyIsReplayed() {
        ClientAccount client = new ClientAccount("CLIENT-001");

        Balance usdBalance = new Balance(client, "USD", new BigDecimal("9900.0000"));
        Balance eurBalance = new Balance(client, "EUR", new BigDecimal("8090.0000"));

        Conversion existingConversion = new Conversion(
                "tx-123",
                "CLIENT-001",
                new BigDecimal("100.0000"),
                "USD",
                new BigDecimal("90.0000"),
                "EUR",
                new BigDecimal("0.90000000"),
                "unit-key-001",
                Instant.now()
        );

        ConversionRequest request = new ConversionRequest(
                new BigDecimal("100.00"),
                "USD",
                "EUR"
        );

        when(conversionRepository.findByClientIdAndIdempotencyKey("CLIENT-001", "unit-key-001"))
                .thenReturn(Optional.of(existingConversion));

        when(clientRepository.findByClientIdForUpdate("CLIENT-001"))
                .thenReturn(Optional.of(client));

        when(balanceRepository.findByClient_ClientIdOrderByCurrencyAsc("CLIENT-001"))
                .thenReturn(List.of(eurBalance, usdBalance));

        ConversionResponse response = conversionService.convert(
                "CLIENT-001",
                "unit-key-001",
                request
        );

        assertThat(response.transactionId()).isEqualTo("tx-123");
        assertThat(response.sourceAmount()).isEqualByComparingTo("100.0000");
        assertThat(response.targetAmount()).isEqualByComparingTo("90.0000");

        verify(balanceRepository, never()).findBalancesForUpdate(anyString(), anyCollection());
        verify(rateService, never()).getRate(anyString(), anyString());
        verify(conversionRepository, never()).save(any());
    }

    @Test
    void convert_shouldThrowInsufficientFundsWhenSourceBalanceIsTooLow() {
        ClientAccount client = new ClientAccount("CLIENT-001");

        Balance usdBalance = new Balance(client, "USD", new BigDecimal("50.0000"));
        Balance eurBalance = new Balance(client, "EUR", new BigDecimal("8000.0000"));

        ConversionRequest request = new ConversionRequest(
                new BigDecimal("100.00"),
                "USD",
                "EUR"
        );

        when(conversionRepository.findByClientIdAndIdempotencyKey("CLIENT-001", "unit-key-insufficient"))
                .thenReturn(Optional.empty());

        when(clientRepository.findByClientIdForUpdate("CLIENT-001"))
                .thenReturn(Optional.of(client));

        when(balanceRepository.findBalancesForUpdate(eq("CLIENT-001"), anyCollection()))
                .thenReturn(List.of(usdBalance, eurBalance));

        assertThatThrownBy(() -> conversionService.convert(
                "CLIENT-001",
                "unit-key-insufficient",
                request
        ))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);

        assertThat(usdBalance.getAmount()).isEqualByComparingTo("50.0000");
        assertThat(eurBalance.getAmount()).isEqualByComparingTo("8000.0000");

        verify(rateService, never()).getRate(anyString(), anyString());
        verify(conversionRepository, never()).save(any());
    }
}
