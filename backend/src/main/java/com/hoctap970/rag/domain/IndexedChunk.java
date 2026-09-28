package com.hoctap970.rag.domain;

public record IndexedChunk(
        int chunkIndex,
        String section,
        String text,
        int pageNumber
) {
    public IndexedChunk(int chunkIndex, String section, String text) {
        this(chunkIndex, section, text, 0);
    }
}
