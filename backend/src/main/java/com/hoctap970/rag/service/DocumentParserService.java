package com.hoctap970.rag.service;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
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
                parsed = new WordContentExtractor(vision, properties).read(file);
            } else {
                try (InputStream stream = file.getInputStream(); var ole = new org.apache.poi.poifs.filesystem.POIFSFileSystem(stream)) {
                    if (!ole.getRoot().hasEntry("WordDocument")) throw new DocumentProcessingException("Nội dung tệp không phải Word DOC. Hãy kiểm tra định dạng thực hoặc xuất DOCX/PDF.", null);
                }
                try (InputStream stream = file.getInputStream()) {
                    parsed = new ParsedDocument(new ApacheTikaDocumentParser().parse(stream).text(),
                            List.of("File DOC được đọc theo lớp chữ. Nếu có hình/bảng phức tạp, nên xuất PDF hoặc DOCX để đọc và đối chiếu tốt hơn."));
                }
            }
            if (!name.endsWith(".pdf")) parsed = new ParsedDocument(escapePageMarkers(parsed.text()), parsed.warnings());
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
                boolean garbled = nativeText.codePoints().anyMatch(c -> c == 0xFFFD || c >= 0xE000 && c <= 0xF8FF);
                boolean visual = readMode == ReadingMode.DEEP
                        || hasLargeImage(pdf.getPage(index).getResources(), new HashSet<>(), 0);
                boolean empty = nativeText.isBlank() && !pdf.getPage(index).hasContents();
                pages.add(new PdfPagePlan(index, nativeText, !empty && (sparse || visual || garbled)));
            }
            // Check the whole plan first, so an over-limit upload uses no Gemini quota.
            long requiredPages = pages.stream().filter(PdfPagePlan::needsVision).count();
            if (properties.visionEnabled() && requiredPages > properties.maxVisionPages()) throw visionLimitExceeded();
            for (var page : pages) {
                int index = page.index();
                String nativeText = escapePageMarkers(page.text());
                String pageText = nativeText;
                if (page.needsVision() && properties.visionEnabled()) {
                    visionPages++;
                    try (PDDocument singlePage = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                        var sourcePage = pdf.getPage(index);
                        var importedPage = singlePage.importPage(sourcePage);
                        // Resources may be inherited from the source page tree rather than stored on the page.
                        importedPage.setResources(sourcePage.getResources());
                        singlePage.save(output);
                        String visualText = escapePageMarkers(vision.readPdfPage(output.toByteArray()));
                        // Keep native text so a visual model cannot silently omit a native fact.
                        pageText = nativeText + (visualText.isBlank() ? "" :
                                "\n[Phần đọc bằng AI từ trang]\n" + visualText);
                    } catch (DocumentProcessingException failure) {
                        if (readMode == ReadingMode.DEEP || nativeText.codePoints().filter(Character::isLetterOrDigit).count() < 60) throw failure;
                        warnings.add("Trang " + (index + 1) + ": đã giữ lớp chữ nhưng chưa đọc được hình bằng AI. " + failure.getMessage());
                        visionPages--;
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

    private String escapePageMarkers(String text) {
        return text == null ? "" : text.replaceAll("(?m)^\\s*\\[\\[TRANG (\\d+)]]\\s*$", "[Nội dung tài liệu] [[TRANG $1]]");
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

}
