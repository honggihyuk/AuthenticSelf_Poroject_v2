import React from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { colors, spacing, typography } from '../theme';

export interface PriceProps {
  /** Integer KRW. */
  amount: number;
  /** Optional original price for strikethrough (sale display). */
  compareAt?: number;
  size?: 'sm' | 'md' | 'lg';
}

function formatKrw(v: number): string {
  return `₩${Number(v).toLocaleString('ko-KR')}`;
}

/**
 * Currency display matching the IKEA price block: a strong primary number,
 * optional struck-through compare-at to its right.
 */
export function Price({ amount, compareAt, size = 'md' }: PriceProps) {
  const sizes = {
    sm: { fontSize: 14, lineHeight: 18 },
    md: { fontSize: 18, lineHeight: 22 },
    lg: { fontSize: 24, lineHeight: 28 },
  } as const;

  return (
    <View style={styles.row}>
      <Text style={[styles.amount, sizes[size]]} accessibilityLabel={`${amount}원`}>
        {formatKrw(amount)}
      </Text>
      {compareAt != null && compareAt > amount && (
        <Text style={styles.compareAt}>{formatKrw(compareAt)}</Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'baseline',
  },
  amount: {
    ...typography.price,
    color: colors.textPrimary,
  },
  compareAt: {
    ...typography.bodyM,
    color: colors.textFaint,
    textDecorationLine: 'line-through',
    marginLeft: spacing.xs,
  },
});
