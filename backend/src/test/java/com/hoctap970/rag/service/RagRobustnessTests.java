package com.hoctap970.rag.service;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hoctap970.rag.config.RagProperties;
import com.hoctap970.rag.domain.ParsedDocument;
import com.hoctap970.rag.exception.AiServiceException;
import com.hoctap970.rag.exception.BadRequestException;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagRobustnessTests {
    private final DocumentParserService parser = mock(DocumentParserService.class);
    private final GeminiModelProvider models = mock(GeminiModelProvider.class);
    private final EmbeddingModel embedding = mock(EmbeddingModel.class);
    private final ChatModel chat = mock(ChatModel.class);
    private RagService service;

    @BeforeEach void setUp() {
        service = service(new RagProperties(900, 120, 800, 5, 0.35));
        when(parser.parseDetailed(any(), any())).thenAnswer(inv -> new ParsedDocument(
                new String(inv.<MockMultipartFile>getArgument(0).getBytes(), StandardCharsets.UTF_8), List.of()));
        when(models.documentEmbeddingModel()).thenReturn(embedding);
        when(models.queryEmbeddingModel()).thenReturn(embedding);
        when(models.chatModel()).thenReturn(chat);
        when(embedding.embedAll(anyList())).thenAnswer(inv -> vectors(inv.<List<?>>getArgument(0).size(), 1, 0));
        when(embedding.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{1, 0})));
        when(chat.chat(any(ChatRequest.class))).thenReturn(answer("Phí 215 [Nguồn 1]."));
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    void nonFiniteDocumentVectorsDoNotReplaceThePreviousDocument(float value) {
        var original = service.upload(file("policy.docx", "Phí 215."));
        when(embedding.embedAll(anyList())).thenAnswer(inv -> vectors(inv.<List<?>>getArgument(0).size(), value, 1));
        assertThatThrownBy(() -> service.upload(file("POLICY.docx", "Phí 999."))).isInstanceOf(AiServiceException.class);
        assertThat(service.listDocuments()).extracting(document -> document.id()).containsExactly(original.documentId());
        assertThat(service.getDocument(original.documentId()).chunks().getFirst().text()).contains("215");
    }

    @Test void zeroVectorsAreRejectedBeforeAnyDocumentIsCommitted() {
        when(embedding.embedAll(anyList())).thenAnswer(inv -> vectors(inv.<List<?>>getArgument(0).size(), 0, 0));
        assertThatThrownBy(() -> service.upload(file("zero.docx", "Phí 215."))).isInstanceOf(AiServiceException.class);
        assertThat(service.documentCount()).isZero();
        assertThat(service.chunkCount()).isZero();
    }

    @Test void incorrectBatchSizeCannotBeHiddenByTheFinalTotalCount() {
        when(embedding.embedAll(anyList())).thenReturn(Response.from(List.of()));
        assertThatThrownBy(() -> service.upload(file("broken.docx", "Phí 215."))).isInstanceOf(AiServiceException.class);
        assertThat(service.documentCount()).isZero();
    }

    @Test void mixedVectorDimensionsCannotPoisonAnExistingLibrary() {
        var original = service.upload(file("policy.docx", "Phí 215."));
        when(embedding.embedAll(anyList())).thenAnswer(inv -> vectors(inv.<List<?>>getArgument(0).size(), 1, 0, 0));
        assertThatThrownBy(() -> service.upload(file("new.docx", "Phí 216.")))
                .isInstanceOf(AiServiceException.class).hasMessageContaining("Số chiều");
        assertThat(service.documentCount()).isEqualTo(1);
        assertThat(service.getDocument(original.documentId()).fileName()).isEqualTo("policy.docx");
        service.clearDocuments();
        assertThat(service.upload(file("new.docx", "Phí 216.")).chunkCount()).isPositive();
    }

    @Test void invalidQueryVectorFallsBackToKeywordsWithNoInventedSemanticScore() {
        service.upload(file("codes.docx", "Mã XY-218 được hỗ trợ 43 tháng."));
        when(embedding.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{Float.NaN, 1})));
        var result = service.ask("Mã XY-218 có thời hạn bao lâu?");
        assertThat(result.sources()).hasSize(1).allMatch(source -> source.score() == null);
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("BM25"));
    }

    @Test void deletingEvidenceDuringGenerationCannotReturnADeadSource() {
        var original = service.upload(file("policy.docx", "Phí 215."));
        when(chat.chat(any(ChatRequest.class))).thenAnswer(inv -> {
            service.deleteDocument(original.documentId());
            return answer("Phí 215 [Nguồn 1].");
        });
        assertThatThrownBy(() -> service.ask("Phí?"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("làm nguồn");
    }

    @Test void replacingEvidenceDuringGenerationCannotReturnTheOldVersion() {
        service.upload(file("policy.docx", "Phí 215."));
        when(chat.chat(any(ChatRequest.class))).thenAnswer(inv -> {
            service.upload(file("policy.docx", "Phí 999."));
            return answer("Phí 215 [Nguồn 1].");
        });
        assertThatThrownBy(() -> service.ask("Phí?"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("làm nguồn");
        assertThat(service.listDocuments()).hasSize(1);
    }

    @Test void changingAnUnrelatedDocumentDoesNotInvalidateScopedEvidence() {
        var original = service.upload(file("policy.docx", "Phí 215."));
        var unrelated = service.upload(file("other.docx", "Phí 999."));
        when(chat.chat(any(ChatRequest.class))).thenAnswer(inv -> {
            service.deleteDocument(unrelated.documentId());
            return answer("Phí 215 [Nguồn 1].");
        });
        assertThat(service.ask("Phí?", List.of(original.documentId())).sources()).hasSize(1);
    }

    @Test void slowerUploadCannotOverwriteANewerUploadOfTheSameName() {
        AtomicBoolean entered = new AtomicBoolean();
        when(embedding.embedAll(anyList())).thenAnswer(inv -> {
            if (entered.compareAndSet(false, true)) service.upload(file("POLICY.docx", "Phí mới 999."));
            return vectors(inv.<List<?>>getArgument(0).size(), 1, 0);
        });
        assertThatThrownBy(() -> service.upload(file("policy.docx", "Phí cũ 215.")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Tệp cùng tên");
        assertThat(service.listDocuments()).hasSize(1);
        assertThat(service.getDocument(service.listDocuments().getFirst().id()).chunks().getFirst().text()).contains("999");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[nguồn 999]", "[NGUỒN 999]", "[Nguon 999]", "[ Nguồn  999 ]", "[Nguồn 0]", "[Nguồn -1]", "[Nguồn ABC]", "[Nguồn 1x]", "[Nguồn 1, 2]", "[Nguồn\u00a0999]", "[Nguo\u0302\u0300n 999]"})
    void citationFormattingCannotBypassRangeValidation(String citation) {
        service.upload(file("policy.docx", "Phí 215."));
        when(chat.chat(any(ChatRequest.class))).thenReturn(answer("Phí 215 " + citation), answer("Phí 215 [Nguồn 1]"));
        assertThat(service.ask("Phí?").answer()).contains("[Nguồn 1]");
        verify(chat, times(2)).chat(any(ChatRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[ NGUỒN 1 ]", "[Nguồn\u00a0+01\u00a0]", "[Nguo\u0302\u0300n 1]"})
    void validCitationVariantsAreNormalizedForTheSourceButtons(String citation) {
        service.upload(file("policy.docx", "Phí 215."));
        when(chat.chat(any(ChatRequest.class))).thenReturn(answer("Phí 215 " + citation + "."));
        assertThat(service.ask("Phí?").answer()).contains("[Nguồn 1]");
        verify(chat).chat(any(ChatRequest.class));
    }

    @Test void storageLimitRefusesAnotherNameButAllowsAnAtomicReplacement() {
        service = service(new RagProperties(900, 120, 800, 5, 0.35, 40, 48000, 24000, 1, true, 1, 5000, 100000000L));
        service.upload(file("policy.docx", "Phí 215."));
        clearInvocations(embedding);
        assertThatThrownBy(() -> service.upload(file("other.docx", "Phí 216.")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("giới hạn lưu trong RAM");
        verify(embedding, never()).embedAll(anyList());
        assertThat(service.upload(file("policy.docx", "Phí mới 216.")).fileName()).isEqualTo("policy.docx");
        assertThat(service.documentCount()).isEqualTo(1);
    }

    @Test void byteCapacityIsCheckedBeforeReadingOrEmbeddingAnotherFile() {
        service = service(new RagProperties(900, 120, 800, 5, 0.35, 40, 48000, 24000, 1, true, 20, 5000, 20L));
        service.upload(file("policy.docx", "Phí 215."));
        clearInvocations(parser, embedding);
        assertThatThrownBy(() -> service.upload(file("other.docx", "Nội dung khác vượt giới hạn lưu.")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("giới hạn lưu trong RAM");
        verifyNoInteractions(parser, embedding);
    }

    @Test void chunkCapacityIsCheckedBeforeCallingTheEmbeddingApi() {
        service = service(new RagProperties(200, 20, 800, 5, 0.35, 40, 48000, 24000, 1, true, 20, 1, 100000000L));
        assertThatThrownBy(() -> service.upload(file("long.docx", "Dữ liệu thử phí 215. ".repeat(200))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("giới hạn lưu trong RAM");
        verifyNoInteractions(embedding);
        assertThat(service.documentCount()).isZero();
    }

    @Test void missingExplicitFilenameWarningSurvivesTheEmptyRetrievalResponse() {
        service.upload(file("policy.docx", "Phí 215."));
        var result = service.ask("Theo missing.pdf, phí là bao nhiêu?");
        assertThat(result.sources()).isEmpty();
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("missing.pdf"));
        verifyNoInteractions(chat);
    }

    @Test void dimensionMismatchInsideABatchIsRejectedBeforeCommit() {
        service = service(new RagProperties(200, 20, 800, 5, 0.35));
        when(embedding.embedAll(anyList())).thenAnswer(inv -> {
            int size = inv.<List<?>>getArgument(0).size();
            var vectors = new java.util.ArrayList<>(Collections.nCopies(size, Embedding.from(new float[]{1, 0})));
            vectors.set(size - 1, Embedding.from(new float[]{1, 0, 0}));
            return Response.from(vectors);
        });
        assertThatThrownBy(() -> service.upload(file("long.docx", "Dữ liệu thử phí 215. ".repeat(50))))
                .isInstanceOf(AiServiceException.class);
        assertThat(service.documentCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONTENT_FILTER", "OTHER", "TOOL_EXECUTION"})
    void interruptedModelOutputCannotBeReturnedAsACompleteAnswer(String reason) {
        service.upload(file("policy.docx", "Phí 215."));
        when(chat.chat(any(ChatRequest.class))).thenReturn(dev.langchain4j.model.chat.response.ChatResponse.builder()
                .aiMessage(AiMessage.from("Phí 215 [Nguồn 1]."))
                .finishReason(dev.langchain4j.model.output.FinishReason.valueOf(reason)).build());
        assertThatThrownBy(() -> service.ask("Phí?"))
                .isInstanceOf(AiServiceException.class).hasMessageContaining("dừng");
        verify(chat).chat(any(ChatRequest.class));
    }

    @Test void filteredCitationRepairIsNotReturnedAsSuccessfulEvenWithValidCitations() {
        service.upload(file("policy.docx", "Phí 215."));
        when(chat.chat(any(ChatRequest.class))).thenReturn(answer("Phí 215 [Nguồn 99]"),
                dev.langchain4j.model.chat.response.ChatResponse.builder().aiMessage(AiMessage.from("Phí 215 [Nguồn 1]"))
                        .finishReason(dev.langchain4j.model.output.FinishReason.CONTENT_FILTER).build());
        assertThatThrownBy(() -> service.ask("Phí?")).isInstanceOf(AiServiceException.class).hasMessageContaining("bộ lọc");
    }

    private RagService service(RagProperties properties) {
        return new RagService(new DocumentFileValidator(), parser, new SectionExtractor(), models, properties,
                new HybridRetriever(properties, models));
    }
    private Response<List<Embedding>> vectors(int count, float... vector) {
        return Response.from(Collections.nCopies(count, Embedding.from(vector)));
    }
    private MockMultipartFile file(String name, String text) {
        return new MockMultipartFile("file", name, "", text.getBytes(StandardCharsets.UTF_8));
    }
    private dev.langchain4j.model.chat.response.ChatResponse answer(String text) {
        return dev.langchain4j.model.chat.response.ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }
}
