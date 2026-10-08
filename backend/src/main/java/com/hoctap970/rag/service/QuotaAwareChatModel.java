package com.hoctap970.rag.service;

import java.time.Clock;
import java.util.regex.Pattern;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/** At most one bounded retry. A known daily quota failure is not sent repeatedly. */
final class QuotaAwareChatModel implements ChatModel {
    @FunctionalInterface interface Pause { void sleep(long milliseconds) throws InterruptedException; }
    private static final Pattern RETRY_SECONDS = Pattern.compile("retrydelay[\\\"\\s:]+(\\d+)s");
    private static final Pattern RETRY_TEXT = Pattern.compile("retry in (\\d+(?:\\.\\d+)?)s");
    private final ChatModel delegate;
    private final Clock clock;
    private final Pause pause;
    private long blockedUntil;
    private RuntimeException quotaFailure;

    QuotaAwareChatModel(ChatModel delegate) { this(delegate, Clock.systemUTC(), Thread::sleep); }
    QuotaAwareChatModel(ChatModel delegate, Clock clock, Pause pause) {
        this.delegate = delegate; this.clock = clock; this.pause = pause;
    }

    @Override public synchronized ChatResponse chat(ChatRequest request) {
        if (quotaFailure != null && clock.millis() < blockedUntil) throw quotaFailure;
        for (int attempt = 0; ; attempt++) {
            try { return delegate.chat(request); }
            catch (RuntimeException failure) {
                String detail = AiFailureMessages.details(failure);
                if (AiFailureMessages.dailyQuota(detail)) {
                    quotaFailure = failure;
                    blockedUntil = clock.millis() + Math.max(60_000, retryDelay(detail, 3_600_000));
                    throw failure;
                }
                boolean rateLimit = detail.contains("429") || detail.contains("ratelimit") || detail.contains("resource_exhausted");
                boolean temporary = detail.contains("503") || detail.contains("502") || detail.contains("timeout")
                        || detail.contains("timed out") || detail.contains("connectexception");
                if (attempt >= 1 || !rateLimit && !temporary) throw failure;
                long delay = rateLimit ? retryDelay(detail, 60_000) : 1_000;
                if (delay > 60_000) throw failure;
                try { pause.sleep(delay); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw failure; }
            }
        }
    }
    private long retryDelay(String detail, long fallback) {
        var match = RETRY_SECONDS.matcher(detail);
        if (!match.find()) { match = RETRY_TEXT.matcher(detail); if (!match.find()) return fallback; }
        try { return Math.max(1_000, (long) Math.ceil(Double.parseDouble(match.group(1)) * 1_000) + 1_000); }
        catch (NumberFormatException invalid) { return fallback; }
    }
}
