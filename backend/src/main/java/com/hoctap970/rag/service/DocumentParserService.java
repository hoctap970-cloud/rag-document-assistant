package com.hoctap970.rag.service;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.Base64;
import com.hoctap970.rag.config.ParsingProperties;
import com.hoctap970.rag.domain.ParsedDocument;
import com.hoctap970.rag.domain.ReadingMode;
import com.hoctap970.rag.exception.BadRequestException;
import com.hoctap970.rag.exception.DocumentProcessingException;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentParserService {

    private final DocumentVisionService vision;
    private final ParsingProperties properties;

    public DocumentParserService(DocumentVisionService vision, ParsingProperties properties) {
        this.vision = vision;
        this.properties = properties;
    }

    public String parse(MultipartFile file) {
        return parseDetailed(file).text();
    }

    public ParsedDocument parseDetailed(MultipartFile file) {
        return parseDetailed(file, ReadingMode.AUTO);
    }

    public ParsedDocument parseDetailed(MultipartFile file, ReadingMode readMode) {
        String name = String.valueOf(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (readMode == ReadingMode.DEEP && !name.endsWith(".pdf")) {
            throw new BadRequestException("Chế độ đọc kỹ từng trang chỉ dành cho PDF. Với Word, hãy chọn Tự động.");
        }
        if (readMode == ReadingMode.DEEP && !properties.visionEnabled()) {
            throw new BadRequestException("Đọc kỹ cần bật RAG_VISION_ENABLED ở backend rồi khởi động lại ứng dụng.");
        }
        try {
            ParsedDocument parsed;
            if (name.endsWith(".pdf")) {
                parsed = parsePdf(file.getBytes(), readMode);
            } else if (name.endsWith(".docx")) {
                parsed = parseDocx(file);
            } else {
                try (InputStream stream = file.getInputStream()) {
                    parsed = new ParsedDocument(new ApacheTikaDocumentParser().parse(stream).text(),
                            List.of("File DOC được đọc theo lớp chữ. Nếu có hình/bảng phức tạp, nên xuất PDF hoặc DOCX để đọc và đối chiếu tốt hơn."));
                }
            }
            if (parsed.text() == null || parsed.text().replaceAll("\\[\\[TRANG \\d+]]", "").isBlank()) {
                throw new DocumentProcessingException("Không tìm thấy nội dung đọc được trong tài liệu.", null);
            }
            if (parsed.text().contains("[không đọc rõ]") || parsed.text().contains("\uFFFD")) {
                List<String> warnings = new ArrayList<>(parsed.warnings());
                warnings.add("Có chữ không đọc rõ hoặc ký tự bị lỗi. Cần kiểm tra bản gốc trước khi dùng đáp án.");
                parsed = new ParsedDocument(parsed.text(), warnings);
            }
            return parsed;
        } catch (DocumentProcessingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DocumentProcessingException(
                    "Không thể đọc tài liệu. Kiểm tra định dạng thực, tệp hỏng, mật khẩu hoặc quyền sao chép.", exception);
        }
    }

    private record PdfPagePlan(int index, String text, boolean needsVision) {}

    private ParsedDocument parsePdf(byte[] bytes, ReadingMode readMode) throws Exception {
        List<String> warnings = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int visionPages = 0;
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            if (pdf.getNumberOfPages() > properties.maxPdfPages()) {
                throw new DocumentProcessingException("PDF vượt giới hạn " + properties.maxPdfPages()
                        + " trang. Hãy chia thành các phần rồi tải lần lượt.", null);
            }
            if (!pdf.getCurrentAccessPermission().canExtractContent()) {
                throw new DocumentProcessingException("PDF không cho phép trích xuất nội dung.", null);
            }
            if (readMode == ReadingMode.DEEP && pdf.getNumberOfPages() > properties.maxVisionPages()) {
                throw visionLimitExceeded();
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<PdfPagePlan> pages = new ArrayList<>();
            for (int index = 0; index < pdf.getNumberOfPages(); index++) {
                stripper.setStartPage(index + 1);
                stripper.setEndPage(index + 1);
                String nativeText = stripper.getText(pdf).strip();
                boolean sparse = nativeText.codePoints().filter(Character::isLetterOrDigit).count() < 60;
                boolean visual = readMode == ReadingMode.DEEP
                        || hasLargeImage(pdf.getPage(index).getResources(), new HashSet<>(), 0);
                pages.add(new PdfPagePlan(index, nativeText, sparse || visual));
            }
            // Check the whole plan first, so an over-limit upload uses no Gemini quota.
            long requiredPages = pages.stream().filter(PdfPagePlan::needsVision).count();
            if (properties.visionEnabled() && requiredPages > properties.maxVisionPages()) throw visionLimitExceeded();
            for (var page : pages) {
                int index = page.index();
                String nativeText = page.text();
                String pageText = nativeText;
                if (page.needsVision() && properties.visionEnabled()) {
                    visionPages++;
                    try (PDDocument singlePage = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                        var sourcePage = pdf.getPage(index);
                        var importedPage = singlePage.importPage(sourcePage);
                        // Resources may be inherited from the source page tree rather than stored on the page.
                        importedPage.setResources(sourcePage.getResources());
                        singlePage.save(output);
                        String visualText = vision.readPdfPage(output.toByteArray());
                        // Keep native text so a visual model cannot silently omit a native fact.
                        pageText = nativeText + (visualText.isBlank() ? "" :
                                "\n[PHẦN ĐỌC BẰNG AI TỪ TRANG]\n" + visualText);
                    }
                } else if (page.needsVision()) {
                    warnings.add("Trang " + (index + 1) + " có ít chữ hoặc có hình; đọc bằng AI đang tắt, nội dung có thể thiếu.");
                }
                text.append("\n[[TRANG ").append(index + 1).append("]]\n").append(pageText).append('\n');
            }
        }
        if (visionPages > 0) {
            warnings.add("Đã đọc " + visionPages + " trang bằng AI"
                    + (readMode == ReadingMode.DEEP ? " (chế độ đọc kỹ từng trang)" : "")
                    + ". Đối chiếu lại số liệu, bảng và chữ nhỏ với bản gốc.");
        }
        warnings.add("Bảng nhiều cột, công thức hoặc sơ đồ có thể mất bố cục khi trích xuất PDF; nguồn có kèm số trang để kiểm tra.");
        return new ParsedDocument(text.toString(), warnings);
    }

    private DocumentProcessingException visionLimitExceeded() {
        return new DocumentProcessingException("Tài liệu cần đọc quá " + properties.maxVisionPages()
                + " trang bằng AI. Hãy chia nhỏ hoặc OCR trước; chưa có dữ liệu nào được lưu và chưa gọi Gemini.", null);
    }

    private boolean hasLargeImage(PDResources resources, Set<PDResources> visited, int depth) throws Exception {
        if (resources == null || depth > 8 || !visited.add(resources)) return false;
        for (var name : resources.getXObjectNames()) {
            var object = resources.getXObject(name);
            if (object instanceof PDImageXObject image && image.getWidth() >= 500 && image.getHeight() >= 250) {
                return true;
            }
            if (object instanceof PDFormXObject form && hasLargeImage(form.getResources(), visited, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private ParsedDocument parseDocx(MultipartFile file) throws Exception {
        StringBuilder text = new StringBuilder();
        List<String> warnings = new ArrayList<>();
        try (InputStream stream = file.getInputStream(); XWPFDocument word = new XWPFDocument(stream)) {
            for (var header : word.getHeaderList()) text.append(header.getText()).append('\n');
            for (var element : word.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    text.append(paragraph.getText()).append('\n');
                } else if (element instanceof XWPFTable table) {
                    appendTable(text, table);
                }
            }
            for (var footnote : word.getFootnotes()) {
                for (var paragraph : footnote.getParagraphs()) text.append(paragraph.getText()).append('\n');
            }
            for (var endnote : word.getEndnotes()) {
                for (var paragraph : endnote.getParagraphs()) text.append(paragraph.getText()).append('\n');
            }
            for (var footer : word.getFooterList()) text.append(footer.getText()).append('\n');
            int imagesRead = 0;
            Set<String> seen = new HashSet<>();
            for (var picture : word.getAllPictures()) {
                String imageName = picture.getFileName().toLowerCase(Locale.ROOT);
                String mime = imageName.endsWith(".png") ? "image/png"
                        : imageName.endsWith(".jpg") || imageName.endsWith(".jpeg") ? "image/jpeg" : null;
                if (!seen.add(Base64.getEncoder().encodeToString(picture.getData()))) continue;
                if (!properties.visionEnabled() || mime == null) {
                    warnings.add("Có hình Word chưa được đọc: " + imageName + ". Có thể xuất PDF để xử lý.");
                    continue;
                }
                if (++imagesRead > properties.maxVisionPages()) {
                    throw new DocumentProcessingException("Word có quá nhiều hình cần đọc. Hãy chia tài liệu thành các phần.", null);
                }
                text.append("\nHÌNH ẢNH ").append(imagesRead).append(" (").append(imageName).append(")\n")
                        .append(vision.readImage(picture.getData(), mime)).append('\n');
            }
            if (imagesRead > 0) warnings.add("Đã đọc " + imagesRead
                    + " hình Word bằng AI; phần chữ từ hình được đặt cuối bản trích xuất. Hãy đối chiếu vị trí/chú thích và số liệu.");
        }
        return new ParsedDocument(text.toString(), warnings);
    }

    private void appendTable(StringBuilder text, XWPFTable table) {
        if (table.getRows().isEmpty()) return;
        String firstRow = table.getRows().getFirst().getTableCells().stream()
                .map(cell -> cell.getText().replaceAll("\\s+", " ").strip())
                .collect(java.util.stream.Collectors.joining(" | "));
        text.append("\nBảng — dòng đầu: ").append(firstRow).append('\n');
        for (int row = 1; row < table.getNumberOfRows(); row++) {
            // Repeat the first row as context, without assuming it really is a header.
            text.append("Dòng đầu của bảng: ").append(firstRow).append("\nDòng ").append(row + 1).append(": ");
            text.append(table.getRow(row).getTableCells().stream()
                    .map(cell -> cell.getText().replaceAll("\\s+", " ").strip())
                    .collect(java.util.stream.Collectors.joining(" | "))).append('\n');
        }
        for (var row : table.getRows()) for (var cell : row.getTableCells()) {
            for (var nested : cell.getTables()) appendTable(text, nested);
        }
    }
}
