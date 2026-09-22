package com.hoctap970.rag.service;

import java.time.Duration;

import com.hoctap970.rag.config.GeminiProperties;
import com.hoctap970.rag.exception.AiConfigurationException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiEmbeddingModel;
import dev.langchain4j.model.google.genai.GoogleGenAiEmbeddingModel.TaskTypeEnum;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GeminiModelProvider {

    private static final int EMBEDDING_BATCH_SIZE = 50;

    private final GeminiProperties properties;
    private volatile ChatModel chatModel;
    private volatile EmbeddingModel documentEmbeddingModel;
    private volatile EmbeddingModel queryEmbeddingModel;

    public GeminiModelProvider(GeminiProperties properties) {
        this.properties = properties;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(properties.apiKey());
    }

    public ChatModel chatModel() {
        requireApiKey();
        if (chatModel == null) {
            synchronized (this) {
                if (chatModel == null) {
                    chatModel = GoogleGenAiChatModel.builder()
                            .apiKey(properties.apiKey())
                            .modelName(properties.chatModel())
                            .temperature(0.1)
                            .maxOutputTokens(1024)
                            .timeout(Duration.ofSeconds(60))
                            .maxRetries(2)
                            .build();
                }
            }
        }
        return chatModel;
    }

    public EmbeddingModel documentEmbeddingModel() {
        requireApiKey();
        if (documentEmbeddingModel == null) {
            synchronized (this) {
                if (documentEmbeddingModel == null) {
                    documentEmbeddingModel = embeddingModel(TaskTypeEnum.RETRIEVAL_DOCUMENT);
                }
            }
        }
        return documentEmbeddingModel;
    }

    public EmbeddingModel queryEmbeddingModel() {
        requireApiKey();
        if (queryEmbeddingModel == null) {
            synchronized (this) {
                if (queryEmbeddingModel == null) {
                    queryEmbeddingModel = embeddingModel(TaskTypeEnum.RETRIEVAL_QUERY);
                }
            }
        }
        return queryEmbeddingModel;
    }

    private EmbeddingModel embeddingModel(TaskTypeEnum taskType) {
        GoogleGenAiEmbeddingModel.Builder builder = GoogleGenAiEmbeddingModel.builder()
                .apiKey(properties.apiKey())
                .modelName(properties.embeddingModel())
                .taskType(taskType)
                .outputDimensionality(properties.embeddingDimensions())
                .timeout(Duration.ofSeconds(45))
                .maxSegmentsPerBatch(EMBEDDING_BATCH_SIZE)
                .maxRetries(2);

        if (taskType == TaskTypeEnum.RETRIEVAL_DOCUMENT) {
            builder.titleMetadataKey("title");
        }
        return builder.build();
    }

    private void requireApiKey() {
        if (!isConfigured()) {
            throw new AiConfigurationException(
                    "Chưa cấu hình GEMINI_API_KEY. Hãy thêm biến môi trường này vào Run Configuration của IntelliJ rồi chạy lại ứng dụng."
            );
        }
    }
}
