package com.hoctap970.rag.service;

import java.util.List;

import com.hoctap970.rag.domain.SectionContent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SectionExtractorTests {

    private final SectionExtractor extractor = new SectionExtractor();

    @Test
    void extractsVietnameseHeadingsAndTheirContent() {
        String text = """
                CHƯƠNG 1 TỔNG QUAN
                RAG kết hợp truy xuất và mô hình sinh.

                1.1 Kiến trúc hệ thống
                Hệ thống gồm loader, splitter, embedding và generator.

                KẾT LUẬN
                RAG giúp câu trả lời bám sát tài liệu.
                """;

        List<SectionContent> sections = extractor.extract(text);

        assertThat(sections)
                .extracting(SectionContent::title)
                .containsExactly("CHƯƠNG 1 TỔNG QUAN", "1.1 Kiến trúc hệ thống", "KẾT LUẬN");
        assertThat(sections.get(1).text()).contains("loader", "embedding");
    }

    @Test
    void usesDefaultSectionWhenDocumentHasNoHeading() {
        List<SectionContent> sections = extractor.extract(
                "Đây là một đoạn văn bình thường có nội dung đủ dài để không bị nhận nhầm là tiêu đề."
        );

        assertThat(sections).hasSize(1);
        assertThat(sections.getFirst().title()).isEqualTo("Nội dung chính");
    }
}
