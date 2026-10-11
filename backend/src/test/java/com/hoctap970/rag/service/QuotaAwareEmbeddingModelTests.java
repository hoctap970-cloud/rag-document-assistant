package com.hoctap970.rag.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuotaAwareEmbeddingModelTests {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-11T00:00:00Z"), ZoneOffset.UTC);

    @Test void dailyEmbeddingQuotaBlocksBothUploadAndQueryWithoutExtraProviderCalls() {
        var documentDelegate = mock(EmbeddingModel.class);
        var queryDelegate = mock(EmbeddingModel.class);
        var failure = new IllegalStateException("429 EmbedContentTokensPerDayPerProject retryDelay\":\"200s\"");
        when(documentDelegate.embedAll(anyList())).thenThrow(failure);
        var retries = new QuotaRetryPolicy(clock, milliseconds -> fail("Quota ngày không tự retry"));
        var document = new QuotaAwareEmbeddingModel(documentDelegate, retries);
        var query = new QuotaAwareEmbeddingModel(queryDelegate, retries);
        assertThatThrownBy(() -> document.embedAll(List.of(TextSegment.from("nội dung")))).isSameAs(failure);
        assertThatThrownBy(() -> query.embed("câu hỏi")).isSameAs(failure);
        assertThatThrownBy(() -> document.embedAll(List.of(TextSegment.from("tải lại")))).isSameAs(failure);
        verify(documentDelegate, times(1)).embedAll(anyList()); verifyNoInteractions(queryDelegate);
    }

    @Test void legacyStringQueryRetriesAtMostOnceAndPreservesDelegateResponse() {
        var delegate = mock(EmbeddingModel.class); var waits = new ArrayList<Long>();
        var expected = Embedding.from(new float[]{1, 0});
        when(delegate.embed("câu hỏi")).thenThrow(new IllegalStateException("503 unavailable"))
                .thenReturn(Response.from(expected));
        var model = new QuotaAwareEmbeddingModel(delegate, new QuotaRetryPolicy(clock, waits::add));
        assertThat(model.embed("câu hỏi").content()).isEqualTo(expected);
        assertThat(waits).containsExactly(1_000L); verify(delegate, times(2)).embed("câu hỏi");
    }
}
