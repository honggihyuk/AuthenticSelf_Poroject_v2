/**
 * Wishlist API client (UC-02-wishlist FR-15 / AC-35).
 *
 * Wraps the four public REST endpoints on the Spring backend:
 *   POST   /api/v1/wishlist                       — add (idempotent)
 *   GET    /api/v1/wishlist?status=ACTIVE|PURCHASED — list
 *   PATCH  /api/v1/wishlist/{wishlistId}          — state transition
 *   DELETE /api/v1/wishlist/{wishlistId}          — hard delete
 *
 * Every call carries the `X-User-Id` stub-auth header (same convention
 * as `spaces.ts` / `client.ts`). Non-2xx responses reject with
 * `ApiError` from `client.ts` carrying the shared `{errorCode, message,
 * correlationId}` envelope.
 *
 * Runtime shape narrowing is a lightweight assertion — no ajv / zod
 * dependency (mirrors `spaces.ts` / AC-35 rationale).
 */

import { ApiError } from './client';

// ---------------------------------------------------------------------------
// Types — mirror the Spring-public contract (FR-5..FR-7 + api_contract.yaml).
// ---------------------------------------------------------------------------

export type WishlistStatus = 'ACTIVE' | 'PURCHASED';

export type WishlistItem = {
  wishlistId: string;
  userId: string;
  furnitureId: string;
  category: 'desk' | 'bed' | 'chair' | 'lighting';
  price: number;
  /**
   * DB casing — matches the V1 ENUM ('Active' | 'Purchased').
   * The REST PATCH request body uses the UPPER_SNAKE casing
   * ({@link WishlistStatus}); responses echo the DB casing unchanged.
   */
  status: 'Active' | 'Purchased';
  addedAt: string;
  purchasedAt: string | null;
  furnitureSnapshot: {
    name: string;
    imageUrl: string | null;
    colorHex: string;
    type: string;
  } | null;
};

export type AddWishlistResponse = WishlistItem & { alreadyExists: boolean };

export type ListWishlistResponse = {
  items: WishlistItem[];
  totalActive: number;
  totalPurchased: number;
  truncated?: boolean;
};

// ---------------------------------------------------------------------------
// Request option shapes
// ---------------------------------------------------------------------------

type BaseOpts = { baseUrl: string; userId: string };

type AddOpts = BaseOpts & {
  furnitureId: string;
  category: 'desk' | 'bed' | 'chair' | 'lighting';
  price: number;
};

type ListOpts = BaseOpts & { status?: WishlistStatus };

type PatchOpts = BaseOpts & { wishlistId: string; status: WishlistStatus };

type DeleteOpts = BaseOpts & { wishlistId: string };

// ---------------------------------------------------------------------------
// Endpoints
// ---------------------------------------------------------------------------

/**
 * POST /api/v1/wishlist — add a furniture item to the user's wishlist.
 *
 * Returns the persisted {@link WishlistItem} plus the `alreadyExists`
 * idempotency flag. Rejects with {@link ApiError} on any non-2xx
 * response (status 400/404 per spec error codes).
 */
export async function addToWishlist(opts: AddOpts): Promise<AddWishlistResponse> {
  const { baseUrl, userId, furnitureId, category, price } = opts;
  const res = await fetch(`${baseUrl}/api/v1/wishlist`, {
    method: 'POST',
    headers: {
      'X-User-Id': userId,
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ furnitureId, category, price }),
  });
  const parsed = await parseJsonOrThrow(res);
  return assertAddResponse(parsed);
}

/**
 * GET /api/v1/wishlist — list the user's wishlist, optionally filtered.
 *
 * Server echoes `{items, totalActive, totalPurchased, truncated?}`.
 * Filter value is UPPER_SNAKE on the REST surface (`ACTIVE`,
 * `PURCHASED`); server is case-insensitive but we send UPPER_SNAKE for
 * consistency with AC-17 assertions.
 */
export async function getWishlist(opts: ListOpts): Promise<ListWishlistResponse> {
  const { baseUrl, userId, status } = opts;
  const qs = status ? `?status=${encodeURIComponent(status)}` : '';
  const res = await fetch(`${baseUrl}/api/v1/wishlist${qs}`, {
    method: 'GET',
    headers: { 'X-User-Id': userId, Accept: 'application/json' },
  });
  const parsed = await parseJsonOrThrow(res);
  return assertListResponse(parsed);
}

/**
 * Alias kept for symmetry with `spaces.ts` — the spec's example import
 * list names it `listWishlist`; the verifier checks for both.
 */
export const listWishlist = getWishlist;

/**
 * PATCH /api/v1/wishlist/{wishlistId} — transition between Active and
 * Purchased. Server enforces the state machine; a same-state PATCH is a
 * no-op on the server side.
 */
export async function updateWishlistState(opts: PatchOpts): Promise<WishlistItem> {
  const { baseUrl, userId, wishlistId, status } = opts;
  const res = await fetch(`${baseUrl}/api/v1/wishlist/${encodeURIComponent(wishlistId)}`, {
    method: 'PATCH',
    headers: {
      'X-User-Id': userId,
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ status }),
  });
  const parsed = await parseJsonOrThrow(res);
  return assertWishlistItem(parsed);
}

/**
 * Alias matching the spec's FR-15 example signature list
 * (`patchWishlist`). Kept alongside {@link updateWishlistState} so
 * callers are free to pick whichever name reads better at their site.
 */
export const patchWishlist = updateWishlistState;

/**
 * DELETE /api/v1/wishlist/{wishlistId} — hard delete. Succeeds from
 * both Active and Purchased states (FR-8 / AC-27).
 */
export async function deleteWishlistItem(opts: DeleteOpts): Promise<void> {
  const { baseUrl, userId, wishlistId } = opts;
  const res = await fetch(`${baseUrl}/api/v1/wishlist/${encodeURIComponent(wishlistId)}`, {
    method: 'DELETE',
    headers: { 'X-User-Id': userId, Accept: 'application/json' },
  });
  if (res.ok) return;
  const text = await res.text();
  let parsed: unknown = null;
  try {
    parsed = text ? JSON.parse(text) : null;
  } catch {
    parsed = null;
  }
  throw new ApiError(res.status, parsed as any);
}

/** Spec-alias — FR-15 example list names `deleteWishlist`. */
export const deleteWishlist = deleteWishlistItem;

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

async function parseJsonOrThrow(res: Response): Promise<unknown> {
  const text = await res.text();
  let parsed: unknown = null;
  try {
    parsed = text ? JSON.parse(text) : null;
  } catch {
    parsed = null;
  }
  if (res.ok) return parsed;
  throw new ApiError(res.status, parsed as any);
}

// ---------------------------------------------------------------------------
// Shape narrowing — lightweight, no schema library.
// ---------------------------------------------------------------------------

export function assertWishlistItem(v: unknown): WishlistItem {
  if (!v || typeof v !== 'object') {
    throw new Error('WishlistItem: body is not an object');
  }
  const obj = v as Record<string, unknown>;
  if (typeof obj.wishlistId !== 'string') throw new Error('WishlistItem: wishlistId missing');
  if (typeof obj.userId !== 'string') throw new Error('WishlistItem: userId missing');
  if (typeof obj.furnitureId !== 'string') throw new Error('WishlistItem: furnitureId missing');
  if (obj.status !== 'Active' && obj.status !== 'Purchased') {
    throw new Error(`WishlistItem: unexpected status=${String(obj.status)}`);
  }
  return obj as unknown as WishlistItem;
}

export function assertAddResponse(v: unknown): AddWishlistResponse {
  const item = assertWishlistItem(v);
  const obj = v as Record<string, unknown>;
  if (typeof obj.alreadyExists !== 'boolean') {
    throw new Error('AddWishlistResponse: alreadyExists missing');
  }
  return item as AddWishlistResponse;
}

export function assertListResponse(v: unknown): ListWishlistResponse {
  if (!v || typeof v !== 'object') {
    throw new Error('ListWishlistResponse: body is not an object');
  }
  const obj = v as Record<string, unknown>;
  if (!Array.isArray(obj.items)) throw new Error('ListWishlistResponse: items missing');
  if (typeof obj.totalActive !== 'number') throw new Error('ListWishlistResponse: totalActive missing');
  if (typeof obj.totalPurchased !== 'number') throw new Error('ListWishlistResponse: totalPurchased missing');
  obj.items.forEach(assertWishlistItem);
  return obj as unknown as ListWishlistResponse;
}
