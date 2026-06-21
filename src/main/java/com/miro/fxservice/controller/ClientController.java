package com.miro.fxservice.controller;

import com.miro.fxservice.dto.BalanceResponse;
import com.miro.fxservice.service.ClientBalanceService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/clients")
public class ClientController {

    private final ClientBalanceService clientBalanceService;

    public ClientController(ClientBalanceService clientBalanceService) {
        this.clientBalanceService = clientBalanceService;
    }

    @GetMapping("/{clientId}/balances")
    public List<BalanceResponse> getClientBalances(
            @PathVariable @NotBlank String clientId) {
        return clientBalanceService.getBalances(clientId);
    }
}
