package com.hoctap970.rag.service;

import com.hoctap970.rag.config.ParsingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RagExamFixtureTests {
    @Test
    void exportsRealPracticeDocumentAndVerifiesAllAnswerEvidenceSurvivesParsing() throws Exception {
        var parser = new DocumentParserService(mock(DocumentVisionService.class), new ParsingProperties(false, 250, 40));
        var parsed = parser.parseDetailed(new MockMultipartFile("file", "de-thu-nhieu.docx", "", RagExamFixture.docx()));
        String text = new SectionExtractor().extract(parsed.text()).stream()
                .map(section -> section.text()).collect(java.util.stream.Collectors.joining("\n"));
        for (var question : RagExamFixture.QUESTIONS) {
            for (String evidence : question.evidenceTerms()) assertThat(text).contains(evidence);
        }
        assertThat(RagExamFixture.export().resolve("de-thu-nhieu.docx")).exists();
    }
}
