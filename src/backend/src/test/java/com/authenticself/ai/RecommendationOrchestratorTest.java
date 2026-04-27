package com.authenticself.ai;

import com.authenticself.ai.dto.RecommendationRequest;
import com.authenticself.ai.dto.RecommendationResponse;
import com.authenticself.domain.Furniture;
import com.authenticself.domain.Space;
import com.authenticself.repository.FurnitureRepository;
import com.authenticself.repository.SpaceRepository;
import com.authenticself.space.PreferredStyle;
import com.authenticself.space.SpaceErrorCode;
import com.authenticself.space.SpaceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RecommendationOrchestrator} (AC-28..AC-30,
 * AC-33..AC-38).
 *
 * <p>No Spring context — all collaborators mocked directly. The
 * {@code aiExecutor} uses a real single-threaded {@link Executors}
 * because the orchestrator dispatches via {@code CompletableFuture}
 * and joins on the caller thread.
 */
class RecommendationOrchestratorTest {

    SpaceRepository        spaces;
    FurnitureRepository    furniture;
    RecommendationClient   client;
    Executor               executor;
    RecommendationOrchestrator orch;

    @BeforeEach
    void setup() {
        spaces = mock(SpaceRepository.class);
        furniture = mock(FurnitureRepository.class);
        client = mock(RecommendationClient.class);
        executor = Executors.newSingleThreadExecutor();
        orch = new RecommendationOrchestrator(
                spaces, furniture, client, executor,
                600, 1024);
    }

    private Space analyzedRoom(String roomId, String userId, PreferredStyle pref) {
        Space s = new Space();
        s.setRoomId(roomId);
        s.setUserId(userId);
        s.setStatus(Space.Status.ANALYZED);
        s.setDimensions("4.0x4.0x2.4m");
        s.setMainColor("#E8D9B0");
        s.setStyle("MODERN");
        s.setPreferredStyle(pref);
        s.setPhotoUrl("file:///tmp/r1.jpg");
        s.setContentType("image/jpeg");
        s.setFileSizeBytes(1234L);
        return s;
    }

    private Furniture desk(String id) {
        Furniture f = new Furniture();
        f.setFurnitureId(id);
        f.setType("desk");
        f.setName("Mock Desk");
        f.setStyle("MODERN");
        f.setSize("120x60x74");
        f.setPrice(189000);
        f.setImageUrl("https://cdn.example.com/" + id + ".jpg");
        f.setColorHex("#E8D9B0");
        f.setWidthCm(120);
        f.setLengthCm(60);
        f.setHeightCm(74);
        f.setStyleTags("MODERN");
        return f;
    }

    private RecommendationResponse emptyOkResponse() {
        return new RecommendationResponse(
                "r1", "OK", "MODERN", "2026-04-18T09:14:22Z",
                new RecommendationResponse.Recommendations(
                        List.of(), List.of(), List.of(), List.of()
                ),
                null, 18);
    }

    // -----------------------------------------------------------------
    // AC-28 — happy path, cacheHit=false, preferredStyle echoed
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-28: happy path → cacheHit=false, Python called once")
    void ac28_happyPath() {
        when(spaces.findById("r1"))
                .thenReturn(Optional.of(analyzedRoom("r1", "u1", PreferredStyle.MODERN)));
        when(furniture.findAll()).thenReturn(List.of(desk("f_desk_001")));
        when(client.callRecommend(any())).thenReturn(emptyOkResponse());

        var out = orch.recommend("r1", "u1", 3);

        assertThat(out.cacheHit()).isFalse();
        assertThat(out.preferredStyle()).isEqualTo("MODERN");
        assertThat(out.response().status()).isEqualTo("OK");
        verify(client, times(1)).callRecommend(any());
    }

    // -----------------------------------------------------------------
    // AC-29 — second identical call hits the cache
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-29: second identical call returns cacheHit=true and no Python call")
    void ac29_cacheHit() {
        when(spaces.findById("r1"))
                .thenReturn(Optional.of(analyzedRoom("r1", "u1", PreferredStyle.MODERN)));
        when(furniture.findAll()).thenReturn(List.of(desk("f_desk_001")));
        when(client.callRecommend(any())).thenReturn(emptyOkResponse());

        var first = orch.recommend("r1", "u1", 3);
        var second = orch.recommend("r1", "u1", 3);

        assertThat(first.cacheHit()).isFalse();
        assertThat(second.cacheHit()).isTrue();
        // generatedAt preserved (same cached payload).
        assertThat(second.response().generatedAt()).isEqualTo(first.response().generatedAt());
        verify(client, times(1)).callRecommend(any());
    }

    // -----------------------------------------------------------------
    // AC-30 — invalidation on preferred-style change
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-30: invalidate(roomId) forces a second Python call")
    void ac30_invalidate() {
        when(spaces.findById("r1"))
                .thenReturn(Optional.of(analyzedRoom("r1", "u1", PreferredStyle.MODERN)));
        when(furniture.findAll()).thenReturn(List.of(desk("f_desk_001")));
        when(client.callRecommend(any())).thenReturn(emptyOkResponse());

        orch.recommend("r1", "u1", 3);

        // Now simulate preferred-style changed to SIMPLE.
        when(spaces.findById("r1"))
                .thenReturn(Optional.of(analyzedRoom("r1", "u1", PreferredStyle.SIMPLE)));
        orch.invalidate("r1");

        var third = orch.recommend("r1", "u1", 3);

        assertThat(third.cacheHit()).isFalse();
        assertThat(third.preferredStyle()).isEqualTo("SIMPLE");
        verify(client, times(2)).callRecommend(any());
    }

    // -----------------------------------------------------------------
    // AC-31 — status != ANALYZED → 409 ANALYSIS_NOT_READY, no Python call
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-31: PENDING_ANALYSIS → SpaceException(ANALYSIS_NOT_READY), no Python call")
    void ac31_analysisNotReady() {
        Space pending = analyzedRoom("r1", "u1", PreferredStyle.MODERN);
        pending.setStatus(Space.Status.PENDING_ANALYSIS);
        when(spaces.findById("r1")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> orch.recommend("r1", "u1", 3))
                .isInstanceOf(SpaceException.class)
                .extracting("code").isEqualTo(SpaceErrorCode.ANALYSIS_NOT_READY);

        verifyNoInteractions(client);
    }

    // -----------------------------------------------------------------
    // AC-32 — preferred_style null → 409 PREFERRED_STYLE_NOT_SET
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-32: preferred_style NULL → PREFERRED_STYLE_NOT_SET")
    void ac32_preferredStyleNotSet() {
        Space unset = analyzedRoom("r1", "u1", null);
        when(spaces.findById("r1")).thenReturn(Optional.of(unset));

        assertThatThrownBy(() -> orch.recommend("r1", "u1", 3))
                .isInstanceOf(SpaceException.class)
                .extracting("code").isEqualTo(SpaceErrorCode.PREFERRED_STYLE_NOT_SET);

        verifyNoInteractions(client);
    }

    // -----------------------------------------------------------------
    // AC-33 — access denied
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-33: userId mismatch → SPACE_ACCESS_DENIED")
    void ac33_forbidden() {
        when(spaces.findById("r1"))
                .thenReturn(Optional.of(analyzedRoom("r1", "u1", PreferredStyle.MODERN)));

        assertThatThrownBy(() -> orch.recommend("r1", "intruder", 3))
                .isInstanceOf(SpaceException.class)
                .extracting("code").isEqualTo(SpaceErrorCode.SPACE_ACCESS_DENIED);

        verifyNoInteractions(client);
    }

    // -----------------------------------------------------------------
    // AC-34 — missing row → SPACE_NOT_FOUND
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-34: unknown roomId → SPACE_NOT_FOUND")
    void ac34_notFound() {
        when(spaces.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orch.recommend("ghost", "u1", 3))
                .isInstanceOf(SpaceException.class)
                .extracting("code").isEqualTo(SpaceErrorCode.SPACE_NOT_FOUND);

        verifyNoInteractions(client);
    }

    // -----------------------------------------------------------------
    // AC-36 — transport failure is rethrown and NOT cached
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-36: transport failure re-thrown, no cache write")
    void ac36_transportFailure() {
        when(spaces.findById("r1"))
                .thenReturn(Optional.of(analyzedRoom("r1", "u1", PreferredStyle.MODERN)));
        when(furniture.findAll()).thenReturn(List.of(desk("f_desk_001")));
        when(client.callRecommend(any()))
                .thenThrow(new AIException(AIErrorCode.AI_SERVICE_UNAVAILABLE, "boom"));

        assertThatThrownBy(() -> orch.recommend("r1", "u1", 3))
                .isInstanceOf(AIException.class)
                .hasFieldOrPropertyWithValue("code", AIErrorCode.AI_SERVICE_UNAVAILABLE);

        // Python returns OK on a SECOND attempt — verify the failed call
        // was not cached (client is called a second time).
        // Use doReturn to re-stub without firing the prior thenThrow at
        // stubbing time (standard Mockito re-stub pitfall).
        doReturn(emptyOkResponse()).when(client).callRecommend(any());
        var retry = orch.recommend("r1", "u1", 3);

        assertThat(retry.cacheHit()).isFalse();
        verify(client, times(2)).callRecommend(any());
    }

    // -----------------------------------------------------------------
    // AC-38 — detectedObjects always []
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-38: Spring sends detectedObjects=[]")
    void ac38_emptyDetectedObjects() {
        when(spaces.findById("r1"))
                .thenReturn(Optional.of(analyzedRoom("r1", "u1", PreferredStyle.MODERN)));
        when(furniture.findAll()).thenReturn(List.of(desk("f_desk_001")));
        when(client.callRecommend(any())).thenReturn(emptyOkResponse());

        orch.recommend("r1", "u1", 3);

        ArgumentCaptor<RecommendationRequest> captor =
                ArgumentCaptor.forClass(RecommendationRequest.class);
        verify(client).callRecommend(captor.capture());
        var req = captor.getValue();

        assertThat(req.space().detectedObjects()).isNotNull();
        assertThat(req.space().detectedObjects()).isEmpty();
        assertThat(req.topNPerCategory()).isEqualTo(3);
        assertThat(req.preferredStyle()).isEqualTo("MODERN");
    }

    // -----------------------------------------------------------------
    // Dimension parser (defensive)
    // -----------------------------------------------------------------
    @Test
    @DisplayName("parseDimensions accepts '4.0x4.0x2.4m' and variants")
    void parseDimensions_ok() {
        assertThat(RecommendationOrchestrator.parseDimensions("4.0x4.0x2.4m"))
                .containsExactly(4.0, 4.0, 2.4);
        assertThat(RecommendationOrchestrator.parseDimensions("3.6x4.2x2.4"))
                .containsExactly(3.6, 4.2, 2.4);
    }

    @Test
    @DisplayName("parseDimensions rejects malformed strings")
    void parseDimensions_bad() {
        assertThat(RecommendationOrchestrator.parseDimensions(null)).isNull();
        assertThat(RecommendationOrchestrator.parseDimensions("")).isNull();
        assertThat(RecommendationOrchestrator.parseDimensions("garbage")).isNull();
        assertThat(RecommendationOrchestrator.parseDimensions("4x4")).isNull();
        assertThat(RecommendationOrchestrator.parseDimensions("0x0x0m")).isNull();
    }
}
