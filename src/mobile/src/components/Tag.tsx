import React from 'react';
import { StyleSheet, Text, TextStyle, View, ViewStyle } from 'react-native';

import { colors, radii, spacing, typography } from '../theme';

export type TagTone = 'neutral' | 'primary' | 'success' | 'danger' | 'accent';

export interface TagProps {
  label: string;
  tone?: TagTone;
}

/**
 * Pill chip used for in-stock, material, ranking badges, etc.
 * IKEA rarely fills tags strongly — `neutral` is the workhorse.
 */
export function Tag({ label, tone = 'neutral' }: TagProps) {
  return (
    <View style={[styles.base, toneContainer[tone]]}>
      <Text style={[styles.label, toneLabel[tone]]} numberOfLines={1}>
        {label}
      </Text>
    </View>
  );
}

const toneContainer: Record<TagTone, ViewStyle> = {
  neutral: { backgroundColor: colors.surfaceAlt },
  primary: { backgroundColor: colors.primarySoft },
  success: { backgroundColor: colors.inStockSoft },
  danger:  { backgroundColor: colors.dangerSoft },
  accent:  { backgroundColor: colors.accent },
};

const toneLabel: Record<TagTone, TextStyle> = {
  neutral: { color: colors.textSecondary },
  primary: { color: colors.primary },
  success: { color: colors.inStock },
  danger:  { color: colors.danger },
  accent:  { color: colors.onAccent },
};

const styles = StyleSheet.create({
  base: {
    alignSelf: 'flex-start',
    paddingHorizontal: spacing.sm,
    paddingVertical: 4,
    borderRadius: radii.pill,
  },
  label: {
    ...typography.label,
    letterSpacing: 0.4,
  },
});
