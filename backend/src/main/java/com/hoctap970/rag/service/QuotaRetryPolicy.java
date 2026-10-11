package com.hoctap970.rag.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** One bounded retry, with a per-model cooldown shared by subsequent calls. */
final class QuotaRetryPolicy {
    @FunctionalInterface interface Pause { void sleep(long milliseconds) throws InterruptedException; }

    private static final long MAX_RETRY_WAIT = 60_000;
    private static final long MAX_COOLDOWN = 86_400_000;
    private static final Pattern RETRY_SECONDS = Pattern.compile("retrydelay[\\\"\\s:]+(\\d+(?:\\.\\d+)?)s");
    private static final Pattern RETRY_TEXT = Pattern.compile("retry in (\\d+(?:\\.\\d+)?)s");

    private final Clock clock;
    private final Pause pause;
    private long blockedUntil;
    private RuntimeException quotaFailure;

    QuotaRetryPolicy() { this(Clock.systemUTC(), Thread::sleep); }
    QuotaRetryPolicy(Clock clock, Pause pause) { this.clock = clock; this.pause = pause; }

    synchronized <T> T execute(Supplier<T> action) {
        if (quotaFailure != null && clock.millis() < blockedUntil) throw quotaFailure;
        quotaFailure = null;
        for (int attempt = 0; ; attempt++) {
            try { return action.get(); }
            catch (RuntimeException failure) {
                String detail = AiFailureMessages.details(failure);
                if (AiFailureMessages.dailyQuota(detail)) {
                    block(failure, Math.max(MAX_RETRY_WAIT, retryDelay(detail, 3_600_000)));
                    throw failure;
                }
                boolean rateLimit = detail.contains("429") || detail.contains("ratelimit") || detail.contains("resource_exhausted");
                boolean temporary = detail.contains("503") || detail.contains("502") || detail.contains("timeout")
                        || detail.contains("timed out") || detail.contains("connectexception");
                long delay = rateLimit ? retryDelay(detail, MAX_RETRY_WAIT) : 1_000;
                if (attempt >= 1 || !rateLimit && !temporary || delay > MAX_RETRY_WAIT) {
                    if (rateLimit) block(failure, delay);
                    throw failure;
                }
                try { pause.sleep(delay); }
                catch (InterruptedException interrupted) {
                    // A cancelled wait does not mean the provider's quota has recovered.
                    if (rateLimit) block(failure, delay);
                    Thread.currentThread().interrupt();
                    throw failure;
                }
            }
        }
    }

    private void block(RuntimeException failure, long milliseconds) {
        quotaFailure = failure;
        long now = clock.millis();
        blockedUntil = now > Long.MAX_VALUE - milliseconds ? Long.MAX_VALUE : now + milliseconds;
    }

    private long retryDelay(String detail, long fallback) {
        var match = RETRY_SECONDS.matcher(detail);
        if (!match.find()) { match = RETRY_TEXT.matcher(detail); if (!match.find()) return fallback; }
        try {
            // Bound before converting: enormous provider values must not wrap into a short wait.
            BigDecimal milliseconds = new BigDecimal(match.group(1)).multiply(BigDecimal.valueOf(1_000))
                    .setScale(0, RoundingMode.CEILING).add(BigDecimal.valueOf(1_000));
            return milliseconds.max(BigDecimal.valueOf(1_000)).min(BigDecimal.valueOf(MAX_COOLDOWN)).longValueExact();
        } catch (ArithmeticException | NumberFormatException invalid) { return fallback; }
    }
}
