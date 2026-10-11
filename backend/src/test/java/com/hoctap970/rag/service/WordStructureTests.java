package com.hoctap970.rag.service;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import com.hoctap970.rag.config.ParsingProperties;
import com.hoctap970.rag.exception.DocumentProcessingException;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WordStructureTests {
    private final DocumentVisionService vision = mock(DocumentVisionService.class);

    @Test void visibleTextIncludesContentControlsTextboxesAndMathButExcludesDeletedRevisions() throws Exception {
        String body = """
                <w:sdt><w:sdtPr/><w:sdtContent><w:p><w:r><w:t>Mức phí xác nhận là 214.</w:t></w:r></w:p></w:sdtContent></w:sdt>
                <w:p><w:r><w:pict><v:shape><v:textbox><w:txbxContent><w:p><w:r><w:t>Ô văn bản: thời hạn 19 ngày.</w:t></w:r></w:p></w:txbxContent></v:textbox></v:shape></w:pict></w:r></w:p>
                <w:p><m:oMath><m:f><m:num><m:r><m:t>3</m:t></m:r></m:num><m:den><m:r><m:t>7</m:t></m:r></m:den></m:f></m:oMath></w:p>
                <w:p><w:del><w:r><w:delText>Giá cũ bị xóa 999.</w:delText></w:r></w:del><w:ins><w:r><w:t>Giá đã sửa là 215.</w:t></w:r></w:ins></w:p>
                <w:p><w:r><w:t>[[TRANG 999]]</w:t></w:r></w:p>
                """;
        String documentXml = "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:v=\"urn:schemas-microsoft-com:vml\" xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\"><w:body>" + body + "<w:sectPr/></w:body></w:document>";
        var result = parser(40).parseDetailed(file(replaceDocumentXml(documentXml)));
        assertThat(result.text()).contains("214", "19 ngày", "(3)/(7)", "215").doesNotContain("Giá cũ", "999.");
        assertThat(new SectionExtractor().extract(result.text())).allMatch(section -> section.pageNumber() == 0);
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("Công thức"));
        verifyNoInteractions(vision);
    }

    @Test void numberingKeepsTheActualStartOverrideAndStepOrder() throws Exception {
        byte[] bytes;
        try (var word = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            var numbering = word.createNumbering();
            var definition = CTAbstractNum.Factory.newInstance();
            definition.setAbstractNumId(BigInteger.ZERO);
            var level = definition.addNewLvl(); level.setIlvl(BigInteger.ZERO);
            level.addNewStart().setVal(BigInteger.ONE);
            level.addNewNumFmt().setVal(STNumberFormat.DECIMAL);
            level.addNewLvlText().setVal("%1.");
            var abstractId = numbering.addAbstractNum(new XWPFAbstractNum(definition));
            var numId = numbering.addNum(abstractId);
            var override = numbering.getNum(numId).getCTNum().addNewLvlOverride();
            override.setIlvl(BigInteger.ZERO); override.addNewStartOverride().setVal(BigInteger.valueOf(7));
            for (String text : new String[]{"Nộp hồ sơ gốc", "Nhận phiếu hẹn"}) {
                var p = word.createParagraph(); p.setNumID(numId); p.setNumILvl(BigInteger.ZERO); p.createRun().setText(text);
            }
            word.write(out); bytes = out.toByteArray();
        }
        assertThat(parser(40).parseDetailed(file(bytes)).text()).contains("7. Nộp hồ sơ gốc", "8. Nhận phiếu hẹn");
    }

    @Test void verticallyMergedCellsKeepTheEntityOnEachDataRow() throws Exception {
        byte[] bytes;
        try (var word = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            var table = word.createTable(3, 3);
            String[][] rows = {{"Xưởng", "Năm", "Sản lượng"}, {"Hải Âu", "2025", "128"}, {"", "2026", "160"}};
            for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) table.getRow(r).getCell(c).setText(rows[r][c]);
            table.getRow(1).getCell(0).getCTTc().addNewTcPr().addNewVMerge().setVal(STMerge.RESTART);
            table.getRow(2).getCell(0).getCTTc().addNewTcPr().addNewVMerge();
            word.write(out); bytes = out.toByteArray();
        }
        var result = parser(40).parseDetailed(file(bytes));
        assertThat(result.text()).contains("Hải Âu | 2025 | 128", "Hải Âu | 2026 | 160", "ô gộp dọc từ dòng 2");
    }

    @Test void imageTextStaysWithItsCaptionAndRepeatedImagesUseOneVisionCall() throws Exception {
        when(vision.readImage(any(), eq("image/png"))).thenReturn("Học phí là 215 nghìn đồng.");
        byte[] bytes;
        try (var word = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            word.createParagraph().createRun().setText("HỌC PHÍ LỚP HẢI ÂU");
            var image = png(0xFF00FFFF);
            word.createParagraph().createRun().addPicture(new ByteArrayInputStream(image), Document.PICTURE_TYPE_PNG, "fees.png", Units.toEMU(200), Units.toEMU(100));
            word.createParagraph().createRun().setText("QUY ĐỊNH KHÁC");
            word.createParagraph().createRun().addPicture(new ByteArrayInputStream(image), Document.PICTURE_TYPE_PNG, "same-fees.png", Units.toEMU(200), Units.toEMU(100));
            word.write(out); bytes = out.toByteArray();
        }
        var result = parser(40).parseDetailed(file(bytes));
        assertThat(result.text().indexOf("215 nghìn")).isBetween(result.text().indexOf("HỌC PHÍ"), result.text().indexOf("QUY ĐỊNH KHÁC"));
        verify(vision, times(1)).readImage(any(), eq("image/png"));
    }

    @Test void allWordImagesAreCountedBeforeSpendingVisionQuota() throws Exception {
        byte[] bytes;
        try (var word = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            for (int color : new int[]{0xFFFF0000, 0xFF0000FF}) word.createParagraph().createRun()
                    .addPicture(new ByteArrayInputStream(png(color)), Document.PICTURE_TYPE_PNG, "picture.png", Units.toEMU(20), Units.toEMU(20));
            word.write(out); bytes = out.toByteArray();
        }
        assertThatThrownBy(() -> parser(1).parseDetailed(file(bytes)))
                .isInstanceOf(DocumentProcessingException.class).hasMessageContaining("chưa gọi Gemini");
        verifyNoInteractions(vision);
    }

    @Test void renamedTextIsNotSilentlyAcceptedAsBinaryWord() {
        assertThatThrownBy(() -> parser(40).parseDetailed(new MockMultipartFile("file", "fake.doc", "application/msword", "text disguised as Word".getBytes())))
                .isInstanceOf(DocumentProcessingException.class);
    }

    @Test void alternativeContentUsesOnlyTheModernRepresentationAndHiddenRunsStayHidden() throws Exception {
        var xml = documentXml("""
                <w:p><mc:AlternateContent><mc:Choice Requires="v"><w:r><w:t>Giá hiện tại 215.</w:t></w:r></mc:Choice>
                  <mc:Fallback><w:r><w:t>Giá dự phòng cũ 999.</w:t></w:r></mc:Fallback></mc:AlternateContent></w:p>
                <w:p><w:r><w:rPr><w:vanish/></w:rPr><w:t>Chữ ẩn 888.</w:t></w:r>
                  <w:r><w:rPr><w:vanish w:val="false"/></w:rPr><w:t>Chữ hiện 214.</w:t></w:r></w:p>
                """);
        var result = parser(40).parseDetailed(file(replaceDocumentXml(xml)));
        assertThat(result.text()).contains("215", "214").doesNotContain("999", "888");
        assertThat(result.warnings()).anyMatch(w -> w.contains("chữ ẩn"));
    }

    @Test void nonBreakingSpacesCannotForgeWordPageNumbers() throws Exception {
        var xml = documentXml("<w:p><w:r><w:t>[[TRANG\u00A0 999]]</w:t></w:r></w:p>"
                + "<w:p><w:r><w:t>Phí thực tế 215.</w:t></w:r></w:p>");
        var result = parser(40).parseDetailed(file(replaceDocumentXml(xml)));
        assertThat(new SectionExtractor().extract(result.text())).allMatch(s -> s.pageNumber() == 0);
        assertThat(result.text()).contains("215");
    }

    @Test void wordWithOnlyAnUnreadableOrBlankImageIsNotIndexedAsReadableText() throws Exception {
        byte[] bytes;
        try (var word = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            word.createParagraph().createRun().addPicture(new ByteArrayInputStream(png(0xFFFFFFFF)),
                    Document.PICTURE_TYPE_PNG, "blank.png", Units.toEMU(200), Units.toEMU(100));
            word.write(out); bytes = out.toByteArray();
        }
        when(vision.readImage(any(), anyString())).thenReturn("");
        assertThatThrownBy(() -> parser(40).parseDetailed(file(bytes)))
                .isInstanceOf(DocumentProcessingException.class).hasMessageContaining("Không tìm thấy");
        assertThatThrownBy(() -> new DocumentParserService(vision, new ParsingProperties(false, 250, 40))
                .parseDetailed(file(bytes))).isInstanceOf(DocumentProcessingException.class).hasMessageContaining("Không tìm thấy");
    }

    @Test void footnoteTablesAreKeptAndTheirReferencesRemainIdentifiable() throws Exception {
        byte[] bytes;
        try (var word = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            var note = word.createFootnote();
            var table = note.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Ngoại lệ"); table.getRow(0).getCell(1).setText("Mức phí");
            table.getRow(1).getCell(0).setText("Hải Âu"); table.getRow(1).getCell(1).setText("214");
            var paragraph = word.createParagraph(); paragraph.createRun().setText("Mức phí tiêu chuẩn là 215.");
            paragraph.addFootnoteReference(note);
            word.write(out); bytes = out.toByteArray();
        }
        var result = parser(40).parseDetailed(file(bytes));
        assertThat(result.text()).contains("215", "Hải Âu | 214", "[Chú thích chân trang");
    }

    @Test void skippedAndHorizontallyMergedTableCellsKeepTheirLogicalColumnPositions() throws Exception {
        var xml = documentXml("""
                <w:tbl><w:tblPr/><w:tblGrid><w:gridCol/><w:gridCol/><w:gridCol/></w:tblGrid>
                  <w:tr><w:tc><w:tcPr><w:gridSpan w:val="2"/></w:tcPr><w:p><w:r><w:t>Đơn vị</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Mức phí</w:t></w:r></w:p></w:tc></w:tr>
                  <w:tr><w:trPr><w:gridBefore w:val="1"/></w:trPr>
                    <w:tc><w:p><w:r><w:t>Hải Âu</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>214</w:t></w:r></w:p></w:tc></w:tr>
                </w:tbl>
                """);
        var result = parser(40).parseDetailed(file(replaceDocumentXml(xml)));
        assertThat(result.text()).contains("Đơn vị | Đơn vị | Mức phí", "[Không có ô] | Hải Âu | 214", "gộp ngang 2 cột");
    }

    @Test void maliciousTableSpanIsRejectedBeforeExpandingBillionsOfColumns() throws Exception {
        var xml = documentXml("""
                <w:tbl><w:tblPr/><w:tblGrid/><w:tr>
                  <w:tc><w:tcPr><w:gridSpan w:val="2147483647"/></w:tcPr><w:p><w:r><w:t>214</w:t></w:r></w:p></w:tc>
                </w:tr></w:tbl>
                """);
        assertThatThrownBy(() -> parser(40).parseDetailed(file(replaceDocumentXml(xml))))
                .isInstanceOf(DocumentProcessingException.class).hasMessageContaining("1000 cột");
        verifyNoInteractions(vision);
    }

    @Test void extractedTextIsBoundedEvenWhenAWordUploadIsUnderTenMegabytes() throws Exception {
        var random = new java.util.Random(907);
        var text = new StringBuilder(DocumentParserService.MAX_EXTRACTED_CHARACTERS + 1);
        for (int i = 0; i <= DocumentParserService.MAX_EXTRACTED_CHARACTERS; i++) text.append((char) ('a' + random.nextInt(26)));
        var bytes = replaceDocumentXml(documentXml("<w:p><w:r><w:t>" + text + "</w:t></w:r></w:p>"));
        assertThat(bytes.length).isLessThan(10 * 1024 * 1024);
        assertThatThrownBy(() -> parser(40).parseDetailed(file(bytes)))
                .isInstanceOf(DocumentProcessingException.class).hasMessageContaining("2000000 ký tự");
        verifyNoInteractions(vision);
    }

    @Test void expandedMergedCellIsRejectedBeforeJoiningItAndReadingTheNextRowImage() throws Exception {
        byte[] bytes;
        try (var word = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            var table = word.createTable(2, 1);
            var cell = table.getRow(0).getCell(0);
            cell.setText(randomText(3_000));
            cell.getCTTc().addNewTcPr().addNewGridSpan().setVal(java.math.BigInteger.valueOf(1_000));
            table.getRow(1).getCell(0).getParagraphs().getFirst().createRun().addPicture(
                    new ByteArrayInputStream(png(0xFF000000)), Document.PICTURE_TYPE_PNG,
                    "later.png", Units.toEMU(200), Units.toEMU(100));
            word.write(out); bytes = out.toByteArray();
        }
        assertThatThrownBy(() -> parser(40).parseDetailed(file(bytes)))
                .isInstanceOf(DocumentProcessingException.class).hasMessageContaining("2000000 ký tự");
        verifyNoInteractions(vision);
    }

    @Test void repeatedHeaderIsBoundedDuringTableReadingBeforeALaterImageUsesQuota() throws Exception {
        byte[] bytes;
        try (var word = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            var table = word.createTable(450, 1);
            table.getRow(0).getCell(0).setText(randomText(5_000));
            table.getRow(449).getCell(0).getParagraphs().getFirst().createRun().addPicture(
                    new ByteArrayInputStream(png(0xFF000000)), Document.PICTURE_TYPE_PNG,
                    "later.png", Units.toEMU(200), Units.toEMU(100));
            word.write(out); bytes = out.toByteArray();
        }
        assertThatThrownBy(() -> parser(40).parseDetailed(file(bytes)))
                .isInstanceOf(DocumentProcessingException.class).hasMessageContaining("2000000 ký tự");
        verifyNoInteractions(vision);
    }

    private String randomText(int length) {
        var random = new java.util.Random(907);
        var result = new StringBuilder(length);
        for (int i = 0; i < length; i++) result.append((char) ('a' + random.nextInt(26)));
        return result.toString();
    }

    @Test void formattedPowersIndicesRadicalsAndStruckOutAmountsPreserveTheirMeaning() throws Exception {
        String xml = "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" "
                + "xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\"><w:body>" + """
                <w:p><w:r><w:t>x</w:t></w:r><w:r><w:rPr><w:vertAlign w:val="superscript"/></w:rPr><w:t>2</w:t></w:r>
                  <w:r><w:t> H</w:t></w:r><w:r><w:rPr><w:vertAlign w:val="subscript"/></w:rPr><w:t>2</w:t></w:r><w:r><w:t>O</w:t></w:r></w:p>
                <w:p><w:r><w:rPr><w:strike/></w:rPr><w:t>Giá cũ 999.</w:t></w:r><w:r><w:t> Giá mới 215.</w:t></w:r></w:p>
                <w:p><m:oMath><m:rad><m:deg><m:r><m:t>3</m:t></m:r></m:deg><m:e><m:r><m:t>x</m:t></m:r></m:e></m:rad>
                  <m:sSubSup><m:e><m:r><m:t>a</m:t></m:r></m:e><m:sub><m:r><m:t>i</m:t></m:r></m:sub>
                    <m:sup><m:r><m:t>2</m:t></m:r></m:sup></m:sSubSup></m:oMath></w:p>
                """ + "<w:sectPr/></w:body></w:document>";
        var result = parser(40).parseDetailed(file(replaceDocumentXml(xml)));
        assertThat(result.text()).contains("x^(2)", "H_(2)O", "[Chữ gạch bỏ: Giá cũ 999.]", "Giá mới 215.",
                "căn bậc (3) của (x)", "a_(i)^(2)");
    }

    @Test void picturesInsideHiddenRunsAreNotReintroducedAsUnplacedVisibleImages() throws Exception {
        byte[] bytes;
        try (var word = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            word.createParagraph().createRun().setText("Giá hiện hành 215.");
            var hidden = word.createParagraph().createRun(); hidden.setVanish(true);
            hidden.addPicture(new ByteArrayInputStream(png(0xFF000000)), Document.PICTURE_TYPE_PNG,
                    "old-fees.png", Units.toEMU(200), Units.toEMU(100));
            word.write(out); bytes = out.toByteArray();
        }
        when(vision.readImage(any(), anyString())).thenReturn("Giá cũ 999.");
        assertThat(parser(40).parseDetailed(file(bytes)).text()).contains("215").doesNotContain("999");
        verifyNoInteractions(vision);
    }

    private String documentXml(String body) {
        return "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" "
                + "xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\" xmlns:v=\"urn:schemas-microsoft-com:vml\">"
                + "<w:body>" + body + "<w:sectPr/></w:body></w:document>";
    }

    private DocumentParserService parser(int limit) { return new DocumentParserService(vision, new ParsingProperties(true, 250, limit)); }
    private MockMultipartFile file(byte[] bytes) { return new MockMultipartFile("file", "structure.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes); }
    private byte[] png(int color) throws IOException {
        var image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) image.setRGB(x, y, color);
        var out = new ByteArrayOutputStream(); ImageIO.write(image, "png", out); return out.toByteArray();
    }
    private byte[] replaceDocumentXml(String xml) throws Exception {
        byte[] original;
        try (var word = new XWPFDocument(); var out = new ByteArrayOutputStream()) { word.createParagraph(); word.write(out); original = out.toByteArray(); }
        var output = new ByteArrayOutputStream();
        try (var input = new ZipInputStream(new ByteArrayInputStream(original)); var zip = new ZipOutputStream(output)) {
            for (ZipEntry entry; (entry = input.getNextEntry()) != null;) {
                zip.putNextEntry(new ZipEntry(entry.getName()));
                zip.write(entry.getName().equals("word/document.xml") ? xml.getBytes(StandardCharsets.UTF_8) : input.readAllBytes());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
