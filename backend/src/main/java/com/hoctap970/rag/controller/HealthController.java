package com.hoctap970.rag.controller;

import com.hoctap970.rag.dto.HealthResponse;
import com.hoctap970.rag.service.GeminiModelProvider;
import com.hoctap970.rag.service.RagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final GeminiModelProvider modelProvider;
    private final RagService ragService;

    public HealthController(GeminiModelProvider modelProvider, RagService ragService) {
        this.modelProvider = modelProvider;
        this.ragService = ragService;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse(
                "UP",
                "rag-document-assistant",
                modelProvider.isConfigured(),
                ragService.documentCount(),
                ragService.chunkCount()
        );
    }
}
