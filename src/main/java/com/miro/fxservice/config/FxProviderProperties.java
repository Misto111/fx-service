package com.miro.fxservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fx.provider")
public record FxProviderProperties(
        String baseUrl,
        int connectTimeoutMs,
        int readTimeoutMs
) {
}