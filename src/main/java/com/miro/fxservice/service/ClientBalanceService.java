package com.miro.fxservice.service;

import com.miro.fxservice.domain.ClientAccount;
import com.miro.fxservice.dto.BalanceResponse;
import com.miro.fxservice.exception.ApiException;
import com.miro.fxservice.exception.ErrorCode;
import com.miro.fxservice.repository.BalanceRepository;
import com.miro.fxservice.repository.ClientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class ClientBalanceService {

    private final ClientRepository clientRepository;
    private final BalanceRepository balanceRepository;

    public ClientBalanceService(
            ClientRepository clientRepository,
            BalanceRepository balanceRepository
    ) {
        this.clientRepository = clientRepository;
        this.balanceRepository = balanceRepository;
    }

    @Transactional(readOnly = true)
    public List<BalanceResponse> getBalances(String clientId) {
        String normalizedClientId = normalizeClientId(clientId);

        ClientAccount client = clientRepository.findByClientId(normalizedClientId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.CLIENT_NOT_FOUND,
                        "Client not found: " + normalizedClientId
                ));

        return balanceRepository.findByClient_ClientIdOrderByCurrencyAsc(client.getClientId())
                .stream()
                .map(balance -> new BalanceResponse(
                        balance.getCurrency(),
                        balance.getAmount()
                ))
                .toList();
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
}