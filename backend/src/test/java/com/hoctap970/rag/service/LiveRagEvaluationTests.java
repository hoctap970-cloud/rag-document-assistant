package com.hoctap970.rag.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import com.hoctap970.rag.config.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in: uses real Gemini requests and quota. Never runs in normal CI. */
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_RAG_EVAL", matches = "true")
class LiveRagEvaluationTests {
    @Test
    void answersFiveAdversarialQuestionsWithRealModels() throws Exception {
        String key = System.getenv("GEMINI_API_KEY");
        assertThat(key).as("Cần đặt GEMINI_API_KEY trong môi trường chạy kiểm thử").isNotBlank();
        var models = new GeminiModelProvider(new GeminiProperties(key,
                System.getenv().getOrDefault("GEMINI_CHAT_MODEL", "gemini-2.5-flash"),
                System.getenv().getOrDefault("GEMINI_EMBEDDING_MODEL", "gemini-embedding-001"), 768, 8192, 120));
        var properties = new RagProperties(1500, 220, 800, 14, 0.35, 40, 48000, 24000, 1, true);
        var parser = new DocumentParserService(new DocumentVisionService(models), new ParsingProperties(true, 250, 40));
        var service = new RagService(new DocumentFileValidator(), parser, new SectionExtractor(), models,
                properties, new HybridRetriever(properties, models));
        service.upload(new MockMultipartFile("file", "de-thu-nhieu.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", RagExamFixture.docx()));
        StringBuilder report = new StringBuilder("# Kết quả Gemini thực — cần đọc lại câu trả lời\n\n");
        List<String> failures = new ArrayList<>();
        for (var question : RagExamFixture.QUESTIONS) {
            try {
                var answer = service.ask(question.text());
                String normalized = answer.answer().toLowerCase().replace("3.4", "3,4");
                boolean pass = !answer.sources().isEmpty()
                        && question.answerTerms().stream().allMatch(normalized::contains);
                if (!pass) failures.add(question.text());
                report.append("## ").append(pass ? "Đạt kiểm tra từ khóa" : "Cần xem lại").append("\n\n")
                        .append(question.text()).append("\n\nChuẩn: ").append(question.expected())
                        .append("\n\nTrả lời: ").append(answer.answer()).append("\n\n");
                for (var source : answer.sources()) report.append("- ").append(source.fileName())
                        .append(" / đoạn ").append(source.chunkIndex()).append(": ").append(source.excerpt()).append("\n");
                for (String warning : answer.warnings()) report.append("\nLưu ý: ").append(warning).append("\n");
                report.append("\n");
            } catch (Exception exception) {
                failures.add(question.text());
                report.append("Lỗi dịch vụ ở câu hỏi: ").append(question.text())
                        .append(". Kiểm tra kết nối hoặc quota rồi chạy lại.\n\n");
            }
        }
        var directory = RagExamFixture.export();
        Files.writeString(directory.resolve("gemini-report.md"), report, StandardCharsets.UTF_8);
        assertThat(failures).as("Xem target/rag-evaluation/gemini-report.md; kiểm tra từ khóa không thay thế chấm nội dung")
                .isEmpty();
    }
}
