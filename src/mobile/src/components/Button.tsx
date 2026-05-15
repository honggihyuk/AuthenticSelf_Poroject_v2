import React from 'react';
import {
  Pressable,
  PressableProps,
  StyleSheet,
  Text,
  TextStyle,
  View,
  ViewStyle,
} from 'react-native';

import { colors, radii, spacing, typography } from '../theme';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'accent';
export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends Omit<PressableProps, 'style' | 'children'> {
  label: string;
  variant?: ButtonVariant;
  size?: ButtonSize;
  fullWidth?: boolean;
  disabled?: boolean;
}

/**
 * IKEA-style button: rectangular with mild rounding, navy primary,
 * yellow `accent` reserved for top-tier "buy" / "wishlist" CTAs.
 * `ghost` is borderless for in-card secondary actions.
 */
export function Button({
  label,
  variant = 'primary',
  size = 'md',
  fullWidth,
  disabled,
  ...rest
}: ButtonProps) {
  const heights: Record<ButtonSize, number> = { sm: 36, md: 48, lg: 56 };
  const paddings: Record<ButtonSize, number> = { sm: spacing.sm, md: spacing.md, lg: spacing.lg };

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: !!disabled }}
      disabled={disabled}
      hitSlop={6}
      {...rest}
      style={({ pressed }) => [
        styles.base,
        variantContainer[variant],
        {
          height: heights[size],
          paddingHorizontal: paddings[size],
          alignSelf: fullWidth ? 'stretch' : 'flex-start',
        },
        pressed && variantPressed[variant],
        disabled && styles.disabled,
      ]}
    >
      <Text style={[styles.label, variantLabel[variant], disabled && styles.labelDisabled]}>
        {label}
      </Text>
    </Pressable>
  );
}

const variantContainer: Record<ButtonVariant, ViewStyle> = {
  primary:   { backgroundColor: colors.primary,    borderWidth: 0 },
  secondary: { backgroundColor: colors.surface,    borderWidth: 1, borderColor: colors.primary },
  ghost:     { backgroundColor: 'transparent',     borderWidth: 0 },
  accent:    { backgroundColor: colors.accent,     borderWidth: 0 },
};

const variantPressed: Record<ButtonVariant, ViewStyle> = {
  primary:   { backgroundColor: colors.primaryPressed },
  secondary: { backgroundColor: colors.primarySoft },
  ghost:     { backgroundColor: colors.surfaceAlt },
  accent:    { backgroundColor: colors.accentPressed },
};

const variantLabel: Record<ButtonVariant, TextStyle> = {
  primary:   { color: colors.onPrimary },
  secondary: { color: colors.primary },
  ghost:     { color: colors.primary },
  accent:    { color: colors.onAccent },
};

const styles = StyleSheet.create({
  base: {
    borderRadius: radii.md,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
  },
  label: {
    ...typography.titleM,
  },
  labelDisabled: {
    color: colors.textFaint,
  },
  disabled: {
    backgroundColor: colors.surfaceAlt,
    borderColor: colors.border,
  },
});

// Re-export a default-styled separator slot consumers can drop between two
// stacked buttons without re-deriving the spacing constant.
export const ButtonGap = () => <View style={{ height: spacing.sm }} />;
