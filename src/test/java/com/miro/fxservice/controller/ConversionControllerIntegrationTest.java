package com.miro.fxservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
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
}