package com.hoctap970.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ChatRequest(
        @NotBlank(message = "Câu hỏi không được để trống")
        @Size(max = 2000, message = "Câu hỏi không được vượt quá 2.000 ký tự")
        String question,
        @Size(max = 20, message = "Chỉ chọn tối đa 20 tài liệu cho một câu hỏi")
        List<@NotNull UUID> documentIds
) {
    public ChatRequest(String question) { this(question, List.of()); }
}
