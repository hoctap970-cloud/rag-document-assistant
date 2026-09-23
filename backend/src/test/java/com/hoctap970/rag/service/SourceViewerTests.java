package com.hoctap970.rag.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.hoctap970.rag.config.RagProperties;
import com.hoctap970.rag.controller.DocumentController;
import com.hoctap970.rag.domain.IndexedDocument;
import com.hoctap970.rag.dto.ChatResponse;
import com.hoctap970.rag.dto.DocumentContent;
import com.hoctap970.rag.dto.UploadResponse;
import com.hoctap970.rag.exception.NotFoundException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceViewerTests {

    @Test
    void retrievedSourceOpensTheSameUploadedDocumentAndChunk() {
        DocumentParserService parser = mock(DocumentParserService.class);
        GeminiModelProvider provider = mock(GeminiModelProvider.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        ChatModel chatModel = mock(ChatModel.class);
        RagService service = new RagService(
                new DocumentFileValidator(), parser, new SectionExtractor(), provider,
                new RagProperties(900, 120, 800, 5, 0.55)
        );

        byte[] original = "original file bytes".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.docx", "application/octet-stream", original
        );
        String extractedText = "CHƯƠNG 1 TỔNG QUAN\nRAG truy xuất văn bản rồi tạo câu trả lời có dẫn nguồn.";
        when(parser.parse(file)).thenReturn(extractedText);
        when(provider.documentEmbeddingModel()).thenReturn(embeddingModel);
        when(provider.queryEmbeddingModel()).thenReturn(embeddingModel);
        when(provider.chatModel()).thenReturn(chatModel);
        when(embeddingModel.embedAll(anyList())).thenAnswer(invocation ->
                Response.from(Collections.nCopies(invocation.<java.util.List<?>>getArgument(0).size(),
                        Embedding.from(new float[]{1, 0})))
        );
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{1, 0})));
        when(chatModel.chat(anyString())).thenReturn("RAG truy xuất văn bản [Nguồn 1].");

        UploadResponse uploaded = service.upload(file);
        ChatResponse answer = service.ask("RAG làm gì?");
        assertThat(answer.sources()).hasSize(1);
        assertThat(answer.sources().getFirst().documentId()).isEqualTo(uploaded.documentId());

        DocumentContent content = service.getDocumentContent(answer.sources().getFirst().documentId());
        assertThat(content.fileName()).isEqualTo("notes.docx");
        assertThat(content.chunks()).hasSize(1);
        assertThat(content.chunks().getFirst().chunkIndex())
                .isEqualTo(answer.sources().getFirst().chunkIndex());
        assertThat(content.chunks().getFirst().text()).contains("RAG truy xuất văn bản");

        DocumentController controller = new DocumentController(service);
        assertThat(controller.original(uploaded.documentId()).getBody()).isEqualTo(original);
        assertThat(controller.original(uploaded.documentId()).getHeaders()
                .getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment");

        service.deleteDocument(uploaded.documentId());
        assertThatThrownBy(() -> service.getDocumentContent(uploaded.documentId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void pdfOriginalOpensInlineWithNoStoreHeaders() {
        RagService service = mock(RagService.class);
        UUID id = UUID.randomUUID();
        byte[] bytes = "%PDF-1.6".getBytes(StandardCharsets.UTF_8);
        when(service.getDocument(id)).thenReturn(new IndexedDocument(
                id, "bai-giang.pdf", "application/pdf", bytes.length, 100, 1, 1,
                Instant.now(), List.of(), bytes, List.of()
        ));

        var response = new DocumentController(service).original(id);
        assertThat(response.getBody()).isEqualTo(bytes);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("inline");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }
}
