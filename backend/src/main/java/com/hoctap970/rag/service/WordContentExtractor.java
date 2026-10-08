package com.hoctap970.rag.service;

import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import com.hoctap970.rag.config.ParsingProperties;
import com.hoctap970.rag.domain.ParsedDocument;
import com.hoctap970.rag.exception.DocumentProcessingException;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Node;

/** Reads visible OOXML text, tables and images in document order. */
final class WordContentExtractor {
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String M = "http://schemas.openxmlformats.org/officeDocument/2006/math";
    private final DocumentVisionService vision;
    private final ParsingProperties properties;
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, String> imageText = new HashMap<>();
    private final Set<String> seenImages = new HashSet<>();
    private final Map<String, Map<Integer, Integer>> listCounters = new HashMap<>();
    private XWPFDocument word;
    private int imagesRead;
    private boolean formulas;

    WordContentExtractor(DocumentVisionService vision, ParsingProperties properties) {
        this.vision = vision; this.properties = properties;
    }

    ParsedDocument read(MultipartFile file) throws Exception {
        try (InputStream stream = file.getInputStream(); XWPFDocument document = new XWPFDocument(stream)) {
            word = document;
            var pictures = word.getAllPackagePictures().stream().collect(java.util.stream.Collectors.toMap(
                    this::imageKey, p -> p, (first, ignored) -> first, LinkedHashMap::new)).values();
            if (properties.visionEnabled() && pictures.stream().filter(p -> mime(p) != null).count() > properties.maxVisionPages()) {
                throw new DocumentProcessingException("Word có quá nhiều hình cần đọc. Hãy chia tài liệu; chưa gọi Gemini.", null);
            }
            StringBuilder text = new StringBuilder();
            for (var header : word.getHeaderList()) appendParts(text, header.getBodyElements(), header::getPictureDataByID);
            appendBlocks(text, word.getDocument().getBody().getDomNode(), word::getPictureDataByID);
            for (var note : word.getFootnotes()) for (var p : note.getParagraphs()) appendParagraph(text, p.getCTP().getDomNode(), word::getPictureDataByID);
            for (var note : word.getEndnotes()) for (var p : note.getParagraphs()) appendParagraph(text, p.getCTP().getDomNode(), word::getPictureDataByID);
            for (var footer : word.getFooterList()) appendParts(text, footer.getBodyElements(), footer::getPictureDataByID);
            for (var picture : pictures) if (!seenImages.contains(imageKey(picture))) {
                String extracted = readImage(picture);
                if (!extracted.isBlank()) {
                    text.append("\n[Hình Word chưa xác định được vị trí]\n").append(extracted).append('\n');
                    warnings.add("Một hình Word được đặt cuối bản chữ vì chưa xác định được vị trí. Đối chiếu chú thích với bản gốc.");
                }
            }
            if (imagesRead > 0) warnings.add("Đã đọc " + imagesRead + " hình Word bằng AI. Phần chữ được đặt tại vị trí ảnh khi xác định được; kiểm tra số liệu với bản gốc.");
            if (formulas) warnings.add("Công thức Word được chuyển sang bản chữ; công thức nhiều tầng cần đối chiếu ký hiệu và thứ tự với bản gốc.");
            return new ParsedDocument(text.toString(), warnings.stream().distinct().toList());
        }
    }

    private void appendParts(StringBuilder out, List<IBodyElement> parts, Function<String, XWPFPictureData> resolver) {
        for (var part : parts) {
            if (part instanceof XWPFParagraph p) appendParagraph(out, p.getCTP().getDomNode(), resolver);
            else if (part instanceof XWPFTable table) appendTable(out, table.getCTTbl().getDomNode(), resolver);
            else if (part instanceof XWPFSDT control) out.append(control.getContent().getText()).append('\n');
        }
    }

    private void appendBlocks(StringBuilder out, Node container, Function<String, XWPFPictureData> resolver) {
        for (Node child = container.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (is(child, W, "del") || is(child, W, "moveFrom")) continue;
            if (is(child, W, "p")) appendParagraph(out, child, resolver);
            else if (is(child, W, "tbl")) appendTable(out, child, resolver);
            else appendBlocks(out, child, resolver); // Includes body-level content controls.
            if (out.length() > 2_000_000) throw new DocumentProcessingException("Word có quá nhiều nội dung. Hãy chia tài liệu thành các phần nhỏ hơn.", null);
        }
    }

    private void appendParagraph(StringBuilder out, Node paragraph, Function<String, XWPFPictureData> resolver) {
        String visible = inline(paragraph, resolver).strip();
        if (!visible.isBlank()) out.append(numberPrefix(paragraph)).append(visible).append('\n');
    }

    private String inline(Node node, Function<String, XWPFPictureData> resolver) {
        if (node == null) return "";
        if (is(node, W, "del") || is(node, W, "moveFrom") || is(node, W, "instrText")) return "";
        if ((is(node, W, "t") || is(node, M, "t") || "t".equals(node.getLocalName())
                && "http://schemas.openxmlformats.org/drawingml/2006/main".equals(node.getNamespaceURI()))) {
            StringBuilder value = new StringBuilder();
            for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeValue() != null) value.append(child.getNodeValue());
            }
            return value.toString();
        }
        if (is(node, W, "tab")) return "\t";
        if (is(node, W, "br") || is(node, W, "cr")) return "\n";
        if ("blip".equals(node.getLocalName()) || "imagedata".equals(node.getLocalName())) {
            String id = attribute(node, "embed");
            if (id.isEmpty()) id = attribute(node, "id");
            var picture = resolver.apply(id);
            if (picture != null) return "\n[Chữ từ hình tại vị trí này]\n" + readImage(picture) + "\n";
        }
        if (is(node, W, "sym")) { warnings.add("Word có ký hiệu bằng phông chuyên dụng chưa đọc được; đối chiếu bản gốc hoặc xuất PDF."); return "[Ký hiệu cần đối chiếu]"; }
        if (M.equals(node.getNamespaceURI())) {
            formulas = true;
            if (is(node, M, "f")) return "(" + inline(child(node, M, "num"), resolver) + ")/(" + inline(child(node, M, "den"), resolver) + ")";
            if (is(node, M, "sSup")) return inline(child(node, M, "e"), resolver) + "^(" + inline(child(node, M, "sup"), resolver) + ")";
            if (is(node, M, "sSub")) return inline(child(node, M, "e"), resolver) + "_(" + inline(child(node, M, "sub"), resolver) + ")";
        }
        StringBuilder result = new StringBuilder();
        for (Node nested = node.getFirstChild(); nested != null; nested = nested.getNextSibling()) {
            result.append(inline(nested, resolver));
            if (is(nested, W, "p")) result.append('\n');
        }
        return result.toString();
    }

    private record MergedCell(String value, int row) {}
    private void appendTable(StringBuilder out, Node table, Function<String, XWPFPictureData> resolver) {
        List<Node> rows = children(table, W, "tr");
        List<String> first = List.of();
        Map<Integer, MergedCell> merged = new HashMap<>();
        for (int r = 0; r < rows.size(); r++) {
            List<String> values = new ArrayList<>();
            List<String> mergeNotes = new ArrayList<>();
            int column = integer(value(child(rows.get(r), W, "trPr"), "gridBefore"), 0);
            for (Node cell : children(rows.get(r), W, "tc")) {
                Node cellProperties = child(cell, W, "tcPr");
                int span = Math.max(1, integer(value(cellProperties, "gridSpan"), 1));
                Node merge = child(cellProperties, W, "vMerge");
                StringBuilder content = new StringBuilder();
                appendBlocks(content, cell, resolver);
                String text = content.toString().replaceAll("\\s+", " ").strip();
                if (merge != null && !attribute(merge, "val").equals("restart")) {
                    var origin = merged.get(column);
                    if (origin != null) {
                        text = origin.value();
                        mergeNotes.add("Cột " + (column + 1) + " dùng ô gộp dọc từ dòng " + origin.row());
                    } else warnings.add("Bảng Word có ô gộp không xác định được ô đầu; không tự điền giá trị thiếu.");
                }
                for (int offset = 0; offset < span; offset++) {
                    if (merge != null) merged.put(column + offset, new MergedCell(text,
                            attribute(merge, "val").equals("restart") ? r + 1 : merged.getOrDefault(column, new MergedCell(text, r + 1)).row()));
                    else merged.remove(column + offset);
                }
                values.add(text);
                if (span > 1) mergeNotes.add("Ô bắt đầu ở cột " + (column + 1) + " gộp ngang " + span + " cột");
                column += span;
            }
            if (r == 0) {
                first = List.copyOf(values);
                out.append("\nBảng — dòng đầu: ").append(String.join(" | ", first)).append('\n');
            } else {
                out.append("Dòng đầu của bảng: ").append(String.join(" | ", first)).append("\nDòng ").append(r + 1)
                        .append(": ").append(String.join(" | ", values)).append('\n');
            }
            for (String note : mergeNotes) out.append('[').append(note).append("]\n");
        }
    }

    private String numberPrefix(Node paragraph) {
        Node numProperties = child(child(paragraph, W, "pPr"), W, "numPr");
        String id = value(numProperties, "numId");
        if (id.isBlank() || id.equals("0") || word.getNumbering() == null) return "";
        int level = integer(value(numProperties, "ilvl"), 0);
        if (level < 0 || level > 8) return "";
        try {
            Node settings = levelSettings(id, level);
            if (settings == null) return "";
            String format = value(settings, "numFmt"), template = value(settings, "lvlText");
            if (format.equals("bullet")) return "• ";
            var counters = listCounters.computeIfAbsent(id, ignored -> new HashMap<>());
            counters.merge(level, levelStart(id, level, settings), (previous, start) -> previous + 1);
            for (int lower : new ArrayList<>(counters.keySet())) if (lower > level) {
                String restart = value(levelSettings(id, lower), "lvlRestart");
                if (!restart.equals("0") && (restart.isBlank() || level < integer(restart, lower))) counters.remove(lower);
            }
            if (template.isBlank()) template = "%" + (level + 1) + ".";
            for (int l = 0; l <= level; l++) {
                Node parent = levelSettings(id, l);
                int count = counters.getOrDefault(l, levelStart(id, l, parent));
                String label = numberLabel(count, value(parent, "numFmt"));
                if (label == null) { warnings.add("Một kiểu đánh số Word chưa được tái tạo; đối chiếu thứ tự với bản gốc."); return ""; }
                template = template.replace("%" + (l + 1), label);
            }
            return template + " ";
        } catch (RuntimeException failure) {
            warnings.add("Có danh sách Word chưa đọc được cách đánh số; đối chiếu bản gốc."); return "";
        }
    }

    private Node levelSettings(String id, int level) {
        var numbering = word.getNumbering();
        if (numbering == null) return null;
        var number = numbering.getNum(new BigInteger(id));
        if (number == null) return null;
        Node concrete = number.getCTNum().getDomNode();
        Node override = children(concrete, W, "lvlOverride").stream()
                .filter(n -> integer(attribute(n, "ilvl"), -1) == level).findFirst().orElse(null);
        var abstractNumber = numbering.getAbstractNum(numbering.getAbstractNumID(new BigInteger(id)));
        if (abstractNumber == null) return null;
        Node settings = child(override, W, "lvl");
        if (settings == null) settings = children(abstractNumber.getCTAbstractNum().getDomNode(), W, "lvl").stream()
                .filter(n -> integer(attribute(n, "ilvl"), -1) == level).findFirst().orElse(null);
        return settings;
    }

    private int levelStart(String id, int level, Node settings) {
        Node concrete = word.getNumbering().getNum(new BigInteger(id)).getCTNum().getDomNode();
        Node override = children(concrete, W, "lvlOverride").stream()
                .filter(n -> integer(attribute(n, "ilvl"), -1) == level).findFirst().orElse(null);
        return integer(value(override, "startOverride"), integer(value(settings, "start"), 1));
    }

    private String numberLabel(int count, String format) {
        if (format.isBlank() || format.equals("decimal")) return String.valueOf(count);
        if (format.equals("lowerLetter") || format.equals("upperLetter")) {
            if (count < 1) return null;
            StringBuilder label = new StringBuilder();
            for (int n = count; n > 0; n = (n - 1) / 26) label.insert(0, (char) ('A' + (n - 1) % 26));
            return format.equals("lowerLetter") ? label.toString().toLowerCase(Locale.ROOT) : label.toString();
        }
        if (format.equals("upperRoman") || format.equals("lowerRoman")) {
            if (count < 1 || count > 3999) return null;
            int[] values = {1000,900,500,400,100,90,50,40,10,9,5,4,1};
            String[] symbols = {"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};
            StringBuilder label = new StringBuilder();
            for (int i = 0; i < values.length; i++) while (count >= values[i]) { label.append(symbols[i]); count -= values[i]; }
            return format.equals("lowerRoman") ? label.toString().toLowerCase(Locale.ROOT) : label.toString();
        }
        return null;
    }

    private String readImage(XWPFPictureData picture) {
        String key = imageKey(picture);
        seenImages.add(key);
        if (imageText.containsKey(key)) return imageText.get(key);
        String mime = mime(picture);
        if (!properties.visionEnabled() || mime == null) {
            warnings.add("Có hình Word chưa được đọc: " + picture.getFileName() + ". Có thể xuất PDF để xử lý.");
            imageText.put(key, ""); return "";
        }
        imagesRead++;
        String extracted = vision.readImage(picture.getData(), mime);
        imageText.put(key, extracted);
        return extracted;
    }

    private String imageKey(XWPFPictureData picture) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(picture.getData())); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private String mime(XWPFPictureData picture) {
        if (picture.getPictureType() == Document.PICTURE_TYPE_PNG) return "image/png";
        if (picture.getPictureType() == Document.PICTURE_TYPE_JPEG) return "image/jpeg";
        return null;
    }
    private static boolean is(Node node, String namespace, String local) { return node != null && local.equals(node.getLocalName()) && namespace.equals(node.getNamespaceURI()); }
    private static Node child(Node node, String namespace, String local) {
        if (node == null) return null;
        for (Node next = node.getFirstChild(); next != null; next = next.getNextSibling()) if (is(next, namespace, local)) return next;
        return null;
    }
    private static List<Node> children(Node node, String namespace, String local) {
        List<Node> result = new ArrayList<>();
        if (node != null) for (Node next = node.getFirstChild(); next != null; next = next.getNextSibling()) if (is(next, namespace, local)) result.add(next);
        return result;
    }
    private static String attribute(Node node, String local) {
        if (node == null || node.getAttributes() == null) return "";
        for (int i = 0; i < node.getAttributes().getLength(); i++) {
            Node attribute = node.getAttributes().item(i);
            if (local.equals(attribute.getLocalName())) return attribute.getNodeValue();
        }
        return "";
    }
    private static String value(Node node, String local) { return attribute(child(node, W, local), "val"); }
    private static int integer(String text, int fallback) { try { return Integer.parseInt(text); } catch (NumberFormatException ignored) { return fallback; } }
}
