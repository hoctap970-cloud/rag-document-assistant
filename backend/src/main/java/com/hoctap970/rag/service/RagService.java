package com.hoctap970.rag.service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hoctap970.rag.config.RagProperties;
import com.hoctap970.rag.domain.IndexedChunk;
import com.hoctap970.rag.domain.IndexedDocument;
import com.hoctap970.rag.domain.SectionContent;
import com.hoctap970.rag.dto.ChatResponse;
import com.hoctap970.rag.dto.DocumentContent;
import com.hoctap970.rag.dto.DocumentSummary;
import com.hoctap970.rag.dto.SourceReference;
import com.hoctap970.rag.dto.UploadResponse;
import com.hoctap970.rag.exception.AiConfigurationException;
import com.hoctap970.rag.exception.AiServiceException;
import com.hoctap970.rag.exception.BadRequestException;
import com.hoctap970.rag.exception.DocumentProcessingException;
import com.hoctap970.rag.exception.NotFoundException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RagService {

    private static final int EMBEDDING_BATCH_SIZE = 50;
    private static final int EXCERPT_LENGTH = 280;
    private static final String NO_RESULT_MESSAGE =
            "Tôi chưa tìm thấy thông tin đủ liên quan trong các tài liệu đã tải lên để trả lời câu hỏi này.";

    private final DocumentFileValidator fileValidator;
    private final DocumentParserService parserService;
    private final SectionExtractor sectionExtractor;
    private final GeminiModelProvider modelProvider;
    private final RagProperties properties;
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
    private final Map<UUID, IndexedDocument> documents = new ConcurrentHashMap<>();
    private final Object storeLock = new Object();

    public RagService(
            DocumentFileValidator fileValidator,
            DocumentParserService parserService,
            SectionExtractor sectionExtractor,
            GeminiModelProvider modelProvider,
            RagProperties properties
    ) {
        this.fileValidator = fileValidator;
        this.parserService = parserService;
        this.sectionExtractor = sectionExtractor;
        this.modelProvider = modelProvider;
        this.properties = properties;
    }

    public UploadResponse upload(MultipartFile file) {
        String fileName = fileValidator.validateAndCleanFileName(file);
        String text = parserService.parse(file);
        List<SectionContent> sections = sectionExtractor.extract(text);

        if (sections.isEmpty()) {
            throw new DocumentProcessingException("Không thể tách nội dung tài liệu thành các đoạn văn bản.", null);
        }

        UUID documentId = UUID.randomUUID();
        List<TextSegment> segments = createSegments(documentId, fileName, sections);
        if (segments.size() > properties.maxChunksPerDocument()) {
            throw new BadRequestException(
                    "Tài liệu tạo ra quá nhiều đoạn (%d). Giới hạn hiện tại là %d đoạn."
                            .formatted(segments.size(), properties.maxChunksPerDocument())
            );
        }

        List<Embedding> embeddings = embedDocument(segments);
        byte[] originalBytes = readOriginalBytes(file);
        Instant uploadedAt = Instant.now();
        String contentType = file.getContentType() == null
                ? "application/octet-stream"
                : file.getContentType();

        addDocumentToStore(
                documentId,
                fileName,
                contentType,
                file.getSize(),
                text.length(),
                sections.size(),
                uploadedAt,
                embeddings,
                segments,
                originalBytes
        );

        return new UploadResponse(
                documentId,
                fileName,
                contentType,
                file.getSize(),
                text.length(),
                sections.size(),
                segments.size(),
                uploadedAt,
                "Đã đọc, chia đoạn và tạo vector cho tài liệu thành công"
        );
    }

    public ChatResponse ask(String rawQuestion) {
        String question = rawQuestion == null ? "" : rawQuestion.trim();
        if (question.isBlank()) {
            throw new BadRequestException("Câu hỏi không được để trống");
        }
        if (documents.isEmpty()) {
            throw new BadRequestException("Hãy tải lên ít nhất một tài liệu trước khi đặt câu hỏi");
        }

        List<EmbeddingMatch<TextSegment>> matches = search(question);
        if (matches.isEmpty()) {
            return new ChatResponse(question, NO_RESULT_MESSAGE, List.of());
        }

        List<SourceReference> sources = toSources(matches);
        String prompt = buildPrompt(question, matches);

        try {
            String answer = modelProvider.chatModel().chat(prompt);
            return new ChatResponse(question, answer, sources);
        } catch (AiConfigurationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiServiceException(
                    "Gemini chưa thể trả lời lúc này. Hãy kiểm tra API key, hạn mức rồi thử lại.",
                    exception
            );
        }
    }

    public List<DocumentSummary> listDocuments() {
        return documents.values().stream()
                .sorted(Comparator.comparing(IndexedDocument::uploadedAt).reversed())
                .map(this::toSummary)
                .toList();
    }

    public IndexedDocument getDocument(UUID documentId) {
        IndexedDocument document = documents.get(documentId);
        if (document == null) {
            throw new NotFoundException("Không tìm thấy tài liệu có mã " + documentId);
        }
        return document;
    }

    public DocumentContent getDocumentContent(UUID documentId) {
        IndexedDocument document = getDocument(documentId);
        return new DocumentContent(document.id(), document.fileName(), document.chunks());
    }

    public void deleteDocument(UUID documentId) {
        synchronized (storeLock) {
            IndexedDocument document = documents.get(documentId);
            if (document == null) {
                throw new NotFoundException("Không tìm thấy tài liệu có mã " + documentId);
            }
            embeddingStore.removeAll(document.embeddingIds());
            documents.remove(documentId);
        }
    }

    public void clearDocuments() {
        synchronized (storeLock) {
            List<String> ids = documents.values().stream()
                    .flatMap(document -> document.embeddingIds().stream())
                    .toList();
            if (!ids.isEmpty()) {
                embeddingStore.removeAll(ids);
            }
            documents.clear();
        }
    }

    public int documentCount() {
        return documents.size();
    }

    public int chunkCount() {
        return documents.values().stream().mapToInt(IndexedDocument::chunkCount).sum();
    }

    private List<TextSegment> createSegments(
            UUID documentId,
            String fileName,
            List<SectionContent> sections
    ) {
        DocumentSplitter splitter = DocumentSplitters.recursive(
                properties.chunkSize(),
                properties.chunkOverlap()
        );
        List<TextSegment> segments = new ArrayList<>();
        int chunkIndex = 1;

        for (SectionContent section : sections) {
            List<TextSegment> sectionChunks = splitter.split(Document.from(section.text()));
            for (TextSegment chunk : sectionChunks) {
                Map<String, Object> attributes = new LinkedHashMap<>();
                attributes.put("document_id", documentId.toString());
                attributes.put("file_name", fileName);
                attributes.put("section", section.title());
                attributes.put("chunk_index", chunkIndex++);
                attributes.put("title", fileName);

                segments.add(TextSegment.from(chunk.text(), Metadata.from(attributes)));
            }
        }
        return segments;
    }

    private List<Embedding> embedDocument(List<TextSegment> segments) {
        try {
            EmbeddingModel model = modelProvider.documentEmbeddingModel();
            List<Embedding> embeddings = new ArrayList<>(segments.size());
            for (int start = 0; start < segments.size(); start += EMBEDDING_BATCH_SIZE) {
                int end = Math.min(start + EMBEDDING_BATCH_SIZE, segments.size());
                embeddings.addAll(model.embedAll(segments.subList(start, end)).content());
            }
            return embeddings;
        } catch (AiConfigurationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiServiceException(
                    "Không thể tạo embedding. Hãy kiểm tra API key, kết nối mạng và hạn mức Gemini.",
                    exception
            );
        }
    }

    private IndexedDocument addDocumentToStore(
            UUID documentId,
            String fileName,
            String contentType,
            long size,
            int characterCount,
            int sectionCount,
            Instant uploadedAt,
            List<Embedding> embeddings,
            List<TextSegment> segments,
            byte[] originalBytes
    ) {
        List<String> ids = new ArrayList<>(segments.size());
        synchronized (storeLock) {
            try {
                for (int index = 0; index < segments.size(); index++) {
                    ids.add(embeddingStore.add(embeddings.get(index), segments.get(index)));
                }
                IndexedDocument document = new IndexedDocument(
                        documentId,
                        fileName,
                        contentType,
                        size,
                        characterCount,
                        sectionCount,
                        segments.size(),
                        uploadedAt,
                        List.copyOf(ids),
                        originalBytes,
                        segments.stream()
                                .map(segment -> new IndexedChunk(
                                        segment.metadata().getInteger("chunk_index"),
                                        segment.metadata().getString("section"),
                                        segment.text()
                                ))
                                .toList()
                );
                documents.put(documentId, document);
                return document;
            } catch (Exception exception) {
                if (!ids.isEmpty()) {
                    embeddingStore.removeAll(ids);
                }
                throw exception;
            }
        }
    }

    private List<EmbeddingMatch<TextSegment>> search(String question) {
        try {
            Embedding queryEmbedding = modelProvider.queryEmbeddingModel().embed(question).content();
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(properties.maxResults())
                    .minScore(properties.minScore())
                    .build();

            synchronized (storeLock) {
                EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
                return List.copyOf(result.matches());
            }
        } catch (AiConfigurationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiServiceException(
                    "Không thể tìm kiếm trong tài liệu. Hãy kiểm tra kết nối Gemini rồi thử lại.",
                    exception
            );
        }
    }

    private String buildPrompt(String question, List<EmbeddingMatch<TextSegment>> matches) {
        StringBuilder context = new StringBuilder();
        for (int index = 0; index < matches.size(); index++) {
            TextSegment segment = matches.get(index).embedded();
            context.append("[Nguồn ").append(index + 1).append("]\n")
                    .append("Tệp: ").append(segment.metadata().getString("file_name")).append('\n')
                    .append("Mục: ").append(segment.metadata().getString("section")).append('\n')
                    .append("Nội dung: ").append(segment.text()).append("\n\n");
        }

        return """
                Bạn là trợ lý hỏi đáp tài liệu. Hãy trả lời bằng tiếng Việt rõ ràng, chính xác.

                Quy tắc bắt buộc:
                - Chỉ dùng thông tin trong phần NGỮ CẢNH bên dưới.
                - Không tự bổ sung kiến thức bên ngoài hoặc bịa thông tin.
                - Nếu ngữ cảnh không đủ, hãy nói rõ rằng tài liệu chưa cung cấp đủ thông tin.
                - Khi dùng thông tin, ghi nguồn theo dạng [Nguồn 1], [Nguồn 2].
                - Trả lời trực tiếp vào câu hỏi, ưu tiên ngắn gọn và dễ hiểu.

                CÂU HỎI:
                %s

                NGỮ CẢNH:
                %s
                """.formatted(question, context);
    }

    private List<SourceReference> toSources(List<EmbeddingMatch<TextSegment>> matches) {
        return matches.stream()
                .map(match -> {
                    TextSegment segment = match.embedded();
                    return new SourceReference(
                            UUID.fromString(segment.metadata().getString("document_id")),
                            segment.metadata().getString("file_name"),
                            segment.metadata().getString("section"),
                            segment.metadata().getInteger("chunk_index"),
                            Math.round(match.score() * 1000.0) / 1000.0,
                            abbreviate(segment.text().replaceAll("\\s+", " ").trim(), EXCERPT_LENGTH)
                    );
                })
                .toList();
    }

    private DocumentSummary toSummary(IndexedDocument document) {
        return new DocumentSummary(
                document.id(),
                document.fileName(),
                document.contentType(),
                document.size(),
                document.characterCount(),
                document.sectionCount(),
                document.chunkCount(),
                document.uploadedAt()
        );
    }

    private String abbreviate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }

    private byte[] readOriginalBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new DocumentProcessingException("Không thể lưu tệp gốc để xem nguồn.", exception);
        }
    }
}
