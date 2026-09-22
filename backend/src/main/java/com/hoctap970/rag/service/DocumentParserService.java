package com.hoctap970.rag.service;

import java.io.InputStream;

import com.hoctap970.rag.exception.DocumentProcessingException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentParserService {

    private final ApacheTikaDocumentParser parser = new ApacheTikaDocumentParser();

    public String parse(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            Document document = parser.parse(inputStream);
            String text = document.text();
            if (text == null || text.isBlank()) {
                throw new DocumentProcessingException(
                        "Không tìm thấy nội dung chữ trong tài liệu. Tệp có thể chỉ chứa ảnh quét.",
                        null
                );
            }
            return text;
        } catch (DocumentProcessingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DocumentProcessingException(
                    "Không thể đọc tài liệu. Hãy kiểm tra tệp có bị hỏng hoặc được đặt mật khẩu hay không.",
                    exception
            );
        }
    }
}
