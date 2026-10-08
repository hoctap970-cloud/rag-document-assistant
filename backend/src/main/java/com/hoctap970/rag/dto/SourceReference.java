package com.hoctap970.rag.dto;

import java.util.UUID;

public record SourceReference(
        UUID documentId,
        String fileName,
        String section,
        int chunkIndex,
        Double score,
        String excerpt,
        int pageNumber
) {
}
