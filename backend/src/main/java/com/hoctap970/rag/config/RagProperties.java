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
        @DefaultValue("true") boolean rerankEnabled,
        @DefaultValue("20") int maxDocuments,
        @DefaultValue("5000") int maxStoredChunks,
        @DefaultValue("100000000") long maxStoredBytes
) {
    public RagProperties(int chunkSize, int chunkOverlap, int maxChunksPerDocument,
                         int maxResults, double minScore) {
        this(chunkSize, chunkOverlap, maxChunksPerDocument, maxResults, minScore,
                40, 48000, 24000, 1, true, 20, 5000, 100_000_000L);
    }

    public RagProperties(int chunkSize, int chunkOverlap, int maxChunksPerDocument,
                         int maxResults, double minScore, int candidateCount,
                         int maxContextCharacters, int fullContextCharacters,
                         int neighborWindow, boolean rerankEnabled) {
        this(chunkSize, chunkOverlap, maxChunksPerDocument, maxResults, minScore,
                candidateCount, maxContextCharacters, fullContextCharacters, neighborWindow,
                rerankEnabled, 20, 5000, 100_000_000L);
    }

    @ConstructorBinding
    public RagProperties {
        if (chunkSize < 200 || chunkOverlap < 0 || chunkOverlap >= chunkSize
                || maxChunksPerDocument < 1 || maxResults < 1 || candidateCount < maxResults
                || maxContextCharacters < chunkSize || fullContextCharacters < 0
                || fullContextCharacters > maxContextCharacters || neighborWindow < 0
                || neighborWindow > 3 || !Double.isFinite(minScore) || minScore < 0 || minScore > 1
                || maxDocuments < 1 || maxStoredChunks < 1 || maxStoredBytes < 1) {
            throw new IllegalArgumentException("Cấu hình RAG không hợp lệ");
        }
    }
}
