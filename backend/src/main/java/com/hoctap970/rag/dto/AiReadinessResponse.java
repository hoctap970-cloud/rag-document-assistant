package com.hoctap970.rag.dto;

import java.time.Instant;

public record AiReadinessResponse(boolean ready, String chatModel, String embeddingModel,
        String chatStatus, String embeddingStatus, String message, Instant checkedAt) {}
