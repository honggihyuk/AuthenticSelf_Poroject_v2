import { TextStyle } from 'react-native';
import { colors } from './colors';

/**
 * Text styles favoring the IKEA hierarchy: a single bold display,
 * a quieter section title, and reading-weight body. Sizes in dp;
 * lineHeight tuned ~1.3 for Latin + Korean mix.
 */
export const typography = {
  displayL: {
    fontSize: 32,
    fontWeight: '700',
    lineHeight: 40,
    color: colors.textPrimary,
    letterSpacing: -0.4,
  } as TextStyle,
  displayM: {
    fontSize: 24,
    fontWeight: '700',
    lineHeight: 32,
    color: colors.textPrimary,
    letterSpacing: -0.2,
  } as TextStyle,
  titleL: {
    fontSize: 20,
    fontWeight: '600',
    lineHeight: 26,
    color: colors.textPrimary,
  } as TextStyle,
  titleM: {
    fontSize: 17,
    fontWeight: '600',
    lineHeight: 22,
    color: colors.textPrimary,
  } as TextStyle,
  bodyL: {
    fontSize: 16,
    fontWeight: '400',
    lineHeight: 22,
    color: colors.textSecondary,
  } as TextStyle,
  bodyM: {
    fontSize: 14,
    fontWeight: '400',
    lineHeight: 20,
    color: colors.textSecondary,
  } as TextStyle,
  caption: {
    fontSize: 12,
    fontWeight: '500',
    lineHeight: 16,
    color: colors.textMuted,
  } as TextStyle,
  label: {
    fontSize: 11,
    fontWeight: '700',
    lineHeight: 14,
    color: colors.textMuted,
    letterSpacing: 0.6,
    textTransform: 'uppercase',
  } as TextStyle,
  price: {
    fontSize: 18,
    fontWeight: '700',
    lineHeight: 22,
    color: colors.textPrimary,
  } as TextStyle,
} as const;

export type TypographyKey = keyof typeof typography;
