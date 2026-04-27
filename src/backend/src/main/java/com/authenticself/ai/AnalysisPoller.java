package com.authenticself.ai;

import com.authenticself.domain.Space;
import com.authenticself.repository.SpaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled sweeper for {@code spaces} rows in {@code PENDING_ANALYSIS}
 * (FR-14 / AC-22).
 * <p>
 * Disabled by default in the {@code test} profile via the
 * {@link ConditionalOnProperty} below — integration tests that want to
 * exercise the poller can enable it explicitly with
 * {@code app.ai.poller.enabled=true}. Bean is simply not created otherwise,
 * which removes any risk of racing the Python mock.
 *
 * <p>A single failing row must NOT stall the queue: the per-row call is
 * wrapped in a defensive try/catch and logged-and-moved-on.
 */
@Component
@ConditionalOnProperty(name = "app.ai.poller.enabled", havingValue = "true", matchIfMissing = false)
public class AnalysisPoller {

    private static final Logger log = LoggerFactory.getLogger(AnalysisPoller.class);

    private final SpaceRepository spaces;
    private final AIOrchestrator  orchestrator;

    public AnalysisPoller(SpaceRepository spaces, AIOrchestrator orchestrator) {
        this.spaces = spaces;
        this.orchestrator = orchestrator;
    }

    /**
     * Poll the queue.
     * <p>
     * Accepts both {@code app.ai.poller.fixed-delay-ms} (handoff spelling)
     * and {@code app.ai.poller.interval-ms} (spec spelling) — {@code ${a:${b:default}}}
     * is Spring's property-chain fallback.
     */
    @Scheduled(fixedDelayString = "${app.ai.poller.fixed-delay-ms:${app.ai.poller.interval-ms:15000}}")
    public void sweep() {
        List<Space> batch = spaces.findTop10ByStatusOrderByUploadedAtAsc(Space.Status.PENDING_ANALYSIS);
        if (batch.isEmpty()) return;

        log.debug("poller tick batchSize={}", batch.size());
        for (Space s : batch) {
            try {
                orchestrator.analyze(s.getRoomId());
            } catch (AIException ex) {
                // Expected failure mode — row will retry next tick for transport,
                // or stay FAILED for analyzer errors. Either way, don't stall.
                log.warn("poller row failed roomId={} code={}", s.getRoomId(), ex.code());
            } catch (RuntimeException ex) {
                log.error("poller row unexpected failure roomId={}", s.getRoomId(), ex);
            }
        }
    }
}
