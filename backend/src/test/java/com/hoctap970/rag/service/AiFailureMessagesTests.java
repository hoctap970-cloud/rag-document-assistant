package com.hoctap970.rag.service;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class AiFailureMessagesTests {
    @Test void cyclicCauseChainCannotHangErrorHandlingOrExposeProviderBody() {
        var first = new IllegalStateException("SECRET-one");
        var second = new IllegalArgumentException("SECRET-two");
        first.initCause(second); second.initCause(first);
        assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                assertThat(AiFailureMessages.describe(first, "Không thể trả lời."))
                        .contains("Kiểm tra kết nối").doesNotContain("SECRET"));
    }

    @Test void nestedTimeoutAndDailyTokenQuotaReturnSpecificPublicAdvice() {
        assertThat(AiFailureMessages.describe(new IllegalStateException("private-body",
                new java.net.SocketTimeoutException("private-body")), "Lỗi."))
                .contains("quá thời gian chờ").doesNotContain("private-body");
        assertThat(AiFailureMessages.describe(new IllegalStateException("429 EmbedContentTokensPerDayPerProject"), "Lỗi."))
                .contains("quota ngày");
    }
}
