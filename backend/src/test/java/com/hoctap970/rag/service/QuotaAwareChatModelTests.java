package com.hoctap970.rag.service;

import java.time.*;
import java.util.ArrayList;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuotaAwareChatModelTests {
    private final ChatRequest request = ChatRequest.builder().messages(UserMessage.from("kiểm thử")).build();
    @Test void minuteQuotaWaitsForProviderDelayAndRetriesExactlyOnce() {
        var delegate = mock(ChatModel.class); var waits = new ArrayList<Long>();
        var result = dev.langchain4j.model.chat.response.ChatResponse.builder().aiMessage(dev.langchain4j.data.message.AiMessage.from("ok")).build();
        when(delegate.chat(request)).thenThrow(new IllegalStateException("429 retry in 12.4s.")).thenReturn(result);
        var model = new QuotaAwareChatModel(delegate, Clock.systemUTC(), waits::add);
        assertThat(model.chat(request)).isSameAs(result); assertThat(waits).containsExactly(13_400L);
        verify(delegate, times(2)).chat(request);
    }
    @Test void dailyQuotaFailsImmediatelyAndSubsequentCallsDoNotSpendAnotherRequest() {
        var delegate = mock(ChatModel.class); var waits = new ArrayList<Long>();
        var failure = new IllegalStateException("429 GenerateRequestsPerDayPerProjectPerModel-FreeTier retryDelay\":\"16191s\"");
        when(delegate.chat(request)).thenThrow(failure);
        var model = new QuotaAwareChatModel(delegate, Clock.fixed(Instant.parse("2026-10-08T19:30:00Z"), ZoneOffset.UTC), waits::add);
        assertThatThrownBy(() -> model.chat(request)).isSameAs(failure);
        assertThatThrownBy(() -> model.chat(request)).isSameAs(failure);
        verify(delegate, times(1)).chat(request); assertThat(waits).isEmpty();
        assertThat(AiFailureMessages.describe(failure, "Không thể trả lời.")).contains("quota ngày", "60 giây không giải quyết");
    }
    @Test void invalidKeyAndLongMinuteCooldownDoNotRetryAndPublicAdviceNeverIncludesSecrets() {
        var delegate = mock(ChatModel.class);
        when(delegate.chat(request)).thenThrow(new IllegalStateException("403 api_key_invalid secret-value"));
        var model = new QuotaAwareChatModel(delegate, Clock.systemUTC(), milliseconds -> fail("Không chờ khi key sai"));
        assertThatThrownBy(() -> model.chat(request)).isInstanceOf(IllegalStateException.class);
        verify(delegate).chat(request);
        assertThat(AiFailureMessages.describe(new IllegalStateException("403 api_key_invalid secret-value"), "Lỗi.")).doesNotContain("secret-value");
        reset(delegate); when(delegate.chat(request)).thenThrow(new IllegalStateException("429 retry in 120s"));
        assertThatThrownBy(() -> model.chat(request)).isInstanceOf(IllegalStateException.class); verify(delegate).chat(request);
    }
}
