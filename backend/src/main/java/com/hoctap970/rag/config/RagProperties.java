package com.hoctap970.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
        int chunkSize,
        int chunkOverlap,
        int maxChunksPerDocument,
        int maxResults,
        double minScore
) {
}
