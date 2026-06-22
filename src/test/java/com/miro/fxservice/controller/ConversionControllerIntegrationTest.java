package com.miro.fxservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miro.fxservice.exception.ApiException;
import com.miro.fxservice.exception.ErrorCode;
import com.miro.fxservice.provider.FxRateProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Sql(
        statements = {
                "DELETE FROM conversions",

                "UPDATE balances SET amount = 10000.0000 WHERE currency = 'USD' AND client_id = (SELECT id FROM clients WHERE client_id = 'CLIENT-001')",
                "UPDATE balances SET amount = 8000.0000 WHERE currency = 'EUR' AND client_id = (SELECT id FROM clients WHERE client_id = 'CLIENT-001')",
                "UPDATE balances SET amount = 5000.0000 WHERE currency = 'GBP' AND client_id = (SELECT id FROM clients WHERE client_id = 'CLIENT-002')"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
)
class ConversionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FxRateProvider fxRateProvider;

    @Test
    void postConversion_shouldDebitSourceAndCreditTargetBalance() throws Exception {
        when(fxRateProvider.fetchRate("USD", "EUR"))
                .thenReturn(new BigDecimal("0.90000000"));

        String requestBody = """
                {
                  "sourceAmount": 100.00,
                  "sourceCurrency": "USD",
                  "targetCurrency": "EUR"
                }
                """;

        mockMvc.perform(post("/conversions")
                        .header("X-Client-Id", "CLIENT-001")
                        .header("Idempotency-Key", "integration-happy-path-001")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.sourceAmount").value(100.0000))
                .andExpect(jsonPath("$.sourceCurrency").value("USD"))
                .andExpect(jsonPath("$.targetAmount").value(90.0000))
                .andExpect(jsonPath("$.targetCurrency").value("EUR"))
                .andExpect(jsonPath("$.rate").value(0.90000000))
                .andExpect(jsonPath("$.balances", hasSize(2)))
                .andExpect(jsonPath("$.balances[0].currency").value("EUR"))
                .andExpect(jsonPath("$.balances[0].amount").value(8090.0000))
                .andExpect(jsonPath("$.balances[1].currency").value("USD"))
                .andExpect(jsonPath("$.balances[1].amount").value(9900.0000));
    }

    @Test
    void postConversion_shouldReplaySameResultForSameIdempotencyKey() throws Exception {
        when(fxRateProvider.fetchRate("USD", "EUR"))
                .thenReturn(new BigDecimal("0.90000000"));

        String requestBody = """
                {
                  "sourceAmount": 100.00,
                  "sourceCurrency": "USD",
                  "targetCurrency": "EUR"
                }
                """;

        MvcResult firstResult = mockMvc.perform(post("/conversions")
                        .header("X-Client-Id", "CLIENT-001")
                        .header("Idempotency-Key", "integration-idempotency-001")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstJson = objectMapper.readTree(firstResult.getResponse().getContentAsString());
        String firstTransactionId = firstJson.get("transactionId").asText();

        MvcResult secondResult = mockMvc.perform(post("/conversions")
                        .header("X-Client-Id", "CLIENT-001")
                        .header("Idempotency-Key", "integration-idempotency-001")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode secondJson = objectMapper.readTree(secondResult.getResponse().getContentAsString());
        String secondTransactionId = secondJson.get("transactionId").asText();

        assertThat(secondTransactionId).isEqualTo(firstTransactionId);

        mockMvc.perform(get("/clients/CLIENT-001/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[0].amount").value(8090.0000))
                .andExpect(jsonPath("$[1].currency").value("USD"))
                .andExpect(jsonPath("$[1].amount").value(9900.0000));

        verify(fxRateProvider, times(1)).fetchRate("USD", "EUR");
    }

    @Test
    void postConversion_shouldReplaySameResultForConcurrentSameIdempotencyKey() throws Exception {
        when(fxRateProvider.fetchRate("USD", "EUR"))
                .thenAnswer(invocation -> {
                    Thread.sleep(250);
                    return new BigDecimal("0.90000000");
                });

        String requestBody = """
                {
                  "sourceAmount": 100.00,
                  "sourceCurrency": "USD",
                  "targetCurrency": "EUR"
                }
                """;

        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            var first = executor.submit(() -> performConcurrentConversion(requestBody, ready, start));
            var second = executor.submit(() -> performConcurrentConversion(requestBody, ready, start));

            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            MvcResult firstResult = first.get(5, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(5, TimeUnit.SECONDS);

            assertThat(firstResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(secondResult.getResponse().getStatus()).isEqualTo(200);

            JsonNode firstJson = objectMapper.readTree(firstResult.getResponse().getContentAsString());
            JsonNode secondJson = objectMapper.readTree(secondResult.getResponse().getContentAsString());

            assertThat(secondJson.get("transactionId").asText())
                    .isEqualTo(firstJson.get("transactionId").asText());

            mockMvc.perform(get("/clients/CLIENT-001/balances"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].currency").value("EUR"))
                    .andExpect(jsonPath("$[0].amount").value(8090.0000))
                    .andExpect(jsonPath("$[1].currency").value("USD"))
                    .andExpect(jsonPath("$[1].amount").value(9900.0000));

            verify(fxRateProvider, times(1)).fetchRate("USD", "EUR");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void postConversion_shouldReturnInsufficientFundsWithoutChangingBalances() throws Exception {
        String requestBody = """
                {
                  "sourceAmount": 99999999.00,
                  "sourceCurrency": "USD",
                  "targetCurrency": "EUR"
                }
                """;

        mockMvc.perform(post("/conversions")
                        .header("X-Client-Id", "CLIENT-001")
                        .header("Idempotency-Key", "integration-insufficient-001")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.message").value("Insufficient funds for client CLIENT-001 in USD"));

        mockMvc.perform(get("/clients/CLIENT-001/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[0].amount").value(8000.0000))
                .andExpect(jsonPath("$[1].currency").value("USD"))
                .andExpect(jsonPath("$[1].amount").value(10000.0000));
    }

    @Test
    void postConversion_shouldReturnClientNotFoundForUnknownClient() throws Exception {
        String requestBody = """
                {
                  "sourceAmount": 100.00,
                  "sourceCurrency": "USD",
                  "targetCurrency": "EUR"
                }
                """;

        mockMvc.perform(post("/conversions")
                        .header("X-Client-Id", "UNKNOWN")
                        .header("Idempotency-Key", "integration-unknown-client")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"));
    }

    @Test
    void postConversion_shouldReturnBalanceNotFoundForUnknownClientCurrency() throws Exception {
        String requestBody = """
                {
                  "sourceAmount": 100.00,
                  "sourceCurrency": "USD",
                  "targetCurrency": "GBP"
                }
                """;

        mockMvc.perform(post("/conversions")
                        .header("X-Client-Id", "CLIENT-001")
                        .header("Idempotency-Key", "integration-missing-balance")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BALANCE_NOT_FOUND"));
    }

    @Test
    void postConversion_shouldReturnValidationErrorForInvalidCurrency() throws Exception {
        String requestBody = """
                {
                  "sourceAmount": 100.00,
                  "sourceCurrency": "US",
                  "targetCurrency": "EUR"
                }
                """;

        mockMvc.perform(post("/conversions")
                        .header("X-Client-Id", "CLIENT-001")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void postConversion_shouldReturnProviderFailureWhenFxProviderFails() throws Exception {
        reset(fxRateProvider);
        when(fxRateProvider.fetchRate("USD", "EUR"))
                .thenThrow(new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        ErrorCode.PROVIDER_FAILURE,
                        "FX provider is unavailable"
                ));

        String requestBody = """
                {
                  "sourceAmount": 100.00,
                  "sourceCurrency": "USD",
                  "targetCurrency": "EUR"
                }
                """;

        mockMvc.perform(post("/conversions")
                        .header("X-Client-Id", "CLIENT-001")
                        .header("Idempotency-Key", "integration-provider-failure")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PROVIDER_FAILURE"));

        mockMvc.perform(get("/clients/CLIENT-001/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(8000.0000))
                .andExpect(jsonPath("$[1].amount").value(10000.0000));
    }

    @Test
    void getConversions_shouldReturnPaginatedHistoryByClientAndDate() throws Exception {
        when(fxRateProvider.fetchRate("USD", "EUR"))
                .thenReturn(new BigDecimal("0.90000000"));

        String requestBody = """
                {
                  "sourceAmount": 100.00,
                  "sourceCurrency": "USD",
                  "targetCurrency": "EUR"
                }
                """;

        mockMvc.perform(post("/conversions")
                        .header("X-Client-Id", "CLIENT-001")
                        .header("Idempotency-Key", "integration-history")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        String today = LocalDate.now(ZoneOffset.UTC).toString();

        mockMvc.perform(get("/conversions")
                        .param("clientId", "CLIENT-001")
                        .param("date", today)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].clientId").value("CLIENT-001"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private MvcResult performConcurrentConversion(
            String requestBody,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        assertThat(start.await(2, TimeUnit.SECONDS)).isTrue();

        return mockMvc.perform(post("/conversions")
                        .header("X-Client-Id", "CLIENT-001")
                        .header("Idempotency-Key", "integration-concurrent-idempotency")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andReturn();
    }
}
