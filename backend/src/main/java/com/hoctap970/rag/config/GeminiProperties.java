package com.hoctap970.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.gemini")
public record GeminiProperties(
        String apiKey,
        String chatModel,
        String embeddingModel,
        int embeddingDimensions
) {
}
