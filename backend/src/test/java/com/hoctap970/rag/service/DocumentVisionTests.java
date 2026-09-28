package com.hoctap970.rag.service;

import com.hoctap970.rag.exception.DocumentProcessingException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentVisionTests {
    private final ChatModel chat = mock(ChatModel.class);

    @Test
    void truncatedTranscriptionFailsInsteadOfIndexingPartialPage() {
        when(chat.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("Đây là phần đầu trang"))
                .finishReason(FinishReason.LENGTH).build());
        assertThatThrownBy(() -> vision().readPdfPage(new byte[]{1, 2}))
                .isInstanceOf(DocumentProcessingException.class).hasMessageContaining("bị cắt");
    }

    @Test
    void explicitBlankPageMarkerDoesNotBecomeDocumentContent() {
        when(chat.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("[TRANG TRỐNG]"))
                .finishReason(FinishReason.STOP).build());
        assertThat(vision().readPdfPage(new byte[]{1, 2})).isEmpty();
    }

    private DocumentVisionService vision() {
        var models = mock(GeminiModelProvider.class);
        when(models.chatModel()).thenReturn(chat);
        return new DocumentVisionService(models);
    }
}
