package com.miro.fxservice.controller;

import com.miro.fxservice.dto.RateResponse;
import com.miro.fxservice.service.RateService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class RateController {

    private final RateService rateService;

    public RateController(RateService rateService) {
        this.rateService = rateService;
    }

    @GetMapping("/rates")
    public RateResponse getRate(
            @RequestParam
            @NotBlank
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "from must be a valid ISO-4217 currency code")
            String from,

            @RequestParam
            @NotBlank
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "to must be a valid ISO-4217 currency code")
            String to
    ) {
        return rateService.getRate(from, to);
    }
}