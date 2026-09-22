package com.hoctap970.rag.service;

import com.hoctap970.rag.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentFileValidatorTests {

    private final DocumentFileValidator validator = new DocumentFileValidator();

    @Test
    void acceptsSupportedExtensionCaseInsensitively() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "Bai-Giang.PDF",
                "application/pdf",
                "sample".getBytes()
        );

        assertThat(validator.validateAndCleanFileName(file)).isEqualTo("Bai-Giang.PDF");
    }

    @Test
    void rejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.exe",
                "application/octet-stream",
                "sample".getBytes()
        );

        assertThatThrownBy(() -> validator.validateAndCleanFileName(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PDF, DOC và DOCX");
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[0]
        );

        assertThatThrownBy(() -> validator.validateAndCleanFileName(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không được để trống");
    }
}
