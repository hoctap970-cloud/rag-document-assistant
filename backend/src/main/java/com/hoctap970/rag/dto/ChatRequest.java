package com.hoctap970.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "Câu hỏi không được để trống")
        @Size(max = 2000, message = "Câu hỏi không được vượt quá 2.000 ký tự")
        String question
) {
}
