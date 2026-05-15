import React, { ReactNode, useState } from 'react';
import { Image, Pressable, StyleSheet, Text, View } from 'react-native';

import { colors, radii, spacing, typography } from '../theme';
import { Card } from './Card';
import { Price } from './Price';
import { Tag } from './Tag';

export interface ProductCardProps {
  imageUri: string | null;
  name: string;
  /** e.g. "W220 × D160 × H90 cm". Optional — IKEA shows it whenever known. */
  dimensions?: string;
  price: number;
  compareAtPrice?: number;
  /** Footer meta line, e.g. "Solid oak · In stock". */
  meta?: string;
  /** 0..1; rendered as "매칭 87%" tag in the top-right of the image. */
  matchScore?: number;
  /** Optional category eyebrow, e.g. "BED". */
  eyebrow?: string;
  /** Free-form description (e.g. recommendation rationale) shown below meta. */
  description?: string;
  /** Action row at the bottom (buttons, similar-product strip, etc.). */
  footer?: ReactNode;
  /** testID for the matching badge — useful for screen-level assertions. */
  matchBadgeTestID?: string;
  /** Whole-card tap (image + title area). Footer interactions are independent. */
  onPress?: () => void;
  testID?: string;
}

/**
 * IKEA-style product card: full-bleed square hero, generous padding,
 * dimensions on their own line below the name, prominent price, then
 * a meta line (material · stock). Match badge floats over the image.
 *
 * The component is presentational — image URI must already be absolute
 * (callers do the apiBaseUrl prefix themselves).
 */
export function ProductCard({
  imageUri,
  name,
  dimensions,
  price,
  compareAtPrice,
  meta,
  matchScore,
  eyebrow,
  description,
  footer,
  onPress,
  testID,
  matchBadgeTestID,
}: ProductCardProps) {
  const [imageFailed, setImageFailed] = useState(false);
  const showImage = imageUri != null && !imageFailed;
  const matchPct = matchScore != null ? Math.round(matchScore * 100) : null;

  const headBlock = (
    <>
      <View style={styles.imageWrap}>
        {showImage ? (
          <Image
            source={{ uri: imageUri }}
            style={styles.image}
            resizeMode="cover"
            onError={() => setImageFailed(true)}
          />
        ) : (
          <View style={[styles.image, styles.imagePlaceholder]} />
        )}
        {matchPct != null && (
          <View style={styles.matchBadge} testID={matchBadgeTestID}>
            <Text style={styles.matchBadgeText}>{`매칭 ${matchPct}%`}</Text>
          </View>
        )}
      </View>
      <View style={styles.body}>
        {eyebrow ? <Text style={styles.eyebrow}>{eyebrow}</Text> : null}
        <Text style={styles.name} numberOfLines={2}>
          {name}
        </Text>
        {dimensions ? (
          <Text style={styles.dimensions} numberOfLines={1}>
            {dimensions}
          </Text>
        ) : null}
        <View style={styles.priceRow}>
          <Price amount={price} compareAt={compareAtPrice} size="md" />
        </View>
        {meta ? (
          <View style={styles.metaRow}>
            <Tag label={meta} tone="success" />
          </View>
        ) : null}
        {description ? (
          <Text style={styles.description} numberOfLines={3}>
            {description}
          </Text>
        ) : null}
      </View>
    </>
  );

  return (
    <Card padding="none" style={styles.card} testID={testID}>
      {onPress ? (
        <Pressable
          accessibilityRole="button"
          onPress={onPress}
          style={({ pressed }) => [pressed && { opacity: 0.85 }]}
        >
          {headBlock}
        </Pressable>
      ) : (
        headBlock
      )}
      {footer ? <View style={styles.footer}>{footer}</View> : null}
    </Card>
  );
}

const styles = StyleSheet.create({
  card: {
    marginBottom: spacing.md,
  },
  imageWrap: {
    position: 'relative',
    backgroundColor: colors.surfaceAlt,
  },
  image: {
    width: '100%',
    aspectRatio: 1,
  },
  imagePlaceholder: {
    backgroundColor: colors.surfaceAlt,
  },
  matchBadge: {
    position: 'absolute',
    top: spacing.sm,
    right: spacing.sm,
    backgroundColor: colors.accent,
    paddingHorizontal: spacing.sm,
    paddingVertical: 4,
    borderRadius: radii.pill,
  },
  matchBadgeText: {
    ...typography.label,
    color: colors.onAccent,
  },
  body: {
    padding: spacing.md,
  },
  eyebrow: {
    ...typography.label,
    color: colors.primary,
    marginBottom: spacing.xs,
  },
  name: {
    ...typography.titleL,
  },
  dimensions: {
    ...typography.bodyM,
    color: colors.textMuted,
    marginTop: spacing.xxs,
  },
  priceRow: {
    marginTop: spacing.sm,
  },
  metaRow: {
    marginTop: spacing.sm,
  },
  description: {
    ...typography.bodyM,
    color: colors.textMuted,
    marginTop: spacing.sm,
  },
  footer: {
    paddingHorizontal: spacing.md,
    paddingBottom: spacing.md,
    paddingTop: spacing.xs,
    borderTopWidth: 1,
    borderTopColor: colors.divider,
  },
});
