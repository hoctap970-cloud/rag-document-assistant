package com.hoctap970.rag.dto;

import java.util.List;
import java.util.UUID;

import com.hoctap970.rag.domain.IndexedChunk;

public record DocumentContent(
        UUID documentId,
        String fileName,
        List<IndexedChunk> chunks
) {
}
