/**
 * IKEA UI is only mildly rounded — keep radii small. Use `pill` only for
 * tags/badges, `lg` for hero cards, `md` for default surfaces.
 */
export const radii = {
  none: 0,
  sm:   4,
  md:   8,
  lg:   12,
  pill: 999,
} as const;

export type RadiiKey = keyof typeof radii;
