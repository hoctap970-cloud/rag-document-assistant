package com.hoctap970.rag.domain;

import java.util.Locale;
import com.hoctap970.rag.exception.BadRequestException;

public enum ReadingMode {
    AUTO, DEEP;

    public static ReadingMode parse(String value) {
        if (value == null || value.isBlank()) return AUTO;
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Chế độ đọc không hợp lệ. Chọn AUTO hoặc DEEP.");
        }
    }
}
