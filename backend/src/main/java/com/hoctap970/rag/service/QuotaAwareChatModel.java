package com.hoctap970.rag.service;

import java.time.Clock;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/** At most one bounded retry. Quota cooldowns protect subsequent requests. */
final class QuotaAwareChatModel implements ChatModel {
    @FunctionalInterface interface Pause { void sleep(long milliseconds) throws InterruptedException; }
    private final ChatModel delegate;
    private final QuotaRetryPolicy retries;

    QuotaAwareChatModel(ChatModel delegate) { this(delegate, Clock.systemUTC(), Thread::sleep); }
    QuotaAwareChatModel(ChatModel delegate, Clock clock, Pause pause) {
        this.delegate = delegate; this.retries = new QuotaRetryPolicy(clock, pause::sleep);
    }

    @Override public ChatResponse chat(ChatRequest request) {
        return retries.execute(() -> delegate.chat(request));
    }
}
