package com.hoctap970.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
        int chunkSize,
        int chunkOverlap,
        int maxChunksPerDocument,
        int maxResults,
        double minScore,
        @DefaultValue("40") int candidateCount,
        @DefaultValue("48000") int maxContextCharacters,
        @DefaultValue("24000") int fullContextCharacters,
        @DefaultValue("1") int neighborWindow,
        @DefaultValue("true") boolean rerankEnabled
) {
    public RagProperties(int chunkSize, int chunkOverlap, int maxChunksPerDocument,
                         int maxResults, double minScore) {
        this(chunkSize, chunkOverlap, maxChunksPerDocument, maxResults, minScore,
                40, 48000, 24000, 1, true);
    }

    @ConstructorBinding
    public RagProperties {
        if (chunkSize < 200 || chunkOverlap < 0 || chunkOverlap >= chunkSize
                || maxChunksPerDocument < 1 || maxResults < 1 || candidateCount < maxResults
                || maxContextCharacters < chunkSize || fullContextCharacters < 0
                || fullContextCharacters > maxContextCharacters || neighborWindow < 0
                || neighborWindow > 3 || minScore < 0 || minScore > 1) {
            throw new IllegalArgumentException("Cấu hình RAG không hợp lệ");
        }
    }
}
