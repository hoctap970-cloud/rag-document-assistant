package com.hoctap970.rag.dto;

import java.util.List;

public record ChatResponse(
        String question,
        String answer,
        List<SourceReference> sources,
        List<String> warnings
) {
    public ChatResponse(String question, String answer, List<SourceReference> sources) {
        this(question, answer, sources, List.of());
    }
}
