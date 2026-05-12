/**
 * RecommendationScreen (UC-01-recommendation FR-23 / AC-44..AC-50).
 *
 * Renders the ranked furniture list returned by the Spring endpoint
 * `GET /api/v1/spaces/{roomId}/recommendations`. Displays four Korean
 * section headers (책상 / 침대 / 의자 / 조명) with up to N cards each,
 * plus the full-empty "NO_FIT_ANY_CATEGORY" state and the loading +
 * error states spelled out in FR-23.
 *
 * The "위시리스트에 추가" button is a stub per AC-48 — it emits an
 * analytics event + a toast; the real wishlist write lands in UC-02.
 */

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Image,
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
import {
  PREFERRED_STYLE_LABELS,
  PreferredStyle,
} from '../types/style';

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

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function showToast(msg: string): void {
  if (Platform.OS === 'android' && ToastAndroid) {
    ToastAndroid.show(msg, ToastAndroid.SHORT);
  } else {
    // iOS / web fallback — Alert is deterministic + testable.
    Alert.alert('', msg);
  }
}

function formatKrw(v: number): string {
  return `\u20A9${Number(v).toLocaleString('ko-KR')}`;
}

/**
 * Phase A \u2014 backend-served curated images use relative paths like
 * "/static/furniture/<id>.jpg". `<Image>` needs an absolute URL on every
 * platform, so prefix with the configured API base URL. Naver/external
 * URLs (Phase B) already start with "http(s)://" and pass through.
 */
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

  // -------------------------------------------------------------
  // Resolve display style label — prefer server's echoed
  // preferredStyle; fall back to the optional route param
  // (FR-24) if the screen is rendered before the fetch resolves.
  // -------------------------------------------------------------
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
        <ActivityIndicator size="large" color="#1f6feb" />
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
          <Pressable
            testID="btn-go-style-select"
            style={styles.primaryButton}
            onPress={() => navigation.goBack()}
          >
            <Text style={styles.primaryButtonLabel}>스타일 선택</Text>
          </Pressable>
        ) : (
          <Pressable
            testID="btn-retry"
            style={styles.primaryButton}
            onPress={() => void fetchOnce()}
          >
            <Text style={styles.primaryButtonLabel}>다시 시도</Text>
          </Pressable>
        )}
      </View>
    );
  }

  if (!response) {
    // Belt-and-braces — after loading + no error we always have a response.
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
        <Pressable
          testID="btn-retry-style"
          style={styles.primaryButton}
          onPress={() => navigation.goBack()}
        >
          <Text style={styles.primaryButtonLabel}>스타일 다시 선택</Text>
        </Pressable>
      </View>
    );
  }

  // -------------------------------------------------------------
  // Normal list render (AC-44 / AC-45 / AC-46).
  // -------------------------------------------------------------
  return (
    <ScrollView
      contentContainerStyle={styles.container}
      testID="recommendation-screen"
    >
      <Text style={styles.header}>{headerLabel}</Text>

      {CATEGORY_ORDER.map((cat) => {
        const items = response.recommendations[cat];
        return (
          <View key={cat} style={styles.section} testID={`section-${cat}`}>
            <Text style={styles.sectionTitle}>{CATEGORY_LABELS[cat]}</Text>
            {items.length === 0 ? (
              <Text
                style={styles.emptySectionText}
                testID={`empty-${cat}`}
              >
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
// ItemCard — AC-45, AC-48
// ---------------------------------------------------------------------------

function ItemCard({
  item,
  roomId,
  navigation,
}: {
  item: RecommendationItem;
  roomId: string;
  // AR-furniture-placement FR-1 — thread the parent's navigation prop
  // so the per-card "AR로 배치" button can navigate to `ARPlacement`.
  // Typed loosely (`any`) to avoid importing the stack's full param
  // list here; the parent `Props` already pins the name + shape.
  navigation: Props['navigation'];
}) {
  const [imageFailed, setImageFailed] = useState<boolean>(false);
  const matchPct = Math.round(item.fitScore * 100);

  const onAddToWishlist = async () => {
    // UC-01-recommendation AC-48 — analytics emit MUST still fire
    // exactly once per tap, regardless of whether the real API call
    // succeeds. The UC-02-wishlist AC-49 regression guard re-checks
    // this invariant.
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

  return (
    <View
      style={styles.card}
      testID={`card-${item.furnitureId}`}
    >
      {(() => {
        const uri = resolveImageUri(item.imageUrl);
        if (imageFailed || uri == null) {
          return <View style={[styles.cardImage, styles.cardImagePlaceholder]} />;
        }
        return (
          <Image
            source={{ uri }}
            style={styles.cardImage}
            onError={() => setImageFailed(true)}
            testID={`card-image-${item.furnitureId}`}
          />
        );
      })()}
      <View style={styles.cardBody}>
        <Text style={styles.cardName} numberOfLines={1}>
          {item.name}
        </Text>
        <View style={styles.cardRowPriceAndBadge}>
          <Text style={styles.cardPrice}>{formatKrw(item.price)}</Text>
          <Text
            style={styles.cardBadge}
            testID={`match-badge-${item.furnitureId}`}
          >
            {`매칭 ${matchPct}%`}
          </Text>
        </View>
        <Text style={styles.cardRationale} numberOfLines={2}>
          {item.rationale}
        </Text>
        <View style={styles.cardActionsRow}>
          <Pressable
            testID={`btn-wishlist-${item.furnitureId}`}
            style={styles.wishlistBtn}
            onPress={onAddToWishlist}
            accessibilityLabel="위시리스트에 추가"
            accessibilityRole="button"
          >
            <Text style={styles.wishlistBtnLabel}>위시리스트에 추가</Text>
          </Pressable>
          {/*
            AR-furniture-placement FR-1 / AC-7 — per-card "AR로 배치"
            navigation trigger. Additive only: the wishlist button + its
            handler are unchanged, so UC-01 AC-48 (analytics emit
            invariant) and UC-02 AC-49 (real POST) stay green.
          */}
          <Pressable
            testID={`btn-ar-${item.furnitureId}`}
            style={styles.arBtn}
            onPress={() =>
              navigation.navigate('ARPlacement', { roomId, item })
            }
            accessibilityLabel="AR로 배치"
            accessibilityRole="button"
          >
            <Text style={styles.arBtnLabel}>AR로 배치</Text>
          </Pressable>
        </View>

        {/*
          Phase B anchor — "비슷한 실제 상품 (Naver)" 가로 스크롤 자리.
          Phase A에서는 placeholder 상태만 노출하여 추후 Naver 검색·CLIP
          분류 결과 캐시(furniture_similar_cache)가 채워지면 그대로 데이터
          를 끼우면 됨. 빈 상태 동안 사용자는 "준비 중" 카피만 봄.
        */}
        <View testID={`similar-${item.furnitureId}`} style={styles.similarBlock}>
          <Text style={styles.similarHeading}>비슷한 실제 상품</Text>
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.similarRow}
          >
            <View style={styles.similarPlaceholder}>
              <Text style={styles.similarPlaceholderText}>준비 중</Text>
            </View>
            <View style={styles.similarPlaceholder}>
              <Text style={styles.similarPlaceholderText}>준비 중</Text>
            </View>
            <View style={styles.similarPlaceholder}>
              <Text style={styles.similarPlaceholderText}>준비 중</Text>
            </View>
          </ScrollView>
        </View>
      </View>
    </View>
  );
}

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  container: { padding: 16, paddingBottom: 64 },
  header: { fontSize: 22, fontWeight: '700', marginBottom: 16, color: '#222' },
  section: { marginBottom: 24 },
  sectionTitle: {
    fontSize: 18, fontWeight: '600', color: '#111', marginBottom: 10,
  },
  emptySectionText: {
    fontSize: 13, color: '#888', marginLeft: 4, marginTop: 2,
  },
  card: {
    flexDirection: 'row',
    backgroundColor: '#fff',
    borderRadius: 12,
    borderColor: '#e3e3e3',
    borderWidth: 1,
    marginBottom: 10,
    overflow: 'hidden',
  },
  cardImage: {
    width: 96, height: 96, backgroundColor: '#eee',
  },
  cardImagePlaceholder: {
    backgroundColor: '#ccc',
  },
  cardBody: { flex: 1, padding: 10 },
  cardName: { fontSize: 15, fontWeight: '600', color: '#111' },
  cardRowPriceAndBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 4,
  },
  cardPrice: { fontSize: 14, color: '#333', fontWeight: '500' },
  cardBadge: {
    fontSize: 12, color: '#1f6feb',
    backgroundColor: '#eef4ff', paddingVertical: 2, paddingHorizontal: 8, borderRadius: 10,
    overflow: 'hidden',
  },
  cardRationale: {
    fontSize: 12, color: '#555', marginTop: 6,
  },
  cardActionsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 8,
    flexWrap: 'wrap',
  },
  similarBlock: {
    marginTop: 12,
    borderTopWidth: 1,
    borderTopColor: '#f0f0f0',
    paddingTop: 10,
  },
  similarHeading: {
    fontSize: 12, fontWeight: '600', color: '#444', marginBottom: 6,
  },
  similarRow: { flexDirection: 'row' },
  similarPlaceholder: {
    width: 72, height: 72, marginRight: 8,
    backgroundColor: '#f4f4f4', borderRadius: 8,
    alignItems: 'center', justifyContent: 'center',
    borderWidth: 1, borderColor: '#e6e6e6', borderStyle: 'dashed',
  },
  similarPlaceholderText: { fontSize: 11, color: '#888' },
  wishlistBtn: {
    marginRight: 8,
    marginTop: 4,
    paddingVertical: 8, paddingHorizontal: 12,
    borderRadius: 8,
    borderWidth: 1, borderColor: '#1f6feb',
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  wishlistBtnLabel: { color: '#1f6feb', fontSize: 13 },
  arBtn: {
    marginTop: 4,
    paddingVertical: 8, paddingHorizontal: 12,
    borderRadius: 8,
    backgroundColor: '#1f6feb',
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  arBtnLabel: { color: '#fff', fontSize: 13, fontWeight: '600' },

  // status screens
  statusContainer: {
    flex: 1, alignItems: 'center', justifyContent: 'center', padding: 32,
  },
  statusText: { marginTop: 12, fontSize: 14, color: '#444' },
  errorTitle: {
    fontSize: 15, color: '#222', textAlign: 'center', marginBottom: 16,
  },
  primaryButton: {
    backgroundColor: '#1f6feb',
    paddingVertical: 12, paddingHorizontal: 24,
    borderRadius: 10,
  },
  primaryButtonLabel: { color: '#fff', fontSize: 15, fontWeight: '600' },
});
