package com.hoctap970.rag.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.hoctap970.rag.config.GeminiProperties;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Uses the real provider and SDK mapper, with the transport replaced before any request. */
class GeminiModelCompatibilityTests {
    private static final String MODEL = "gemini-3.8-flash";
    private static final String ANSWER = "NOVA sẵn sàng.";
    private static final byte[] IMAGE = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aPf8AAAAASUVORK5CYII=");
    private static final byte[] PDF = "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @ParameterizedTest(name = "Gemini 3.8 {0} request omits unsupported settings")
    @ValueSource(strings = {"text", "image", "pdf"})
    @Timeout(10)
    void chatAndVisionUseCompatibleSdkRequestsWithoutNetwork(String input) {
        var provider = new GeminiModelProvider(new GeminiProperties(
                "offline-test-key", MODEL, "gemini-embedding-001", 768, 8192, 120));
        var delegate = (GoogleGenAiChatModel) ReflectionTestUtils.getField(provider.chatModel(), "delegate");
        assertThat(delegate).isNotNull();
        var client = (Client) ReflectionTestUtils.getField(delegate, "client");
        assertThat(client).isNotNull();

        try (client) {
            var transport = mock(Models.class);
            // Keep reflection confined to this test: production still builds the real SDK client.
            ReflectionTestUtils.setField(client, "models", transport);
            assertThat(ReflectionTestUtils.getField(client, "models")).isSameAs(transport);
            var config = new AtomicReference<GenerateContentConfig>();
            var contents = new AtomicReference<List<Content>>();
            when(transport.generateContent(eq(MODEL), anyList(), any(GenerateContentConfig.class)))
                    .thenAnswer(invocation -> {
                        contents.set(invocation.getArgument(1));
                        config.set(invocation.getArgument(2));
                        return GenerateContentResponse.builder().candidates(List.of(Candidate.builder()
                                .content(Content.fromParts(Part.fromText(ANSWER)))
                                .finishReason("STOP").build())).build();
                    });

            String answer = switch (input) {
                case "image" -> new DocumentVisionService(provider).readImage(IMAGE, "image/png");
                case "pdf" -> new DocumentVisionService(provider).readPdfPage(PDF);
                default -> provider.chatModel().chat(ChatRequest.builder()
                        .messages(UserMessage.from("Kiểm tra kết nối NOVA."))
                        .build()).aiMessage().text();
            };

            assertThat(answer).isEqualTo(ANSWER);
            assertThat(config.get()).isNotNull();
            // Check the SDK's serialized form, not just nullable LangChain4j builder fields.
            assertThat(config.get().toJson())
                    .contains("\"maxOutputTokens\":8192")
                    .doesNotContain("\"temperature\"", "\"topP\"", "\"topK\"", "\"candidateCount\"",
                            "\"thinkingConfig\"");
            verify(transport).generateContent(eq(MODEL), anyList(), same(config.get()));
            verifyNoMoreInteractions(transport);

            var parts = contents.get().stream().flatMap(content -> content.parts().orElse(List.of()).stream()).toList();
            if (input.equals("text")) {
                assertThat(parts).allSatisfy(part -> assertThat(part.inlineData()).isEmpty());
                assertThat(parts).anySatisfy(part -> assertThat(part.text()).contains("Kiểm tra kết nối NOVA."));
            } else {
                assertThat(parts).anySatisfy(part -> {
                    assertThat(part.inlineData()).isPresent();
                    assertThat(part.inlineData().orElseThrow().mimeType())
                            .contains(input.equals("image") ? "image/png" : "application/pdf");
                    assertThat(part.inlineData().orElseThrow().data().orElseThrow())
                            .isEqualTo(input.equals("image") ? IMAGE : PDF);
                });
            }
        }
    }
}
