package com.hoctap970.rag.service;

import java.util.List;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.Response;

/** Document and query embeddings use the same model and the same quota cooldown. */
final class QuotaAwareEmbeddingModel implements EmbeddingModel {
    private final EmbeddingModel delegate;
    private final QuotaRetryPolicy retries;

    QuotaAwareEmbeddingModel(EmbeddingModel delegate, QuotaRetryPolicy retries) {
        this.delegate = delegate; this.retries = retries;
    }

    @Override public Response<Embedding> embed(String text) {
        return retries.execute(() -> delegate.embed(text));
    }

    @Override public Response<Embedding> embed(TextSegment segment) {
        return retries.execute(() -> delegate.embed(segment));
    }

    @Override public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        return retries.execute(() -> delegate.embedAll(segments));
    }

    @Override public EmbeddingResponse embed(EmbeddingRequest request) {
        return retries.execute(() -> delegate.embed(request));
    }

    @Override public int dimension() { return retries.execute(delegate::dimension); }
    @Override public String modelName() { return delegate.modelName(); }
}
