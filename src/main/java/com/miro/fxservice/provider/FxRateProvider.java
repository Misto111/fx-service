package com.miro.fxservice.provider;

import com.miro.fxservice.exception.ApiException;
import com.miro.fxservice.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

@Component
public class FxRateProvider {

    private final RestClient fxRestClient;

    public FxRateProvider(RestClient fxRestClient) {
        this.fxRestClient = fxRestClient;
    }

    public BigDecimal fetchRate(String fromCurrency, String toCurrency) {
        try {
            ExchangeRateApiResponse response = fxRestClient
                    .get()
                    .uri("/{baseCurrency}", fromCurrency)
                    .retrieve()
                    .body(ExchangeRateApiResponse.class);

            if (response == null || response.rates() == null) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        ErrorCode.PROVIDER_FAILURE,
                        "FX provider returned empty response"
                );
            }

            BigDecimal rate = response.rates().get(toCurrency);

            if (rate == null) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        ErrorCode.PROVIDER_FAILURE,
                        "FX provider does not provide rate for currency: " + toCurrency
                );
            }

            return rate;
        } catch (ApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.PROVIDER_FAILURE,
                    "FX provider is unavailable"
            );
        }
    }
}