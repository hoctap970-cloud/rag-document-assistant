package com.hoctap970.rag.dto;

public record SourceReference(
        String fileName,
        String section,
        int chunkIndex,
        double score,
        String excerpt
) {
}
