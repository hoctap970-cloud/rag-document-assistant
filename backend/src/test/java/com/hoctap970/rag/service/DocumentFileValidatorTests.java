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

    @Test void cleansBrowserPathsAndRejectsControlCharactersAndOversizedFiles() {
        assertThat(validator.validateAndCleanFileName(new MockMultipartFile("file", "C:\\fakepath\\Bài giảng.docx", "", new byte[]{1})))
                .isEqualTo("Bài giảng.docx");
        assertThatThrownBy(() -> validator.validateAndCleanFileName(new MockMultipartFile("file", "bad\nname.pdf", "", new byte[]{1})))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> validator.validateAndCleanFileName(new MockMultipartFile("file", "large.pdf", "", new byte[10 * 1024 * 1024 + 1])))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("10 MB");
    }
}
