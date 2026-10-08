package com.hoctap970.rag.service;

import java.util.Locale;
import java.util.Set;

import com.hoctap970.rag.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
public class DocumentFileValidator {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "doc", "docx");

    public String validateAndCleanFileName(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Tệp tải lên không được để trống");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw new BadRequestException("Tệp vượt quá giới hạn 10 MB");
        }

        String originalName = file.getOriginalFilename();
        String cleanName = originalName == null ? "" : StringUtils.getFilename(StringUtils.cleanPath(originalName)).trim();

        if (cleanName.isBlank() || cleanName.length() > 255 || cleanName.codePoints().anyMatch(Character::isISOControl)) {
            throw new BadRequestException("Tên tệp không hợp lệ");
        }

        String extension = StringUtils.getFilenameExtension(cleanName);
        if (extension == null || !SUPPORTED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("Chỉ hỗ trợ tệp PDF, DOC và DOCX");
        }

        return cleanName;
    }
}
