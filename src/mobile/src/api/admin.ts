import { ApiError, type UploadErrorBody } from './client';

export type AdminWindow = 'LAST_7D' | 'LAST_30D' | 'ALL';

export type DateCount = { date: string; count: number };
export type DateKrw   = { date: string; krw: number };
export type ColorCount = { color: string; count: number };

export type UsersTile = {
  totalUsers:    number;
  totalAdmins:   number;
  newSignups:    number;
  activeUsers:   number;
  signupsByDay:  DateCount[];
};

export type RoomsTile = {
  totalSpaces:        number;
  statusDistribution: Record<string, number>;
  styleDistribution:  Record<string, number>;
  mainColorTop5:      ColorCount[];
};

export type WishlistTile = {
  totalItems:           number;
  statusDistribution:   Record<string, number>;
  categoryDistribution: Record<string, number>;
  conversionRate:       number;
};

export type SalesTile = {
  totalSalesKrw:      number;
  purchasedItemCount: number;
  averageOrderKrw:    number;
  salesByCategory:    Record<string, number>;
  salesByDay:         DateKrw[];
};

export type AdminOverview = {
  window:      AdminWindow;
  generatedAt: string;
  users:       UsersTile;
  rooms:       RoomsTile;
  wishlist:    WishlistTile;
  sales:       SalesTile;
};

export type GetAdminOverviewArgs = {
  baseUrl: string;
  userId:  string;
  window?: AdminWindow;
};

/**
 * GET /api/v1/admin/overview.
 *
 * Window accepts only LAST_7D | LAST_30D | ALL (canonical wire form per
 * backend TimeWindow.parse). The backend defaults to ALL when omitted; we
 * default to LAST_30D since the dashboard shows trend data more usefully.
 */
export async function getAdminOverview(args: GetAdminOverviewArgs): Promise<AdminOverview> {
  const w = args.window ?? 'LAST_30D';
  const res = await fetch(
    `${args.baseUrl}/api/v1/admin/overview?window=${encodeURIComponent(w)}`,
    {
      method: 'GET',
      headers: { 'X-User-Id': args.userId, Accept: 'application/json' },
    },
  );
  const text = await res.text();
  let parsed: unknown = null;
  try { parsed = text ? JSON.parse(text) : null; } catch { parsed = null; }
  if (res.ok) return parsed as AdminOverview;
  throw new ApiError(res.status, parsed as UploadErrorBody | null);
}

// ---------------------------------------------------------------------------
// UC-ML-PERSIST FR-11 — admin Space-detail debug view: persisted YOLO
// detections envelope, surfaced through the existing
// GET /api/v1/spaces/{roomId} endpoint (the `aiDetections` field).
// ---------------------------------------------------------------------------

/** One persisted YOLO detection: absolute-pixel xyxy bbox + label + score. */
export type AiDetection = {
  label: string;
  bbox: [number, number, number, number]; // x1, y1, x2, y2 absolute pixels
  confidence: number;
};

/** Self-describing envelope persisted in spaces.ai_detections. Null when the
 *  column is NULL (pre-migration, FAILED, transport-fail, or zero objects). */
export type AiDetectionsEnvelope = {
  imageWidth: number;
  imageHeight: number;
  detections: AiDetection[];
};

/** Subset of the space-detail response the admin debug view needs. */
export type AdminSpaceDetail = {
  roomId: string;
  status: string;
  mainColor: string | null;
  aiDetections: AiDetectionsEnvelope | null;
};

export type GetAdminSpaceDetailArgs = {
  baseUrl: string;
  userId: string;
  roomId: string;
};

/**
 * GET /api/v1/spaces/{roomId} — reused by the admin Space-detail debug view
 * to read the persisted detections envelope. Returns the raw space body; the
 * caller renders the bounding-box overlay from `aiDetections`.
 */
export async function getAdminSpaceDetail(
  args: GetAdminSpaceDetailArgs,
): Promise<AdminSpaceDetail> {
  const res = await fetch(
    `${args.baseUrl}/api/v1/spaces/${encodeURIComponent(args.roomId)}`,
    {
      method: 'GET',
      headers: { 'X-User-Id': args.userId, Accept: 'application/json' },
    },
  );
  const text = await res.text();
  let parsed: unknown = null;
  try { parsed = text ? JSON.parse(text) : null; } catch { parsed = null; }
  if (res.ok) return parsed as AdminSpaceDetail;
  throw new ApiError(res.status, parsed as UploadErrorBody | null);
}
