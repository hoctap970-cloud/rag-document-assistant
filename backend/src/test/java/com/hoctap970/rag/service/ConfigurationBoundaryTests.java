package com.hoctap970.rag.service;

import com.hoctap970.rag.config.GeminiProperties;
import com.hoctap970.rag.config.RagProperties;
import com.hoctap970.rag.exception.AiConfigurationException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConfigurationBoundaryTests {
    @Test void missingKeyAllowsOfflineStartupButEveryModelFailsBeforeSendingARequest() {
        var models = new GeminiModelProvider(new GeminiProperties("  ", "chat", "embedding", 768, 512, 30));
        assertThat(models.isConfigured()).isFalse();
        assertThatThrownBy(models::chatModel).isInstanceOf(AiConfigurationException.class).hasMessageContaining("GEMINI_API_KEY");
        assertThatThrownBy(models::documentEmbeddingModel).isInstanceOf(AiConfigurationException.class);
        assertThatThrownBy(models::queryEmbeddingModel).isInstanceOf(AiConfigurationException.class);
    }

    @Test void namesAndKeyAreTrimmedAndPropertiesNeverPrintTheKey() {
        var properties = new GeminiProperties(" secret-test-key ", " chat ", " embedding ", 768, 512, 30);
        assertThat(properties.apiKey()).isEqualTo("secret-test-key");
        assertThat(properties.chatModel()).isEqualTo("chat");
        assertThat(properties.embeddingModel()).isEqualTo("embedding");
        assertThat(properties.toString()).doesNotContain("secret-test-key");
    }

    @Test void invalidRagScoresAndMemoryLimitsFailAtStartup() {
        for (double score : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -.01, 1.01}) {
            assertThatThrownBy(() -> new RagProperties(900, 120, 800, 5, score)).isInstanceOf(IllegalArgumentException.class);
        }
        var properties = new RagProperties(900, 120, 800, 5, .35);
        assertThat(properties.maxDocuments()).isEqualTo(20);
        assertThat(properties.maxStoredChunks()).isEqualTo(5000);
        assertThat(properties.maxStoredBytes()).isEqualTo(100_000_000L);
        assertThatThrownBy(() -> new RagProperties(900, 120, 800, 5, .35, 40, 48000, 24000, 1, true,
                0, 5000, 100_000_000L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RagProperties(900, 120, 800, 5, .35, 40, 48000, 24000, 1, true,
                20, 0, 100_000_000L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RagProperties(900, 120, 800, 5, .35, 40, 48000, 24000, 1, true,
                20, 5000, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
