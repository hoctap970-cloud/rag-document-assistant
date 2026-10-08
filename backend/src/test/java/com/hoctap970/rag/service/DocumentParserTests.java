package com.hoctap970.rag.service;

import java.io.ByteArrayOutputStream;
import com.hoctap970.rag.config.ParsingProperties;
import com.hoctap970.rag.domain.ReadingMode;
import com.hoctap970.rag.exception.BadRequestException;
import com.hoctap970.rag.exception.DocumentProcessingException;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class DocumentParserTests {
    private final DocumentVisionService vision = mock(DocumentVisionService.class);

    @Test
    void readsRealDocxTableWithRowContextAndKeepsBodyOrder() throws Exception {
        byte[] bytes;
        try (var word = new XWPFDocument(); var output = new ByteArrayOutputStream()) {
            word.createParagraph().createRun().setText("BÁO CÁO DOANH THU");
            var table = word.createTable(3, 3);
            String[][] rows = {{"Đơn vị", "Năm", "Doanh thu"}, {"Sao Mai", "2024", "620"},
                    {"Sao Mai", "2025", "873"}};
            for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) table.getRow(r).getCell(c).setText(rows[r][c]);
            word.createParagraph().createRun().setText("Đơn vị tính: triệu đồng.");
            word.write(output);
            bytes = output.toByteArray();
        }
        var result = parser(true, 40).parseDetailed(new MockMultipartFile("file", "table.docx", "", bytes));
        assertThat(result.text()).contains("Sao Mai | 2025 | 873", "Đơn vị | Năm | Doanh thu", "triệu đồng");
        assertThat(new SectionExtractor().extract(result.text()).stream().map(s -> s.text()).toList())
                .anyMatch(text -> text.contains("873"));
        verifyNoInteractions(vision);
    }

    @Test
    void mixedPdfUsesVisionForSparsePageAndRetainsOriginalPageNumbers() throws Exception {
        when(vision.readPdfPage(any())).thenReturn("Điều kiện nhận học bổng là GPA từ 3.7.");
        var result = parser(true, 40).parseDetailed(pdf(2));
        assertThat(result.text()).contains("[[TRANG 1]]", "[[TRANG 2]]", "GPA từ 3.7");
        assertThat(result.warnings()).anyMatch(w -> w.contains("1 trang bằng AI"));
        assertThat(new SectionExtractor().extract(result.text()))
                .anyMatch(s -> s.pageNumber() == 2 && s.text().contains("3.7"));
        verify(vision, times(1)).readPdfPage(any());
    }

    @Test
    void disabledVisionReportsUnreadPagesAndLimitFailsWithoutPartialSuccess() throws Exception {
        var result = parser(false, 40).parseDetailed(pdf(2));
        assertThat(result.warnings()).anyMatch(w -> w.contains("Trang 2") && w.contains("đang tắt"));
        assertThatThrownBy(() -> parser(true, 1).parseDetailed(pdf(3)))
                .isInstanceOf(DocumentProcessingException.class).hasMessageContaining("quá 1 trang");
        verifyNoInteractions(vision);
    }

    @Test
    void deepModeReadsEvenTextDensePdfPagesAndKeepsNativeEvidence() throws Exception {
        when(vision.readPdfPage(any())).thenReturn("Sơ đồ: A chuyển tới B; số liệu là 873.");
        var file = pdf(1);
        var automatic = parser(true, 40).parseDetailed(file);
        assertThat(automatic.text()).contains("Native page").doesNotContain("873");
        verifyNoInteractions(vision);

        var deep = parser(true, 40).parseDetailed(file, ReadingMode.DEEP);
        assertThat(deep.text()).contains("Native page", "873", "[[TRANG 1]]");
        assertThat(deep.warnings()).anyMatch(w -> w.contains("đọc kỹ từng trang"));
        verify(vision).readPdfPage(any());
    }

    @Test
    void deepModeRejectsTooManyPagesBeforeAnyVisionCall() throws Exception {
        assertThatThrownBy(() -> parser(true, 1).parseDetailed(pdf(2), ReadingMode.DEEP))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("quá 1 trang").hasMessageContaining("chưa gọi Gemini");
        verifyNoInteractions(vision);
    }

    @Test
    void deepModeCannotSilentlyIgnoreWordFilesOrDisabledVision() throws Exception {
        assertThatThrownBy(() -> parser(true, 40).parseDetailed(
                new MockMultipartFile("file", "sample.docx", "", new byte[]{1}), ReadingMode.DEEP))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("chỉ dành cho PDF");
        assertThatThrownBy(() -> parser(false, 40).parseDetailed(pdf(1), ReadingMode.DEEP))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("RAG_VISION_ENABLED");
        verifyNoInteractions(vision);
    }

    private DocumentParserService parser(boolean enabled, int visionLimit) {
        return new DocumentParserService(vision, new ParsingProperties(enabled, 250, visionLimit));
    }

    @Test void readsRealLegacyBinaryDocUsingTikaWithoutVision() throws Exception {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("/fixtures/simple.doc")) { bytes = input.readAllBytes(); }
        var parsed = parser(true, 40).parseDetailed(new MockMultipartFile("file", "simple.doc", "application/msword", bytes));
        assertThat(parsed.text()).contains("This is a simple file");
        assertThat(parsed.warnings()).anyMatch(w -> w.contains("lớp chữ"));
        verifyNoInteractions(vision);
    }

    @Test void blankPdfDoesNotUseVisionAndPasswordProtectedPdfFailsClearly() throws Exception {
        try (var document = new PDDocument(); var out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage()); document.save(out);
            assertThatThrownBy(() -> parser(true, 40).parseDetailed(new MockMultipartFile("file", "blank.pdf", "", out.toByteArray())))
                    .isInstanceOf(DocumentProcessingException.class).hasMessageContaining("Không tìm thấy");
            var protection = new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("owner", "secret", new org.apache.pdfbox.pdmodel.encryption.AccessPermission());
            document.protect(protection); out.reset(); document.save(out);
            assertThatThrownBy(() -> parser(true, 40).parseDetailed(new MockMultipartFile("file", "locked.pdf", "", out.toByteArray())))
                    .isInstanceOf(DocumentProcessingException.class).hasMessageContaining("mật khẩu");
        }
        verifyNoInteractions(vision);
    }

    @Test void automaticPdfCanKeepNativeTextAfterVisionFailureButDeepAndScansRemainStrict() throws Exception {
        byte[] bytes;
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf(1).getBytes()); var out = new ByteArrayOutputStream()) {
            var image = new java.awt.image.BufferedImage(600, 300, java.awt.image.BufferedImage.TYPE_INT_RGB);
            try (var content = new PDPageContentStream(document, document.getPage(0), PDPageContentStream.AppendMode.APPEND, true)) {
                content.drawImage(org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(document, image), 40, 400, 300, 150);
            }
            document.save(out); bytes = out.toByteArray();
        }
        when(vision.readPdfPage(any())).thenThrow(new DocumentProcessingException("Quota AI đã hết; thử lại sau.", null));
        var file = new MockMultipartFile("file", "visual.pdf", "", bytes);
        var parsed = parser(true, 40).parseDetailed(file);
        assertThat(parsed.text()).contains("Native page");
        assertThat(parsed.warnings()).anyMatch(w -> w.contains("giữ lớp chữ"));
        assertThatThrownBy(() -> parser(true, 40).parseDetailed(file, ReadingMode.DEEP)).isInstanceOf(DocumentProcessingException.class);
        assertThatThrownBy(() -> parser(true, 40).parseDetailed(pdf(2))).isInstanceOf(DocumentProcessingException.class);
    }

    @Test
    void visionReceivesReadablePageEvenWhenPdfResourcesAreInherited() throws Exception {
        byte[] bytes;
        try (var pdf = new PDDocument(); var out = new ByteArrayOutputStream()) {
            var page = new PDPage();
            pdf.addPage(page);
            try (var content = new PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(40, 750);
                content.showText("ZX-907: 37 months.");
                content.endText();
            }
            pdf.getPages().getCOSObject().setItem(org.apache.pdfbox.cos.COSName.RESOURCES, page.getResources());
            page.getCOSObject().removeItem(org.apache.pdfbox.cos.COSName.RESOURCES);
            pdf.save(out);
            bytes = out.toByteArray();
        }
        when(vision.readPdfPage(any())).thenAnswer(invocation -> {
            try (var sent = org.apache.pdfbox.Loader.loadPDF(invocation.<byte[]>getArgument(0))) {
                assertThat(sent.getNumberOfPages()).isEqualTo(1);
                assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(sent)).contains("ZX-907: 37 months");
            }
            return "Pin: [không đọc rõ] tháng.";
        });
        var result = parser(true, 40).parseDetailed(new MockMultipartFile("file", "inherited.pdf", "application/pdf", bytes));
        assertThat(result.text()).contains("ZX-907: 37 months", "[không đọc rõ]");
        assertThat(result.warnings()).anyMatch(w -> w.contains("chữ không đọc rõ"));
        verify(vision).readPdfPage(any());
    }

    private MockMultipartFile pdf(int pages) throws Exception {
        try (var pdf = new PDDocument(); var out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (var content = new PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(40, 750);
                content.showText("Native page: all employees receive training and information about safety procedures.");
                content.endText();
            }
            for (int i = 1; i < pages; i++) {
                var visualPage = new PDPage();
                pdf.addPage(visualPage);
                try (var content = new PDPageContentStream(pdf, visualPage)) {
                    content.addRect(40, 600, 160, 80); content.stroke();
                }
            }
            pdf.save(out);
            return new MockMultipartFile("file", "mixed.pdf", "application/pdf", out.toByteArray());
        }
    }
}
