package com.hoctap970.rag.domain;

import java.util.List;

public record ParsedDocument(String text, List<String> warnings) {
    public ParsedDocument {
        warnings = List.copyOf(warnings);
    }
}
