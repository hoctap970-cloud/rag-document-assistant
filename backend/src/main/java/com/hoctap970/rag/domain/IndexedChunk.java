package com.hoctap970.rag.domain;

public record IndexedChunk(
        int chunkIndex,
        String section,
        String text
) {
}
