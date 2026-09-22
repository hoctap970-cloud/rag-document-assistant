package com.hoctap970.rag.dto;

import java.util.List;

public record ChatResponse(
        String question,
        String answer,
        List<SourceReference> sources
) {
}
