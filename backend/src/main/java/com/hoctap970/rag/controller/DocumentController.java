package com.hoctap970.rag.controller;

import java.util.Locale;
import java.util.Set;

import com.hoctap970.rag.dto.UploadResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of(".pdf", ".doc", ".docx");

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public UploadResponse upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File must not be empty"
            );
        }

        String fileName = file.getOriginalFilename();

        if (fileName == null || !hasSupportedExtension(fileName)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only PDF, DOC and DOCX files are supported"
            );
        }

        return new UploadResponse(
                fileName,
                file.getContentType(),
                file.getSize(),
                "File received successfully"
        );
    }

    private boolean hasSupportedExtension(String fileName) {
        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);

        return SUPPORTED_EXTENSIONS.stream()
                .anyMatch(lowerCaseName::endsWith);
    }
}
/*
Các annotation quan trọng:
- @RestController: class nhận HTTP request.
- @RequestMapping("/api/documents"): đường dẫn chung.
- @PostMapping("/upload"): endpoint nhận file.
- @RequestParam("file"): lấy trường tên file trong request.
- MultipartFile: kiểu dữ liệu của Spring dành cho file upload.
- ResponseStatusException: trả lỗi HTTP 400 nếu file không hợp lệ.
* */