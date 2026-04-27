/**
 * Shared style enums + Korean labels (Task-4 FR-20 / AC-27).
 *
 * Single source of truth for the RN side. The Spring `Style` and
 * `PreferredStyle` enums carry exactly these values — keep them in
 * lock-step.
 */

/** AI-output style — never includes `CURRENT` (AC-4). */
export type Style =
  | 'MODERN'
  | 'SIMPLE'
  | 'CLASSIC'
  | 'SCANDINAVIAN'
  | 'INDUSTRIAL';

/** User-choice style — superset of `Style` by one value (CURRENT). */
export type PreferredStyle = Style | 'CURRENT';

/** All six user-facing options, in display order. */
export const PREFERRED_STYLES: readonly PreferredStyle[] = [
  'CURRENT',
  'MODERN',
  'SIMPLE',
  'CLASSIC',
  'SCANDINAVIAN',
  'INDUSTRIAL',
] as const;

/** The five AI-output values (used for fuzz-assertions / UI labels). */
export const AI_STYLES: readonly Style[] = [
  'MODERN',
  'SIMPLE',
  'CLASSIC',
  'SCANDINAVIAN',
  'INDUSTRIAL',
] as const;

/** Korean user-facing labels (AC-27). */
export const PREFERRED_STYLE_LABELS: Record<PreferredStyle, string> = {
  CURRENT:      '현재 디자인 그대로',
  MODERN:       '모던',
  SIMPLE:       '심플',
  CLASSIC:      '클래식',
  SCANDINAVIAN: '스칸디나비안',
  INDUSTRIAL:   '인더스트리얼',
};

/** English display name used for the AI-detection badge. */
export const STYLE_DISPLAY_NAMES: Record<Style, string> = {
  MODERN:       'Modern',
  SIMPLE:       'Simple',
  CLASSIC:      'Classic',
  SCANDINAVIAN: 'Scandinavian',
  INDUSTRIAL:   'Industrial',
};

/** Runtime guard — used when narrowing API responses. */
export function isStyle(v: unknown): v is Style {
  return typeof v === 'string' && (AI_STYLES as readonly string[]).includes(v);
}

export function isPreferredStyle(v: unknown): v is PreferredStyle {
  return (
    typeof v === 'string' && (PREFERRED_STYLES as readonly string[]).includes(v)
  );
}
