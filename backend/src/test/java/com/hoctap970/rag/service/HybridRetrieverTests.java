package com.hoctap970.rag.service;

import java.util.*;
import com.hoctap970.rag.config.RagProperties;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HybridRetrieverTests {
    private final GeminiModelProvider models = mock(GeminiModelProvider.class);

    private HybridRetriever retriever(int results, int budget, int fullContext, int neighbors, boolean rerank) {
        return new HybridRetriever(new RagProperties(900, 120, 800, results, 0.55,
                40, budget, fullContext, neighbors, rerank), models);
    }

    @Test
    void fiveHardQuestionsRecoverEvidenceBuriedBelowDenseTopFive() {
        List<String> questions = List.of(
                "Mã ZX-907 bảo hành bao lâu?",
                "Doanh thu Sao Mai năm 2025 bao nhiêu?",
                "Sinh viên nghỉ vì bệnh cần nộp giấy gì?",
                "Phí gói Premium cho khách tổ chức là bao nhiêu?",
                "Tỷ lệ học bổng loại A so với loại B?");
        List<String> answers = List.of(
                "Mã ZX-907 được bảo hành 37 tháng, khác với ZX-906 là 12 tháng.",
                "Sao Mai | 2025 | Doanh thu: 873 triệu đồng.",
                "Sinh viên nghỉ vì bệnh phải nộp giấy xác nhận của bệnh viện.",
                "Gói Premium: khách cá nhân 190; khách tổ chức 470 nghìn đồng.",
                "Học bổng loại A: 80%; học bổng loại B: 45%.");
        for (int question = 0; question < questions.size(); question++) {
            List<EmbeddingMatch<TextSegment>> corpus = noisyCorpus();
            var target = match("target", 77, "Dữ liệu chính thức", answers.get(question), 0.2);
            corpus.add(target);
            var result = retriever(5, 10000, 0, 0, false).select(questions.get(question), corpus);
            assertThat(result.matches()).as(questions.get(question)).contains(target);
        }
    }

    @Test
    void retrievesAdjacentExceptionAndNeverMixesDocumentsWithSameChunkIndex() {
        var main = match("policy", 4, "Đăng ký", "Mã ZZ-88 đóng phí đăng ký 500 nghìn đồng.", 0.95);
        var exception = match("policy", 5, "Ngoại lệ", "Miễn phí nếu có giấy xác nhận hộ nghèo.", 0.05);
        var unrelated = match("other", 5, "Khác", "Phí vận chuyển hàng hóa.", 0.1);
        var result = retriever(1, 6000, 0, 1, false)
                .select("Phí đăng ký ZZ-88?", List.of(main, exception, unrelated));
        assertThat(result.matches()).contains(main, exception).doesNotContain(unrelated);
    }

    @Test
    void fullContextRetainsSeparatedFactsForSmallDocuments() {
        var a = match("a", 1, "Số liệu", "Sản lượng 2024 là 120.", 0.9);
        var b = match("a", 2, "Số liệu", "Sản lượng 2025 là 150.", 0.05);
        var result = retriever(1, 5000, 5000, 0, false).select("Tăng bao nhiêu phần trăm?", List.of(a, b));
        assertThat(result.complete()).isTrue();
        assertThat(result.matches()).containsExactly(a, b);
    }

    @Test
    void contextBudgetIsEnforcedAndOverviewReportsIncompleteCoverage() {
        var result = retriever(8, 2300, 0, 1, false).select("Tóm tắt toàn bộ tài liệu", noisyCorpus());
        assertThat(result.complete()).isFalse();
        assertThat(result.overview()).isTrue();
        int cost = result.matches().stream().mapToInt(m -> m.embedded().text().length()
                + m.embedded().metadata().getString("file_name").length()
                + m.embedded().metadata().getString("section").length() + 120).sum();
        assertThat(cost).isLessThanOrEqualTo(2300);
    }

    @Test
    void explicitFilenameScopesRetrievalAndUnavailableRerankingFallsBack() {
        var correct = match("policy", 1, "Tuyển sinh", "Mức học phí được quy định là 17 triệu.", 0.6);
        var wrong = match("other", 1, "Tuyển sinh", "Mức học phí cũ là 12 triệu.", 0.99);
        var result = retriever(1, 5000, 0, 0, true)
                .select("Học phí trong policy.pdf?", List.of(correct, wrong));
        assertThat(result.matches()).containsExactly(correct);
        var fallback = retriever(1, 5000, 0, 0, true).select("học phí", List.of(correct, wrong));
        assertThat(fallback.matches()).isNotEmpty();
    }

    @Test
    void unicodeAccentVariantsMatchLexically() {
        var target = match("abc", 1, "Quy định", "Điều kiện nhận trợ cấp mã AB-442 là đủ 36 tháng.", 0.1);
        var result = retriever(2, 5000, 0, 0, false)
                .select("dieu kien tro cap AB-442?", List.of(target, match("xyz", 1, "Khác", "Mùa hè nóng.", 0.1)));
        assertThat(result.matches()).containsExactly(target);
    }

    private List<EmbeddingMatch<TextSegment>> noisyCorpus() {
        List<EmbeddingMatch<TextSegment>> corpus = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            corpus.add(match("noise", i, "Phụ lục " + i,
                    "Thông tin quảng bá du lịch địa phương. ".repeat(15), 0.9));
        }
        return corpus;
    }

    @Test
    void repeatedBoilerplateCannotCrowdOutDistinctEvidence() {
        var corpus = new ArrayList<EmbeddingMatch<TextSegment>>();
        for (int i = 1; i <= 70; i++) corpus.add(match("policy", i, "Lưu ý",
                "Bảo hành thiết bị cần đọc điều kiện bảo hành theo quy định bảo hành.", 0.99));
        var evidence = match("policy", 100, "Điều kiện", "Thời hạn thiết bị là 37 tháng; pin chỉ 12 tháng.", 0.75);
        corpus.add(evidence);
        var result = retriever(2, 5000, 0, 0, false).select("Thời hạn bảo hành thiết bị?", corpus);
        assertThat(result.matches()).contains(evidence).hasSize(2);
    }

    @Test
    void filenameSubstringDoesNotAccidentallySelectAnotherDocument() {
        var shortName = match("a", 1, "Số liệu", "Doanh thu 12 triệu đồng.", 0.99);
        var requested = match("ba", 1, "Số liệu", "Doanh thu 873 triệu đồng.", 0.1);
        var result = retriever(5, 5000, 5000, 0, false)
                .select("Doanh thu trong ba.pdf là bao nhiêu?", List.of(shortName, requested));
        assertThat(result.matches()).containsExactly(requested);
    }

    @Test
    void longerFilenameWinsButSeparateExplicitMentionsStillSelectBoth() {
        var shortName = match("2026", 1, "Số liệu", "Doanh thu 12 triệu đồng.", 0.99);
        var longName = match("báo cáo 2026", 1, "Số liệu", "Doanh thu 873 triệu đồng.", 0.1);
        var engine = retriever(5, 5000, 5000, 0, false);
        assertThat(engine.select("Trong ‘báo cáo 2026.pdf’, doanh thu là bao nhiêu?", List.of(shortName, longName)).matches())
                .containsExactly(longName);
        assertThat(engine.select("So sánh báo cáo 2026.pdf và 2026.pdf", List.of(shortName, longName)).matches())
                .containsExactlyInAnyOrder(shortName, longName);
    }

    @Test
    void identicalTextFromDifferentDocumentsKeepsBothCitations() {
        var first = match("first", 1, "Điều kiện", "Bảo hành thiết bị 37 tháng.", 0.8);
        var second = match("second", 1, "Điều kiện", "Bảo hành thiết bị 37 tháng.", 0.8);
        assertThat(retriever(2, 5000, 0, 0, false)
                .select("Bảo hành thiết bị?", List.of(first, second)).matches())
                .containsExactlyInAnyOrder(first, second);
    }

    @Test
    void independentQuestionClausesReserveEvidenceForBothIdentifiers() {
        var corpus = new ArrayList<EmbeddingMatch<TextSegment>>();
        for (int i = 1; i <= 45; i++) corpus.add(match("policy", i, "Bảo hành",
                "Mã ZX-907 bảo hành 37 tháng. Quy định phần " + i + ".", 0.99));
        var fee = match("policy", 100, "Lệ phí", "Mã AB-442 có phí đăng ký 470 nghìn đồng.", 0.1);
        corpus.add(fee);
        var result = retriever(2, 5000, 0, 0, false)
                .select("Mã ZX-907 bảo hành bao lâu; mã AB-442 có phí đăng ký bao nhiêu?", corpus);
        assertThat(result.matches()).contains(fee).hasSize(2);
        assertThat(result.matches()).anyMatch(m -> m.embedded().text().contains("ZX-907"));
    }

    @Test
    void comparingExplicitFilesDoesNotSpendAllAnchorsOnOneFile() {
        var corpus = new ArrayList<EmbeddingMatch<TextSegment>>();
        for (int i = 1; i <= 45; i++) corpus.add(match("old", i, "Học phí",
                "Mức học phí 12 triệu đồng. Phụ lục " + i + ".", 0.99));
        var current = match("new", 1, "Học phí", "Mức học phí mới 17 triệu đồng.", 0.1);
        corpus.add(current);
        var result = retriever(2, 5000, 0, 0, false)
                .select("So sánh mức học phí trong old.pdf và new.pdf", corpus);
        assertThat(result.matches()).contains(current).hasSize(2);
        assertThat(result.matches()).anyMatch(m -> m.embedded().metadata().getString("file_name").equals("old.pdf"));
    }

    @Test
    void exactRequestedYearWinsOverHighSemanticScoreForWrongYear() {
        var wrong = match("report", 1, "Doanh thu", "Doanh thu năm 2024 là 120 triệu.", 0.99);
        var right = match("report", 2, "Doanh thu", "Doanh thu năm 2025 là 150 triệu.", 0.1);
        assertThat(retriever(1, 5000, 0, 0, false)
                .select("Doanh thu năm 2025 bao nhiêu?", List.of(wrong, right)).matches()).containsExactly(right);
    }

    @Test
    void comparisonBetweenTwoYearsKeepsBothLowAndHighScoringRows() {
        var corpus = new ArrayList<EmbeddingMatch<TextSegment>>();
        for (int i = 1; i <= 45; i++) corpus.add(match("report", i, "Doanh thu",
                "Doanh thu năm 2024 là 120 triệu. Chú thích " + i + ".", 0.99));
        var current = match("report", 100, "Doanh thu", "Doanh thu năm 2025 là 150 triệu.", 0.1);
        corpus.add(current);
        var result = retriever(2, 5000, 0, 0, false)
                .select("Doanh thu tăng bao nhiêu phần trăm từ năm 2024 đến năm 2025?", corpus);
        assertThat(result.matches()).contains(current).hasSize(2);
        assertThat(result.matches()).anyMatch(m -> m.embedded().text().contains("2024"));
    }

    @Test
    void explicitlyExcludedFilenameDoesNotBecomeTheOnlySelectedFile() {
        var wrong = match("old", 1, "Học phí", "Học phí 12 triệu đồng.", 0.99);
        var right = match("new", 1, "Học phí", "Học phí 17 triệu đồng.", 0.1);
        assertThat(retriever(2, 5000, 5000, 0, false)
                .select("Học phí bao nhiêu, không dùng old.pdf", List.of(wrong, right)).matches()).containsExactly(right);
    }

    @Test
    void visuallyEquivalentUnicodeHyphenInCodeFindsExactEvidence() {
        var wrong = match("policy", 1, "Bảo hành", "Mã ZX-906 được bảo hành 12 tháng.", 0.99);
        var right = match("policy", 2, "Bảo hành", "Mã ZX-907 được bảo hành 37 tháng.", 0.1);
        assertThat(retriever(1, 5000, 0, 0, false)
                .select("Mã ZX\u2011907 bảo hành bao lâu?", List.of(wrong, right)).matches()).containsExactly(right);
    }

    @Test
    void allItemsRequestUsesOverviewCoverageWarningEvenWithoutSummaryKeyword() {
        assertThat(retriever(2, 5000, 0, 0, false)
                .select("Liệt kê tất cả các điều kiện và ngoại lệ", noisyCorpus()).overview()).isTrue();
    }

    @Test
    void nonexistentFilenameDoesNotSilentlySearchOtherUploadedFiles() {
        var current = match("current", 1, "Học phí", "Học phí 17 triệu đồng.", 0.99);
        var result = retriever(2, 5000, 5000, 0, false)
                .select("Học phí trong missing.pdf là bao nhiêu?", List.of(current));
        assertThat(result.matches()).isEmpty();
        assertThat(result.warnings()).anySatisfy(warning -> assertThat(warning)
                .contains("Không tìm thấy tệp", "missing.pdf"));
    }

    @Test
    void missingSecondComparisonFileDoesNotAnswerUsingOnlyTheFirst() {
        var current = match("current", 1, "Học phí", "Học phí 17 triệu đồng.", 0.99);
        var result = retriever(2, 5000, 5000, 0, false)
                .select("So sánh current.pdf và ‘báo cáo mới.docx’", List.of(current));
        assertThat(result.matches()).isEmpty();
        assertThat(result.warnings()).hasSize(1).first().asString().contains("bao cao moi.docx");
    }

    @Test
    void excludingMultipleJoinedFilenamesKeepsOnlyTheRemainingDocument() {
        var first = match("first", 1, "Học phí", "Học phí 12 triệu đồng.", 0.99);
        var second = match("second", 1, "Học phí", "Học phí 14 triệu đồng.", 0.99);
        var third = match("third", 1, "Học phí", "Học phí 17 triệu đồng.", 0.1);
        assertThat(retriever(2, 5000, 5000, 0, false)
                .select("Học phí hiện nay? Ngoại trừ first.pdf và second.pdf", List.of(first, second, third)).matches())
                .containsExactly(third);
    }

    @Test
    void negatedWrongYearDoesNotCompeteWithRequestedYearForExactAnchor() {
        var wrong = match("report", 1, "Doanh thu", "Doanh thu năm 2024 là 120 triệu.", 0.99);
        var right = match("report", 2, "Doanh thu", "Doanh thu năm 2025 là 150 triệu.", 0.1);
        assertThat(retriever(1, 5000, 0, 0, false)
                .select("Doanh thu năm 2025, không phải năm 2024, là bao nhiêu?", List.of(wrong, right)).matches())
                .containsExactly(right);
    }

    @Test
    void partialMultiYearCoverageWarnsWhenOnlyOneAnchorIsAllowed() {
        var old = match("report", 1, "Doanh thu", "Doanh thu năm 2024 là 120 triệu.", 0.99);
        var current = match("report", 2, "Doanh thu", "Doanh thu năm 2025 là 150 triệu.", 0.1);
        var result = retriever(1, 5000, 0, 0, false)
                .select("So sánh doanh thu năm 2024 và năm 2025", List.of(old, current));
        assertThat(result.matches()).hasSize(1);
        assertThat(result.warnings()).anySatisfy(warning -> assertThat(warning).contains("chưa giữ đủ bằng chứng"));
    }

    @Test
    void oversizedBestCandidateDoesNotPreventUsingAFittingRelevantChunk() {
        var oversized = match("report", 1, "Học phí", "Quy định học phí. ".repeat(200), 0.99);
        var fitting = match("report", 2, "Học phí", "Học phí mỗi năm là 17 triệu đồng.", 0.9);
        assertThat(retriever(1, 1000, 0, 0, false)
                .select("Quy định học phí?", List.of(oversized, fitting)).matches()).containsExactly(fitting);
    }

    @Test
    void questionAboutIneligibilityRetainsNegativeRuleInsteadOfPositiveLookalike() {
        var positive = match("policy", 1, "Hoàn học phí", "Sinh viên được hoàn học phí nếu hủy đăng ký đúng hạn.", 0.99);
        var negative = match("policy", 2, "Hoàn học phí", "Sinh viên không được hoàn học phí nếu tự thôi học.", 0.1);
        assertThat(retriever(1, 5000, 0, 0, false)
                .select("Sinh viên nào không được hoàn học phí?", List.of(positive, negative)).matches())
                .containsExactly(negative);
        assertThat(HybridRetriever.tokens("không được hoàn học phí")).contains("khong");
    }

    @Test void largeConfiguredResultLimitDoesNotOverflowAndDiscardOverviewEvidence() {
        var properties = new RagProperties(200, 20, 800, Integer.MAX_VALUE, .35,
                Integer.MAX_VALUE, 5000, 0, 0, false);
        var corpus = List.of(match("report", 1, "Phí", "Phí 12 triệu.", .8),
                match("report", 2, "Hạn", "Hạn 14 ngày.", .8));
        assertThat(new HybridRetriever(properties, mock(GeminiModelProvider.class))
                .select("Liệt kê tất cả các điều kiện", corpus).matches()).hasSize(2);
    }

    static EmbeddingMatch<TextSegment> match(String document, int index, String section, String text, double score) {
        var segment = TextSegment.from(text, Metadata.from(Map.of(
                "document_id", UUID.nameUUIDFromBytes(document.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),
                "file_name", document + ".pdf", "section", section, "chunk_index", index, "page_number", index)));
        return new EmbeddingMatch<>(score, document + index, Embedding.from(new float[]{1, 0}), segment);
    }
}
