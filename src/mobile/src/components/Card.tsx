import React, { PropsWithChildren } from 'react';
import { StyleSheet, View, ViewProps } from 'react-native';

import { colors, radii, spacing } from '../theme';

export interface CardProps extends ViewProps {
  variant?: 'plain' | 'outlined' | 'elevated';
  padding?: keyof typeof spacing | 'none';
}

/**
 * Generic container surface. IKEA leans on outlined cards over elevated
 * shadow — `outlined` is the default. `elevated` exists for hero CTAs
 * where the platform shadow is acceptable.
 */
export function Card({
  variant = 'outlined',
  padding = 'md',
  style,
  children,
  ...rest
}: PropsWithChildren<CardProps>) {
  return (
    <View
      {...rest}
      style={[
        styles.base,
        variant === 'outlined' && styles.outlined,
        variant === 'elevated' && styles.elevated,
        padding !== 'none' && { padding: spacing[padding] },
        style,
      ]}
    >
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  base: {
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    overflow: 'hidden',
  },
  outlined: {
    borderWidth: 1,
    borderColor: colors.border,
  },
  elevated: {
    shadowColor: '#000',
    shadowOpacity: 0.08,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
    elevation: 2,
  },
});
