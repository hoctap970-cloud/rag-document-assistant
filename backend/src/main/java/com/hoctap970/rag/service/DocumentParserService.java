package com.hoctap970.rag.service;

import java.io.InputStream;

import com.hoctap970.rag.exception.DocumentProcessingException;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentParserService {

    private static final String NO_EXTRACTABLE_TEXT_MESSAGE =
            "Không tìm thấy chữ có thể chọn/copy trong tài liệu. PDF scan hoặc chữ đã chuyển thành hình/nét vẽ cần OCR trước khi tải lên.";

    private final ApacheTikaDocumentParser parser = new ApacheTikaDocumentParser();

    public String parse(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            Document document = parser.parse(inputStream);
            String text = document.text();
            if (text == null || text.isBlank()) {
                throw new DocumentProcessingException(NO_EXTRACTABLE_TEXT_MESSAGE, null);
            }
            return text;
        } catch (BlankDocumentException exception) {
            throw new DocumentProcessingException(NO_EXTRACTABLE_TEXT_MESSAGE, exception);
        } catch (DocumentProcessingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DocumentProcessingException(
                    "Không thể trích xuất chữ từ tài liệu. Nếu là PDF xuất để in hoặc scan, hãy dùng bản có thể chọn/copy chữ hoặc OCR; cũng kiểm tra tệp có bị hỏng hay đặt mật khẩu.",
                    exception
            );
        }
    }
}
