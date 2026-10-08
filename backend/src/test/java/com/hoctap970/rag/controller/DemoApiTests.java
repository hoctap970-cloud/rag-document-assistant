package com.hoctap970.rag.controller;

import java.io.ByteArrayOutputStream;
import java.util.*;
import com.hoctap970.rag.config.*;
import com.hoctap970.rag.service.*;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real controllers, validation, parser and index; deterministic models consume no API quota. */
class DemoApiTests {
    private MockMvc mvc;
    private ChatModel chat;
    private GeminiModelProvider models;

    @BeforeEach void setUp() {
        models = mock(GeminiModelProvider.class);
        var embedding = mock(EmbeddingModel.class); chat = mock(ChatModel.class);
        when(models.isConfigured()).thenReturn(true);
        when(models.documentEmbeddingModel()).thenReturn(embedding);
        when(models.queryEmbeddingModel()).thenReturn(embedding);
        when(models.chatModel()).thenReturn(chat);
        when(embedding.embedAll(anyList())).thenAnswer(inv -> Response.from(Collections.nCopies(
                inv.<List<?>>getArgument(0).size(), Embedding.from(new float[]{1,0}))));
        when(embedding.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{1,0})));
        when(chat.chat(any(ChatRequest.class))).thenReturn(dev.langchain4j.model.chat.response.ChatResponse.builder()
                .aiMessage(AiMessage.from("Phí 215 triệu đồng [Nguồn 1].")).build());
        var properties = new RagProperties(900, 120, 800, 5, .35);
        var parser = new DocumentParserService(mock(DocumentVisionService.class), new ParsingProperties(false, 250, 40));
        var rag = new RagService(new DocumentFileValidator(), parser, new SectionExtractor(), models, properties,
                new HybridRetriever(properties, models));
        var readiness = new AiReadinessService(models, new GeminiProperties("test-only", "chat", "embedding", 2, 512, 30));
        mvc = MockMvcBuilders.standaloneSetup(new DocumentController(rag), new ChatController(rag),
                new HealthController(models, rag, readiness)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void uploadListScopeAnswerSourceOriginalReplaceDeleteAndClearWorkThroughHttp() throws Exception {
        byte[] bytes = word("Phí chính thức 215 triệu đồng. Không áp dụng mức 100 của năm trước.");
        String id = upload("policy.docx", bytes);
        mvc.perform(get("/api/documents")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Phí?\",\"documentIds\":[\"" + id + "\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sources[0].documentId").value(id))
                .andExpect(jsonPath("$.answer").value("Phí 215 triệu đồng [Nguồn 1]."));
        mvc.perform(get("/api/documents/" + id + "/content")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.chunks[0].text").value(org.hamcrest.Matchers.containsString("215")));
        byte[] original = mvc.perform(get("/api/documents/" + id + "/original")).andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(original).isEqualTo(bytes);
        String updated = upload("POLICY.docx", word("Phí 310 triệu đồng."));
        mvc.perform(get("/api/documents")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/documents/" + id + "/content")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/documents/" + updated)).andExpect(status().isOk());
        upload("other.docx", bytes);
        mvc.perform(delete("/api/documents")).andExpect(status().isOk());
        mvc.perform(get("/api/health")).andExpect(jsonPath("$.documentCount").value(0))
                .andExpect(jsonPath("$.chunkCount").value(0));
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Phí?\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void malformedRequestsAndUnreadableFilesReturnUsefulErrorsWithoutIndexingThem() throws Exception {
        for (String body : List.of("{}", "{\"question\":\"  \"}", "{\"question\":\"x\",\"documentIds\":[null]}",
                "{\"question\":\"x\",\"documentIds\":[\"invalid\"]}", "{")) {
            mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        }
        mvc.perform(get("/api/documents/not-a-uuid/content")).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/documents/upload")).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/documents/upload").file(new MockMultipartFile("file", "empty.pdf", "", new byte[0])))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/documents/upload").file(new MockMultipartFile("file", "script.html", "", "x".getBytes())))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/documents/upload").file(new MockMultipartFile("file", "broken.pdf", "", "fake PDF".getBytes())))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(multipart("/api/documents/upload").file(new MockMultipartFile("file", "word.docx", "", word("Nội dung"))).param("readMode", "DEEP"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/documents/upload").file(new MockMultipartFile("file", "word.docx", "", word("Nội dung"))).param("readMode", "WRONG"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/documents")).andExpect(jsonPath("$.length()").value(0));
        verifyNoInteractions(chat);
    }

    @Test void normalHealthUsesNoQuotaAndExplicitAiCheckReportsBothModels() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.geminiConfigured").value(true));
        verifyNoInteractions(chat);
        mvc.perform(post("/api/health/ai")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.ready").value(true)).andExpect(jsonPath("$.chatModel").value("chat"));
    }

    private String upload(String name, byte[] bytes) throws Exception {
        String json = mvc.perform(multipart("/api/documents/upload").file(new MockMultipartFile("file", name, "", bytes)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(json, "$.documentId");
    }
    private byte[] word(String text) throws Exception {
        try (var document = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text); document.write(out); return out.toByteArray();
        }
    }
}
