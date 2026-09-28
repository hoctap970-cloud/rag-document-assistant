package com.hoctap970.rag.domain;

public record SectionContent(String title, String text, int pageNumber) {
    public SectionContent(String title, String text) {
        this(title, text, 0);
    }
}
