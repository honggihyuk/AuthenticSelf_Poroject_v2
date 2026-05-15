/**
 * RecommendationScreen (UC-01-recommendation FR-23 / AC-44..AC-50).
 *
 * Renders the ranked furniture list returned by the Spring endpoint
 * `GET /api/v1/spaces/{roomId}/recommendations`. Visual layer uses the
 * IKEA-style design system (theme + ProductCard). All testIDs, copy,
 * analytics, and wishlist behavior are preserved from the prior version.
 */

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Image,
  Linking,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  Platform,
  Alert,
} from 'react-native';

// react-native-web does not re-export ToastAndroid; conditional require keeps
// the static analyzer happy while preserving Android behaviour.
const ToastAndroid: typeof import('react-native').ToastAndroid | undefined =
  Platform.OS === 'android'
    ? (require('react-native') as typeof import('react-native')).ToastAndroid
    : undefined;
import { NativeStackScreenProps } from '@react-navigation/native-stack';

import { RootStackParamList } from '../../App';
import { settings } from '../settings';
import { ApiError } from '../api/client';
import {
  emitWishlistAddClicked,
  getRecommendations,
  RecommendationItem,
  RecommendationResponse,
} from '../api/spaces';
import { addToWishlist } from '../api/wishlist';
import { getFurnitureSimilar, SimilarProduct } from '../api/similar';
import {
  PREFERRED_STYLE_LABELS,
  PreferredStyle,
} from '../types/style';
import { Button, ProductCard, ScreenHeader } from '../components';
import { colors, radii, spacing, typography } from '../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'Recommendation'>;

const CATEGORY_ORDER: Array<keyof RecommendationResponse['recommendations']> = [
  'desk',
  'bed',
  'chair',
  'lighting',
];

const CATEGORY_LABELS: Record<string, string> = {
  desk: '책상',
  bed: '침대',
  chair: '의자',
  lighting: '조명',
};

const CATEGORY_EYEBROW: Record<string, string> = {
  desk: 'DESK',
  bed: 'BED',
  chair: 'CHAIR',
  lighting: 'LIGHTING',
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function showToast(msg: string): void {
  if (Platform.OS === 'android' && ToastAndroid) {
    ToastAndroid.show(msg, ToastAndroid.SHORT);
  } else {
    Alert.alert('', msg);
  }
}

function formatKrw(v: number): string {
  return `₩${Number(v).toLocaleString('ko-KR')}`;
}

function resolveImageUri(raw: string | null | undefined): string | null {
  if (!raw) return null;
  if (raw.startsWith('http://') || raw.startsWith('https://') || raw.startsWith('data:')) {
    return raw;
  }
  return `${settings.apiBaseUrl}${raw.startsWith('/') ? raw : '/' + raw}`;
}

function sectionEmptyCopy(cat: string): string {
  return `${CATEGORY_LABELS[cat] ?? cat}는 현재 추천할 가구가 없습니다.`;
}

function mapErrorToCopy(err: unknown): {
  title: string;
  showBackButton: boolean;
} {
  if (err instanceof ApiError) {
    const code = err.body?.errorCode;
    if (code === 'ANALYSIS_NOT_READY') {
      return {
        title: '분석이 아직 끝나지 않았어요. 잠시 후 다시 시도해주세요.',
        showBackButton: false,
      };
    }
    if (code === 'PREFERRED_STYLE_NOT_SET') {
      return {
        title: '스타일을 먼저 선택해주세요.',
        showBackButton: true,
      };
    }
    if (code === 'AI_SERVICE_UNAVAILABLE') {
      return {
        title: '추천 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.',
        showBackButton: false,
      };
    }
  }
  return { title: '추천을 가져오지 못했습니다.', showBackButton: false };
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

export default function RecommendationScreen({ route, navigation }: Props) {
  const { roomId } = route.params;

  const [response, setResponse] = useState<RecommendationResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<unknown | null>(null);

  const fetchOnce = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const resp = await getRecommendations({
        baseUrl: settings.apiBaseUrl,
        userId: settings.userId,
        roomId,
        topNPerCategory: 3,
      });
      setResponse(resp);
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }, [roomId]);

  useEffect(() => {
    void fetchOnce();
  }, [fetchOnce]);

  const headerPreferredStyle: PreferredStyle | null = useMemo(() => {
    if (response?.preferredStyle) return response.preferredStyle;
    const fromRoute = (route.params as { preferredStyle?: PreferredStyle }).preferredStyle;
    if (fromRoute != null) return fromRoute;
    return null;
  }, [response, route.params]);

  const headerLabel = headerPreferredStyle
    ? `${PREFERRED_STYLE_LABELS[headerPreferredStyle]} 스타일 추천`
    : '추천 결과';

  // -------------------------------------------------------------
  // Loading state — FR-23 (AC-50).
  // -------------------------------------------------------------
  if (loading) {
    return (
      <View style={styles.statusContainer} testID="recommendation-loading">
        <ActivityIndicator size="large" color={colors.primary} />
        <Text style={styles.statusText}>추천을 준비하고 있어요...</Text>
      </View>
    );
  }

  // -------------------------------------------------------------
  // Error state — FR-23 (AC-49 covers PREFERRED_STYLE_NOT_SET).
  // -------------------------------------------------------------
  if (error) {
    const { title, showBackButton } = mapErrorToCopy(error);
    return (
      <View style={styles.statusContainer} testID="recommendation-error">
        <Text style={styles.errorTitle}>{title}</Text>
        {showBackButton ? (
          <Button
            testID="btn-go-style-select"
            label="스타일 선택"
            variant="primary"
            size="md"
            onPress={() => navigation.goBack()}
          />
        ) : (
          <Button
            testID="btn-retry"
            label="다시 시도"
            variant="primary"
            size="md"
            onPress={() => void fetchOnce()}
          />
        )}
      </View>
    );
  }

  if (!response) {
    return null;
  }

  // -------------------------------------------------------------
  // NO_FIT_ANY_CATEGORY full-empty state (AC-47).
  // -------------------------------------------------------------
  const allEmpty = CATEGORY_ORDER.every(
    (k) => response.recommendations[k].length === 0,
  );
  if (allEmpty && response.warning === 'NO_FIT_ANY_CATEGORY') {
    return (
      <View style={styles.statusContainer} testID="recommendation-nofit">
        <Text style={styles.errorTitle}>
          이 공간에 딱 맞는 가구를 찾지 못했습니다. 다른 스타일을 선택해 보시겠어요?
        </Text>
        <Button
          testID="btn-retry-style"
          label="스타일 다시 선택"
          variant="primary"
          size="md"
          onPress={() => navigation.goBack()}
        />
      </View>
    );
  }

  // -------------------------------------------------------------
  // Normal list render (AC-44 / AC-45 / AC-46).
  // -------------------------------------------------------------
  return (
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.scrollContent}
      testID="recommendation-screen"
    >
      <ScreenHeader
        eyebrow="당신을 위한 추천"
        title={headerLabel}
        subtitle="공간 분석을 바탕으로 4개 카테고리에서 골랐어요."
      />

      {CATEGORY_ORDER.map((cat) => {
        const items = response.recommendations[cat];
        return (
          <View key={cat} style={styles.section} testID={`section-${cat}`}>
            <View style={styles.sectionHeader}>
              <Text style={styles.sectionEyebrow}>{CATEGORY_EYEBROW[cat]}</Text>
              <Text style={styles.sectionTitle}>{CATEGORY_LABELS[cat]}</Text>
            </View>
            {items.length === 0 ? (
              <Text style={styles.emptySectionText} testID={`empty-${cat}`}>
                {sectionEmptyCopy(cat)}
              </Text>
            ) : (
              items.map((item) => (
                <ItemCard
                  key={item.furnitureId}
                  item={item}
                  roomId={roomId}
                  navigation={navigation}
                />
              ))
            )}
          </View>
        );
      })}
    </ScrollView>
  );
}

// ---------------------------------------------------------------------------
// ItemCard — wraps ProductCard with the wishlist + AR + similar footer.
// AC-45, AC-48, AR FR-1 / AC-7.
// ---------------------------------------------------------------------------

function ItemCard({
  item,
  roomId,
  navigation,
}: {
  item: RecommendationItem;
  roomId: string;
  navigation: Props['navigation'];
}) {
  // Phase B — "비슷한 실제 상품" cache fetch (failures fall back to placeholder).
  const [similar, setSimilar] = useState<SimilarProduct[] | null>(null);
  useEffect(() => {
    let cancelled = false;
    getFurnitureSimilar({
      baseUrl: settings.apiBaseUrl,
      furnitureId: item.furnitureId,
    })
      .then((res) => { if (!cancelled) setSimilar(res.items); })
      .catch(() => { if (!cancelled) setSimilar([]); });
    return () => { cancelled = true; };
  }, [item.furnitureId]);

  const onAddToWishlist = async () => {
    // UC-01-recommendation AC-48 — analytics emit MUST fire exactly once
    // per tap, regardless of whether the real API call succeeds.
    emitWishlistAddClicked(roomId, item.furnitureId);
    try {
      const resp = await addToWishlist({
        baseUrl:     settings.apiBaseUrl,
        userId:      settings.userId,
        furnitureId: item.furnitureId,
        category:    item.type,
        price:       item.price,
      });
      if (resp.alreadyExists) {
        if (resp.status === 'Purchased') {
          showToast('이미 구매 완료로 표시된 항목이에요.');
        } else {
          showToast('이미 위시리스트에 있어요.');
        }
      } else {
        showToast('위시리스트에 담았어요.');
      }
    } catch (e) {
      if (e instanceof ApiError) {
        const code = e.body?.errorCode;
        if (code === 'FURNITURE_NOT_FOUND' || code === 'USER_NOT_FOUND') {
          showToast('위시리스트에 추가하지 못했어요.');
          return;
        }
      }
      showToast('네트워크 오류로 추가하지 못했어요.');
    }
  };

  const footer = (
    <View>
      <View style={styles.actionsRow}>
        <View style={styles.actionsCol}>
          <Button
            testID={`btn-wishlist-${item.furnitureId}`}
            label="위시리스트에 추가"
            variant="secondary"
            size="md"
            fullWidth
            onPress={onAddToWishlist}
            accessibilityLabel="위시리스트에 추가"
          />
        </View>
        <View style={styles.actionsGap} />
        <View style={styles.actionsCol}>
          <Button
            testID={`btn-ar-${item.furnitureId}`}
            label="AR로 배치"
            variant="primary"
            size="md"
            fullWidth
            onPress={() => navigation.navigate('ARPlacement', { roomId, item })}
            accessibilityLabel="AR로 배치"
          />
        </View>
      </View>

      <View testID={`similar-${item.furnitureId}`} style={styles.similarBlock}>
        <Text style={styles.similarHeading}>비슷한 실제 상품</Text>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.similarRow}
        >
          {similar == null ? (
            <View style={[styles.similarPlaceholder, { justifyContent: 'center' }]}>
              <ActivityIndicator size="small" />
            </View>
          ) : similar.length === 0 ? (
            <>
              <View style={styles.similarPlaceholder}>
                <Text style={styles.similarPlaceholderText}>준비 중</Text>
              </View>
              <View style={styles.similarPlaceholder}>
                <Text style={styles.similarPlaceholderText}>준비 중</Text>
              </View>
              <View style={styles.similarPlaceholder}>
                <Text style={styles.similarPlaceholderText}>준비 중</Text>
              </View>
            </>
          ) : (
            similar.map((s) => (
              <Pressable
                key={`${s.source}-${s.externalId}`}
                testID={`similar-card-${item.furnitureId}-${s.externalId}`}
                style={styles.similarCard}
                onPress={() => { Linking.openURL(s.externalUrl).catch(() => undefined); }}
                accessibilityRole="link"
                accessibilityLabel={s.title}
              >
                <Image
                  source={{ uri: s.imageUrl }}
                  style={styles.similarImage}
                  referrerPolicy="no-referrer"
                />
                {s.price != null && (
                  <Text style={styles.similarPrice} numberOfLines={1}>
                    {formatKrw(s.price)}
                  </Text>
                )}
              </Pressable>
            ))
          )}
        </ScrollView>
      </View>
    </View>
  );

  return (
    <ProductCard
      testID={`card-${item.furnitureId}`}
      eyebrow={CATEGORY_EYEBROW[item.type]}
      imageUri={resolveImageUri(item.imageUrl)}
      name={item.name}
      price={item.price}
      matchScore={item.fitScore}
      matchBadgeTestID={`match-badge-${item.furnitureId}`}
      description={item.rationale}
      footer={footer}
    />
  );
}

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  scroll: { flex: 1, backgroundColor: colors.background },
  scrollContent: { paddingBottom: spacing.xxl },

  section: {
    paddingHorizontal: spacing.lg,
    marginBottom: spacing.xl,
  },
  sectionHeader: {
    marginBottom: spacing.md,
  },
  sectionEyebrow: {
    ...typography.label,
    color: colors.primary,
    marginBottom: spacing.xxs,
  },
  sectionTitle: {
    ...typography.titleL,
  },
  emptySectionText: {
    ...typography.bodyM,
    color: colors.textMuted,
    paddingVertical: spacing.md,
  },

  actionsRow: {
    flexDirection: 'row',
    alignItems: 'stretch',
  },
  actionsCol: { flex: 1 },
  actionsGap: { width: spacing.sm },

  similarBlock: {
    marginTop: spacing.md,
    borderTopWidth: 1,
    borderTopColor: colors.divider,
    paddingTop: spacing.sm,
  },
  similarHeading: {
    ...typography.caption,
    color: colors.textSecondary,
    fontWeight: '600',
    marginBottom: spacing.xs,
  },
  similarRow: { flexDirection: 'row' },
  similarPlaceholder: {
    width: 72, height: 72, marginRight: spacing.xs,
    backgroundColor: colors.surfaceAlt, borderRadius: radii.md,
    alignItems: 'center', justifyContent: 'center',
    borderWidth: 1, borderColor: colors.border, borderStyle: 'dashed',
  },
  similarPlaceholderText: { ...typography.caption, color: colors.textFaint },
  similarCard: {
    width: 72, marginRight: spacing.xs,
  },
  similarImage: {
    width: 72, height: 72, borderRadius: radii.md, backgroundColor: colors.surfaceAlt,
  },
  similarPrice: {
    fontSize: 11, color: colors.textSecondary, marginTop: 4, fontWeight: '500',
  },

  // Status screens
  statusContainer: {
    flex: 1,
    backgroundColor: colors.background,
    alignItems: 'center', justifyContent: 'center',
    padding: spacing.xl,
  },
  statusText: {
    ...typography.bodyL,
    color: colors.textSecondary,
    marginTop: spacing.sm,
  },
  errorTitle: {
    ...typography.titleM,
    textAlign: 'center',
    marginBottom: spacing.lg,
  },
});
