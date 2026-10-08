package com.hoctap970.rag.controller;

import com.hoctap970.rag.dto.HealthResponse;
import com.hoctap970.rag.dto.AiReadinessResponse;
import com.hoctap970.rag.service.AiReadinessService;
import com.hoctap970.rag.service.GeminiModelProvider;
import com.hoctap970.rag.service.RagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final GeminiModelProvider modelProvider;
    private final RagService ragService;
    private final AiReadinessService readiness;

    public HealthController(GeminiModelProvider modelProvider, RagService ragService, AiReadinessService readiness) {
        this.modelProvider = modelProvider;
        this.ragService = ragService;
        this.readiness = readiness;
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

    @PostMapping("/health/ai")
    public ResponseEntity<AiReadinessResponse> checkAi() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(readiness.check());
    }
}
