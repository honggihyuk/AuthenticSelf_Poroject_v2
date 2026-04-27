package com.authenticself.ai;

import com.authenticself.ai.dto.SpaceAnalysisResultDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal trigger for the AI pipeline (FR-13).
 * <p>
 * The {@code /internal/v1/*} prefix is an explicit signal that this endpoint
 * is NOT intended for public / mobile traffic. Production ingress must block
 * it; this task does not add authn/z (same stance as UC-01-photo-upload).
 *
 * <p>No CORS, no header requirements, no rate-limiting here — the whole
 * point is an ops/test knob.
 */
@RestController
@RequestMapping("/internal/v1/spaces")
public class AIOrchestratorController {

    private final AIOrchestrator orchestrator;

    public AIOrchestratorController(AIOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/{roomId}/analyze")
    public ResponseEntity<SpaceAnalysisResultDTO> analyze(@PathVariable String roomId) {
        SpaceAnalysisResultDTO result = orchestrator.analyze(roomId);
        return ResponseEntity.ok(result);
    }
}
