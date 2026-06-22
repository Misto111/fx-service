package com.miro.fxservice.service;

import com.miro.fxservice.exception.ApiException;
import com.miro.fxservice.exception.ErrorCode;
import com.miro.fxservice.provider.FxRateProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateServiceTest {

    @Test
    void getRate_shouldReturnOneWithoutCallingProviderForSameCurrency() {
        FxRateProvider fxRateProvider = mock(FxRateProvider.class);
        RateService rateService = new RateService(fxRateProvider, 300);

        var response = rateService.getRate("usd", "USD");

        assertThat(response.from()).isEqualTo("USD");
        assertThat(response.to()).isEqualTo("USD");
        assertThat(response.rate()).isEqualByComparingTo("1.00000000");
        verify(fxRateProvider, never()).fetchRate("USD", "USD");
    }

    @Test
    void getRate_shouldCacheProviderResultWithinTtl() {
        FxRateProvider fxRateProvider = mock(FxRateProvider.class);
        RateService rateService = new RateService(fxRateProvider, 300);

        when(fxRateProvider.fetchRate("USD", "EUR"))
                .thenReturn(new BigDecimal("0.90000000"));

        var firstResponse = rateService.getRate("USD", "EUR");
        var secondResponse = rateService.getRate("USD", "EUR");

        assertThat(firstResponse.rate()).isEqualByComparingTo("0.90000000");
        assertThat(secondResponse.rate()).isEqualByComparingTo("0.90000000");
        verify(fxRateProvider, times(1)).fetchRate("USD", "EUR");
    }

    @Test
    void getRate_shouldPropagateProviderFailureAsApiException() {
        FxRateProvider fxRateProvider = mock(FxRateProvider.class);
        RateService rateService = new RateService(fxRateProvider, 300);

        when(fxRateProvider.fetchRate("USD", "EUR"))
                .thenThrow(new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        ErrorCode.PROVIDER_FAILURE,
                        "FX provider is unavailable"
                ));

        assertThatThrownBy(() -> rateService.getRate("USD", "EUR"))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PROVIDER_FAILURE);
    }
}
