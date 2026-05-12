/**
 * Recommendations API client (UC-01-recommendation FR-25).
 *
 * Wraps `GET /api/v1/spaces/{roomId}/recommendations` on the Spring
 * backend. Response shape mirrors the backend's
 * `RecommendationsApiResponse` (see api_contract.yaml) — which itself
 * wraps the Python `POST /recommend/furniture` response with two
 * Spring-only fields (`preferredStyle`, `cacheHit`).
 *
 * The module is deliberately separate from `client.ts` (the upload +
 * generic spaces client) so the recommendation surface can evolve
 * without churning the upload path's test surface.
 *
 * Runtime-shape narrowing is a lightweight assertion — no heavy
 * schema-validation library required per the spec.
 */

import { ApiError } from './client';
import type { PreferredStyle, Style } from '../types/style';

// ---------------------------------------------------------------------------
// Types — mirror the Spring-public contract (FR-19 + FR-25).
// ---------------------------------------------------------------------------

export type FurnitureType = 'desk' | 'bed' | 'chair' | 'lighting';

export type ScoreBreakdown = {
  sizeFit: number;
  styleMatch: number;
  colorHarmony: number;
  objectConflict: number;
};

export type RecommendationItem = {
  furnitureId: string;
  name: string;
  type: FurnitureType;
  price: number;
  imageUrl: string | null;
  /** Phase A — curated 3D model URL for AR placement. Null when no GLB is
   *  associated with this furniture row; the AR screen falls back to the
   *  per-type placeholder in that case. */
  modelUrl?: string | null;
  fitScore: number;
  scoreBreakdown: ScoreBreakdown;
  rationale: string;
};

export type RecommendationsByCategory = {
  desk: RecommendationItem[];
  bed: RecommendationItem[];
  chair: RecommendationItem[];
  lighting: RecommendationItem[];
};

export type RecommendationResponse = {
  roomId: string;
  status: 'OK';
  resolvedStyle: Style;
  preferredStyle: PreferredStyle;
  generatedAt: string;
  cacheHit: boolean;
  recommendations: RecommendationsByCategory;
  warning: 'NO_FIT_ANY_CATEGORY' | null;
  processingMs: number;
};

// ---------------------------------------------------------------------------
// Settings + auth stub — mirrors client.ts conventions.
// ---------------------------------------------------------------------------

type GetRecommendationsOpts = {
  baseUrl: string;
  userId: string;
  roomId: string;
  topNPerCategory?: number;
};

/**
 * Fetch ranked furniture recommendations for a room.
 *
 * Rejects with {@link ApiError} on any non-2xx response, carrying the
 * parsed `{errorCode, message, correlationId}` envelope when available.
 * Unexpected payload shapes (e.g. missing the four category keys)
 * throw a plain {@link Error} so the caller can distinguish transport
 * vs. contract issues.
 */
export async function getRecommendations(
  opts: GetRecommendationsOpts,
): Promise<RecommendationResponse> {
  const { baseUrl, userId, roomId, topNPerCategory } = opts;
  const qs =
    topNPerCategory != null ? `?topNPerCategory=${encodeURIComponent(topNPerCategory)}` : '';
  const url = `${baseUrl}/api/v1/spaces/${encodeURIComponent(roomId)}/recommendations${qs}`;
  const res = await fetch(url, {
    method: 'GET',
    headers: { 'X-User-Id': userId, Accept: 'application/json' },
  });
  const text = await res.text();
  let parsed: unknown = null;
  try {
    parsed = text ? JSON.parse(text) : null;
  } catch {
    parsed = null;
  }
  if (!res.ok) {
    throw new ApiError(res.status, parsed as any);
  }
  return assertRecommendationResponse(parsed);
}

// ---------------------------------------------------------------------------
// Runtime shape narrowing — lightweight; no ajv / zod dependency.
// ---------------------------------------------------------------------------

export function assertRecommendationResponse(v: unknown): RecommendationResponse {
  if (!v || typeof v !== 'object') {
    throw new Error('RecommendationResponse: body is not an object');
  }
  const obj = v as Record<string, unknown>;
  if (typeof obj.roomId !== 'string') {
    throw new Error('RecommendationResponse: roomId missing');
  }
  if (obj.status !== 'OK') {
    throw new Error(`RecommendationResponse: unexpected status=${obj.status as string}`);
  }
  const recs = obj.recommendations as Record<string, unknown> | undefined;
  if (!recs || typeof recs !== 'object') {
    throw new Error('RecommendationResponse: recommendations missing');
  }
  for (const cat of ['desk', 'bed', 'chair', 'lighting'] as const) {
    if (!Array.isArray(recs[cat])) {
      throw new Error(`RecommendationResponse: recommendations.${cat} not an array`);
    }
  }
  return obj as unknown as RecommendationResponse;
}

// ---------------------------------------------------------------------------
// Analytics stub — FR-23 "위시리스트에 추가" button (AC-48).
// Kept on the API module so the real wishlist write (UC-02) can
// swap this for a real `POST /api/v1/wishlist` call with no screen-side
// churn.
// ---------------------------------------------------------------------------

export type AnalyticsEvent =
  | {
      type: 'wishlist_add_clicked';
      payload: { roomId: string; furnitureId: string };
    };

type AnalyticsSink = (ev: AnalyticsEvent) => void;

let analyticsSink: AnalyticsSink = () => {
  // default — emit to console for dev inspection; tests override this.
  // eslint-disable-next-line no-console
  // console.debug intentionally no-op in production.
};

/** Swap the analytics sink (tests + real analytics infra). */
export function setAnalyticsSink(sink: AnalyticsSink): void {
  analyticsSink = sink;
}

/** Emit the `wishlist_add_clicked` event (stub per AC-48). */
export function emitWishlistAddClicked(roomId: string, furnitureId: string): void {
  analyticsSink({ type: 'wishlist_add_clicked', payload: { roomId, furnitureId } });
}
