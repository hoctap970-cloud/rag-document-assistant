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
    public GeminiProperties {
        apiKey = apiKey == null ? "" : apiKey.strip();
        if (chatModel == null || chatModel.isBlank() || embeddingModel == null || embeddingModel.isBlank()
                || embeddingDimensions < 1 || maxOutputTokens < 1 || timeoutSeconds < 1) {
            throw new IllegalArgumentException("Cấu hình model, số chiều hoặc thời gian chờ Gemini không hợp lệ");
        }
    }

    @Override public String toString() {
        return "GeminiProperties[apiKey=REDACTED, chatModel=" + chatModel + ", embeddingModel=" + embeddingModel
                + ", embeddingDimensions=" + embeddingDimensions + ", maxOutputTokens=" + maxOutputTokens
                + ", timeoutSeconds=" + timeoutSeconds + "]";
    }
}
