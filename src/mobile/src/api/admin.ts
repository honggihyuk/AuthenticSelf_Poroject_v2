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
