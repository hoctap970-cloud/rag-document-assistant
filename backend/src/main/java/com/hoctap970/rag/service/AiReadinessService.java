package com.hoctap970.rag.service;

import java.time.Instant;
import com.hoctap970.rag.config.GeminiProperties;
import com.hoctap970.rag.dto.AiReadinessResponse;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.springframework.stereotype.Service;

/** On-demand, small real requests. Regular health checks never spend AI quota. */
@Service
public class AiReadinessService {
    private final GeminiModelProvider models;
    private final GeminiProperties properties;
    public AiReadinessService(GeminiModelProvider models, GeminiProperties properties) {
        this.models = models;
        this.properties = properties;
    }

    public synchronized AiReadinessResponse check() {
        if (!models.isConfigured()) return result(false, "Chưa cấu hình", "Chưa cấu hình",
                "Đặt GEMINI_API_KEY trong Run Configuration của IntelliJ rồi chạy lại.");
        String chat = "Chưa đạt", embedding = "Chưa đạt";
        StringBuilder advice = new StringBuilder();
        try {
            var vector = models.queryEmbeddingModel().embed("Kiểm tra tìm kiếm tài liệu NOVA.").content();
            if (vector == null || vector.dimension() != properties.embeddingDimensions()) {
                throw new IllegalStateException("Embedding dimension mismatch");
            }
            embedding = "Đạt";
        } catch (Exception failure) { advice.append(AiFailureMessages.describe(failure, "Kiểm tra embedding chưa đạt.")); }
        try {
            var response = models.chatModel().chat(ChatRequest.builder().messages(
                    UserMessage.from("Đây là kiểm tra kết nối. Chỉ trả lời: NOVA sẵn sàng.")).build());
            if (response.aiMessage() == null || response.aiMessage().text() == null || response.aiMessage().text().isBlank()) {
                throw new IllegalStateException("Empty model response");
            }
            chat = "Đạt";
        } catch (Exception failure) { advice.append(' ').append(AiFailureMessages.describe(failure, "Kiểm tra trả lời chưa đạt.")); }
        boolean ready = chat.equals("Đạt") && embedding.equals("Đạt");
        return result(ready, chat, embedding, ready
                ? "Chat và embedding vừa phản hồi thành công. Hãy tải một tài liệu thử để kiểm tra đọc file và dẫn nguồn."
                : advice.toString().strip());
    }

    private AiReadinessResponse result(boolean ready, String chat, String embedding, String message) {
        return new AiReadinessResponse(ready, properties.chatModel(), properties.embeddingModel(),
                chat, embedding, message, Instant.now());
    }
}
