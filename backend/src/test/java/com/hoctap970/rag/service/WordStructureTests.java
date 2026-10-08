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
