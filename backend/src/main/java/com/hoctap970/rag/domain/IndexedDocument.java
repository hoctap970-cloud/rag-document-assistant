package com.hoctap970.rag.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IndexedDocument(
        UUID id,
        String fileName,
        String contentType,
        long size,
        int characterCount,
        int sectionCount,
        int chunkCount,
        Instant uploadedAt,
        List<String> embeddingIds
) {
}
