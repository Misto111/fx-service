package com.miro.fxservice.controller;

import com.miro.fxservice.dto.ConversionHistoryResponse;
import com.miro.fxservice.dto.ConversionRequest;
import com.miro.fxservice.dto.ConversionResponse;
import com.miro.fxservice.dto.PageResponse;
import com.miro.fxservice.service.ConversionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/conversions")
public class ConversionController {

    private final ConversionService conversionService;

    public ConversionController(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @PostMapping
    public ConversionResponse convert(
            @RequestHeader("X-Client-Id") @NotBlank String clientId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ConversionRequest request
    ) {
        return conversionService.convert(clientId, idempotencyKey, request);
    }

    @GetMapping
    public PageResponse<ConversionHistoryResponse> searchConversions(
            @RequestParam(required = false) String transactionId,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,

            @RequestParam(required = false) String clientId,

            @RequestParam(defaultValue = "0")
            @PositiveOrZero(message = "page must be zero or positive")
            int page,

            @RequestParam(defaultValue = "20")
            @Positive(message = "size must be positive")
            int size
    ) {
        return conversionService.searchConversions(
                transactionId,
                date,
                clientId,
                page,
                size
        );
    }
}