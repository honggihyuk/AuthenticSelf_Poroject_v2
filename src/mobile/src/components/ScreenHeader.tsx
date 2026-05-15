import React from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { colors, spacing, typography } from '../theme';

export interface ScreenHeaderProps {
  eyebrow?: string;
  title: string;
  subtitle?: string;
}

/**
 * Top-of-screen heading block. `eyebrow` is the small uppercase label
 * IKEA uses above big titles ("INSPIRATION", "FOR YOUR LIVING ROOM").
 */
export function ScreenHeader({ eyebrow, title, subtitle }: ScreenHeaderProps) {
  return (
    <View style={styles.wrap}>
      {eyebrow ? <Text style={styles.eyebrow}>{eyebrow}</Text> : null}
      <Text style={styles.title}>{title}</Text>
      {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.lg,
    paddingBottom: spacing.md,
  },
  eyebrow: {
    ...typography.label,
    color: colors.primary,
    marginBottom: spacing.xs,
  },
  title: {
    ...typography.displayM,
  },
  subtitle: {
    ...typography.bodyL,
    color: colors.textMuted,
    marginTop: spacing.xs,
  },
});
