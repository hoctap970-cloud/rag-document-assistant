package com.hoctap970.rag.dto;

import java.util.UUID;

public record SourceReference(
        UUID documentId,
        String fileName,
        String section,
        int chunkIndex,
        double score,
        String excerpt
) {
}
