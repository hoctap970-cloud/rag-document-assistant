package com.hoctap970.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.gemini")
public record GeminiProperties(
        String apiKey,
        String chatModel,
        String embeddingModel,
        int embeddingDimensions,
        @DefaultValue("8192") int maxOutputTokens,
        @DefaultValue("120") int timeoutSeconds
) {
}
