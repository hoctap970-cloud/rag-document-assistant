package com.hoctap970.rag.service;

import java.util.*;
import java.nio.charset.StandardCharsets;
import com.hoctap970.rag.config.RagProperties;
import com.hoctap970.rag.domain.ParsedDocument;
import com.hoctap970.rag.exception.BadRequestException;
import com.hoctap970.rag.exception.AiServiceException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RagLifecycleTests {
    private final DocumentParserService parser = mock(DocumentParserService.class);
    private final GeminiModelProvider models = mock(GeminiModelProvider.class);
    private final EmbeddingModel embedding = mock(EmbeddingModel.class);
    private final ChatModel chat = mock(ChatModel.class);
    private RagService service;
    @BeforeEach void setUp() {
        var properties = new RagProperties(900, 120, 800, 5, 0.35);
        service = new RagService(new DocumentFileValidator(), parser, new SectionExtractor(), models, properties, new HybridRetriever(properties, models));
        when(parser.parseDetailed(any(), any())).thenAnswer(inv -> new ParsedDocument(
                new String(inv.<MockMultipartFile>getArgument(0).getBytes(), StandardCharsets.UTF_8), List.of()));
        when(models.documentEmbeddingModel()).thenReturn(embedding);
        when(models.queryEmbeddingModel()).thenReturn(embedding);
        when(models.chatModel()).thenReturn(chat);
        when(embedding.embedAll(anyList())).thenAnswer(inv -> Response.from(Collections.nCopies(
                inv.<List<?>>getArgument(0).size(), Embedding.from(new float[]{1, 0}))));
        when(embedding.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{1, 0})));
        when(chat.chat(any(ChatRequest.class))).thenReturn(answer("Phí là 215 [Nguồn 1]."));
    }

    @Test void explicitDocumentIdsIsolateEvidenceEvenWhenVectorScoresAreIdentical() {
        var old = service.upload(file("old.docx", "Phí cũ là 100."));
        var current = service.upload(file("current.docx", "Phí mới là 215."));
        var result = service.ask("Phí là bao nhiêu?", List.of(current.documentId()));
        assertThat(result.sources()).allMatch(source -> source.documentId().equals(current.documentId())).hasSize(1);
        assertThat(result.sources()).noneMatch(source -> source.documentId().equals(old.documentId()));
        assertThat(service.ask("Phí là bao nhiêu?").sources()).hasSize(2);
    }
    @Test void missingScopeCannotSilentlySearchTheRemainingDocuments() {
        service.upload(file("other.docx", "Phí là 215."));
        assertThatThrownBy(() -> service.ask("Phí?", List.of(UUID.randomUUID())))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("không còn");
        verifyNoInteractions(chat);
    }
    @Test void successfulReplacementRemovesOldVectorsAndAnEmbeddingFailureKeepsThePreviousCopy() {
        var first = service.upload(file("policy.docx", "Phí cũ 100."));
        var replacement = service.upload(file("POLICY.docx", "Phí mới 215."));
        assertThat(service.documentCount()).isEqualTo(1);
        assertThat(service.ask("Phí mới?").sources()).allMatch(source -> source.documentId().equals(replacement.documentId()));
        assertThatThrownBy(() -> service.getDocument(first.documentId())).isInstanceOf(com.hoctap970.rag.exception.NotFoundException.class);
        when(embedding.embedAll(anyList())).thenThrow(new IllegalStateException("Embedding unavailable"));
        assertThatThrownBy(() -> service.upload(file("policy.docx", "Phí 999."))).isInstanceOf(AiServiceException.class);
        assertThat(service.getDocument(replacement.documentId()).chunks().getFirst().text()).contains("215");
        assertThat(service.documentCount()).isEqualTo(1);
    }
    @Test void clearingTheLibraryDuringAnUploadCannotRepopulateItFromTheOldRequest() {
        when(embedding.embedAll(anyList())).thenAnswer(inv -> {
            service.clearDocuments();
            return Response.from(Collections.nCopies(inv.<List<?>>getArgument(0).size(), Embedding.from(new float[]{1,0})));
        });
        assertThatThrownBy(() -> service.upload(file("slow.docx", "Phí 215.")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("chưa được lưu");
        assertThat(service.documentCount()).isZero(); assertThat(service.chunkCount()).isZero();
    }
    @Test void aFailedQueryEmbeddingStillAllowsKeywordRetrievalWithAnExplicitWarning() {
        service.upload(file("identifiers.docx", "Mã XY-218 được hỗ trợ 43 tháng."));
        when(embedding.embed(anyString())).thenThrow(new IllegalStateException("429 RESOURCE_EXHAUSTED"));
        var result = service.ask("Mã XY-218 có thời hạn bao lâu?");
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().getFirst().score()).isNull();
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("BM25"));
    }
    @Test void aBadCitationCanBeRepairedOnceAndHugeCitationNumbersCannotCrashValidation() {
        service.upload(file("policy.docx", "Phí 215."));
        when(chat.chat(any(ChatRequest.class))).thenReturn(answer("Phí 215 [Nguồn 999999999999999999999999]"), answer("Phí 215 [Nguồn 1]"));
        assertThat(service.ask("Phí?").answer()).contains("[Nguồn 1]");
        verify(chat, times(2)).chat(any(ChatRequest.class));
    }
    @Test void deletingAndClearingRemoveBothTextAndVectors() {
        var first = service.upload(file("first.docx", "Phí 215."));
        var second = service.upload(file("second.docx", "Phí 216."));
        service.deleteDocument(first.documentId());
        assertThat(service.ask("Phí?").sources()).allMatch(source -> source.documentId().equals(second.documentId()));
        service.clearDocuments();
        assertThatThrownBy(() -> service.ask("Phí?")).isInstanceOf(BadRequestException.class);
        assertThat(service.listDocuments()).isEmpty(); assertThat(service.chunkCount()).isZero();
    }
    private MockMultipartFile file(String name, String text) { return new MockMultipartFile("file", name, "", text.getBytes(StandardCharsets.UTF_8)); }
    private dev.langchain4j.model.chat.response.ChatResponse answer(String text) {
        return dev.langchain4j.model.chat.response.ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }
}
