package com.authenticself.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight in-process cache for the transient {@code styleConfidence}
 * value (Task-4 FR-14).
 * <p>
 * Only {@code spaces.style} (the argmax) is persisted in V3; the confidence
 * is useful for the UI's "AI detected X (72%)" badge but is not worth a
 * schema column of its own at this point. This cache is:
 * <ul>
 *   <li>per-process (no Redis) — cache miss simply returns {@code null}
 *       and the UI drops the percentage from the badge (FR-14 allows it);</li>
 *   <li>TTL 15 minutes — entries are evicted lazily on access.</li>
 * </ul>
 *
 * <p>Static access is intentional: {@link AIOrchestrator} writes and
 * {@link com.authenticself.space.SpaceController} reads, and neither
 * shares a bean lifecycle with the other. A global map is simpler than
 * threading an extra dependency through both call paths.
 */
public final class StyleConfidenceCache {

    /** TTL applied to every cached entry. */
    private static final long TTL_MS = 15L * 60_000L;  // 15 minutes

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    // Clock source — indirected so tests can rebind it if needed.
    private static final AtomicLong MOCK_NOW = new AtomicLong(-1L);

    private StyleConfidenceCache() { /* utility class */ }

    public static void put(String roomId, double confidence) {
        CACHE.put(roomId, new Entry(confidence, now() + TTL_MS));
    }

    /** Returns the cached confidence or {@code null} on miss / expiry. */
    public static Double get(String roomId) {
        Entry e = CACHE.get(roomId);
        if (e == null) return null;
        if (e.expiresAt() < now()) {
            CACHE.remove(roomId, e);
            return null;
        }
        return e.confidence();
    }

    /** Test-only — clear all entries. */
    public static void clear() {
        CACHE.clear();
    }

    private static long now() {
        long mocked = MOCK_NOW.get();
        return mocked >= 0 ? mocked : System.currentTimeMillis();
    }

    /** Test-only — pin the clock to a specific wall-clock ms (use -1 to unpin). */
    static void setMockNow(long millis) {
        MOCK_NOW.set(millis);
    }

    private record Entry(double confidence, long expiresAt) { }
}
