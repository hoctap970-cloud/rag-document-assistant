package com.hoctap970.rag.dto;

public record HealthResponse(
        String status,
        String application,
        boolean geminiConfigured,
        int documentCount,
        int chunkCount
) {
}
