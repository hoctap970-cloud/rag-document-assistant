package com.hoctap970.rag.service;

import com.hoctap970.rag.config.GeminiProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AiReadinessTests {
    @Test void missingKeyDoesNotSpendQuotaAndConfigurationNeverPrintsTheSecret() {
        var models = mock(GeminiModelProvider.class);
        var properties = new GeminiProperties("secret-test-value", "chat", "embedding", 2, 1024, 30);
        var service = new AiReadinessService(models, properties);
        assertThat(service.check().ready()).isFalse();
        verify(models, never()).chatModel(); verify(models, never()).queryEmbeddingModel();
        assertThat(properties.toString()).contains("REDACTED").doesNotContain("secret-test-value");
    }
    @Test void probeTestsBothApisAndReturnsPublicRecoveryAdviceForQuotaFailures() {
        var models = mock(GeminiModelProvider.class);
        var embedding = mock(EmbeddingModel.class); var chat = mock(ChatModel.class);
        when(models.isConfigured()).thenReturn(true);
        when(models.queryEmbeddingModel()).thenReturn(embedding); when(models.chatModel()).thenReturn(chat);
        when(embedding.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{1, 0})));
        when(chat.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException("429 quota: SECRET-BODY"));
        var service = new AiReadinessService(models, new GeminiProperties("key", "chat", "embedding", 2, 1024, 30));
        var result = service.check();
        assertThat(result.ready()).isFalse(); assertThat(result.embeddingStatus()).isEqualTo("Đạt");
        assertThat(result.message()).contains("60 giây").doesNotContain("SECRET-BODY");
        when(chat.chat(any(ChatRequest.class))).thenReturn(dev.langchain4j.model.chat.response.ChatResponse.builder()
                .aiMessage(AiMessage.from("NOVA sẵn sàng.")).build());
        assertThat(service.check().ready()).isTrue();
    }
}
