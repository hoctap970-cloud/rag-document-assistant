package com.hoctap970.rag.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.hoctap970.rag.domain.IndexedDocument;
import com.hoctap970.rag.dto.DocumentContent;
import com.hoctap970.rag.dto.DocumentSummary;
import com.hoctap970.rag.dto.MessageResponse;
import com.hoctap970.rag.dto.UploadResponse;
import com.hoctap970.rag.service.RagService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final RagService ragService;

    public DocumentController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse upload(@RequestParam("file") MultipartFile file) {
        return ragService.upload(file);
    }

    @GetMapping
    public List<DocumentSummary> list() {
        return ragService.listDocuments();
    }

    @GetMapping("/{documentId}/content")
    public ResponseEntity<DocumentContent> content(@PathVariable UUID documentId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ragService.getDocumentContent(documentId));
    }

    @GetMapping("/{documentId}/original")
    public ResponseEntity<byte[]> original(@PathVariable UUID documentId) {
        IndexedDocument document = ragService.getDocument(documentId);
        boolean pdf = document.fileName().toLowerCase(Locale.ROOT).endsWith(".pdf");
        ContentDisposition disposition = (pdf ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(document.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(pdf ? MediaType.APPLICATION_PDF : MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(document.originalBytes().length)
                .body(document.originalBytes());
    }

    @DeleteMapping("/{documentId}")
    public MessageResponse delete(@PathVariable UUID documentId) {
        ragService.deleteDocument(documentId);
        return new MessageResponse("Đã xóa tài liệu và các vector liên quan khỏi RAM");
    }

    @DeleteMapping
    public MessageResponse clear() {
        ragService.clearDocuments();
        return new MessageResponse("Đã xóa toàn bộ tài liệu và vector khỏi RAM");
    }
}
