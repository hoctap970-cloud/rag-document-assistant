package com.hoctap970.rag.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.hoctap970.rag.domain.SectionContent;
import org.springframework.stereotype.Component;

@Component
public class SectionExtractor {

    private static final String DEFAULT_SECTION = "Nội dung chính";
    private static final Pattern KEYWORD_HEADING = Pattern.compile(
            "^(chương|phần|mục|điều|bài|section|chapter)\\s+[\\p{L}0-9IVXLCDM]+.*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern NUMBERED_HEADING = Pattern.compile(
            "^(?:\\d+(?:\\.\\d+)*[.)]?|[IVXLCDM]+[.)])\\s+\\S.*$",
            Pattern.CASE_INSENSITIVE
    );

    public List<SectionContent> extract(String rawText) {
        String normalized = normalize(rawText);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<SectionContent> sections = new ArrayList<>();
        String currentTitle = DEFAULT_SECTION;
        StringBuilder currentText = new StringBuilder();

        for (String rawLine : normalized.split("\\n")) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                appendParagraphBreak(currentText);
                continue;
            }

            if (isHeading(line)) {
                addSection(sections, currentTitle, currentText);
                currentTitle = abbreviate(line, 160);
                currentText.setLength(0);
            } else {
                if (!currentText.isEmpty() && currentText.charAt(currentText.length() - 1) != '\n') {
                    currentText.append(' ');
                }
                currentText.append(line);
            }
        }

        addSection(sections, currentTitle, currentText);
        if (sections.isEmpty()) {
            sections.add(new SectionContent(DEFAULT_SECTION, normalized));
        }
        return sections;
    }

    private boolean isHeading(String line) {
        if (line.length() > 160 || line.split("\\s+").length > 18) {
            return false;
        }

        String lower = line.toLowerCase(Locale.ROOT);
        if (KEYWORD_HEADING.matcher(lower).matches()) {
            return true;
        }

        if (NUMBERED_HEADING.matcher(line).matches() && !endsLikeSentence(line)) {
            return true;
        }

        long letters = line.codePoints().filter(Character::isLetter).count();
        return letters >= 4
                && line.equals(line.toUpperCase(Locale.ROOT))
                && !endsLikeSentence(line);
    }

    private boolean endsLikeSentence(String line) {
        return line.endsWith(".") || line.endsWith(",") || line.endsWith(";") || line.endsWith(":");
    }

    private void addSection(List<SectionContent> sections, String title, StringBuilder text) {
        String content = text.toString().trim();
        if (!content.isBlank()) {
            sections.add(new SectionContent(title, content));
        }
    }

    private void appendParagraphBreak(StringBuilder text) {
        if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') {
            text.append('\n');
        }
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00A0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String abbreviate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }
}
