package com.hoctap970.rag.dto;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

public record UploadResponse(
        UUID documentId,
        String fileName,
        String contentType,
        long size,
        int characterCount,
        int sectionCount,
        int chunkCount,
        Instant uploadedAt,
        String message,
        List<String> warnings
) {
}
