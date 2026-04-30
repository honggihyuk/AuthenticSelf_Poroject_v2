/**
 * WishlistScreen — UC-02-wishlist FR-17 / AC-39..AC-46.
 *
 * Two-tab view over the user's wishlist:
 *   - 보관 중 (Active)   — primary/default tab
 *   - 구매 완료 (Purchased)
 *
 * Each card exposes:
 *   - 구매 완료 (Active) / 보관 중으로 되돌리기 (Purchased): PATCH status
 *   - 삭제: DELETE the item
 * with an Alert.alert confirm prompt + optimistic local update.
 *
 * Loading / error / empty states follow the spec §5 FR-17 copy verbatim
 * so verification can grep for the exact strings.
 */

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  Image,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
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
  deleteWishlistItem,
  getWishlist,
  ListWishlistResponse,
  updateWishlistState,
  WishlistItem,
} from '../api/wishlist';

type Props = NativeStackScreenProps<RootStackParamList, 'Wishlist'>;

const CATEGORY_LABELS: Record<string, string> = {
  desk: '책상',
  bed: '침대',
  chair: '의자',
  lighting: '조명',
};

type Tab = 'active' | 'purchased';

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
  return `\u20A9${Number(v).toLocaleString('ko-KR')}`;
}

function formatYmd(iso: string | null | undefined): string {
  if (!iso) return '';
  // Backend emits LocalDateTime strings like "2026-04-18T09:14:22"
  // (no zone). Slice out the date portion — timezone-safe and avoids
  // the JS Date parser which may subtract a day in Pacific timezones.
  const m = /^(\d{4}-\d{2}-\d{2})/.exec(iso);
  return m ? m[1] : iso;
}

function isWishlistItemNotFound(err: unknown): boolean {
  return err instanceof ApiError && err.body?.errorCode === 'WISHLIST_ITEM_NOT_FOUND';
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

export default function WishlistScreen(_props: Props) {
  const [resp, setResp] = useState<ListWishlistResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<unknown | null>(null);
  const [tab, setTab] = useState<Tab>('active');

  const fetchOnce = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getWishlist({
        baseUrl: settings.apiBaseUrl,
        userId: settings.userId,
      });
      setResp(data);
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchOnce();
  }, [fetchOnce]);

  const { activeItems, purchasedItems } = useMemo(() => {
    const active: WishlistItem[] = [];
    const purchased: WishlistItem[] = [];
    (resp?.items ?? []).forEach((item) => {
      if (item.status === 'Active') active.push(item);
      else purchased.push(item);
    });
    return { activeItems: active, purchasedItems: purchased };
  }, [resp]);

  // -----------------------------------------------------------------
  // Actions
  // -----------------------------------------------------------------
  const onTogglePurchased = useCallback(
    async (item: WishlistItem) => {
      const target = item.status === 'Active' ? 'PURCHASED' : 'ACTIVE';
      const confirmMsg =
        target === 'PURCHASED' ? '구매 완료로 표시할까요?' : '보관 중 상태로 되돌릴까요?';
      Alert.alert(confirmMsg, undefined, [
        { text: '취소', style: 'cancel' },
        {
          text: '확인',
          onPress: async () => {
            try {
              const updated = await updateWishlistState({
                baseUrl: settings.apiBaseUrl,
                userId: settings.userId,
                wishlistId: item.wishlistId,
                status: target,
              });
              // Optimistic local update — move the item between arrays.
              setResp((prev) => {
                if (!prev) return prev;
                const remaining = prev.items.filter((i) => i.wishlistId !== item.wishlistId);
                const next = [updated, ...remaining];
                return {
                  ...prev,
                  items: next,
                  totalActive: next.filter((i) => i.status === 'Active').length,
                  totalPurchased: next.filter((i) => i.status === 'Purchased').length,
                };
              });
              // After marking purchased, jump to the purchased tab so the
              // user sees where the item landed (AC-41).
              if (target === 'PURCHASED') setTab('purchased');
              else setTab('active');
            } catch (e) {
              if (isWishlistItemNotFound(e)) {
                showToast('이미 삭제된 항목이에요.');
                void fetchOnce();
              } else {
                showToast('상태를 변경하지 못했어요.');
              }
            }
          },
        },
      ]);
    },
    [fetchOnce],
  );

  const onDelete = useCallback(
    async (item: WishlistItem) => {
      Alert.alert('위시리스트에서 삭제할까요?', undefined, [
        { text: '취소', style: 'cancel' },
        {
          text: '확인',
          onPress: async () => {
            try {
              await deleteWishlistItem({
                baseUrl: settings.apiBaseUrl,
                userId: settings.userId,
                wishlistId: item.wishlistId,
              });
              setResp((prev) => {
                if (!prev) return prev;
                const next = prev.items.filter((i) => i.wishlistId !== item.wishlistId);
                return {
                  ...prev,
                  items: next,
                  totalActive: next.filter((i) => i.status === 'Active').length,
                  totalPurchased: next.filter((i) => i.status === 'Purchased').length,
                };
              });
            } catch (e) {
              if (isWishlistItemNotFound(e)) {
                showToast('이미 삭제된 항목이에요.');
                void fetchOnce();
              } else {
                showToast('삭제하지 못했어요.');
              }
            }
          },
        },
      ]);
    },
    [fetchOnce],
  );

  // -----------------------------------------------------------------
  // Render — loading / error / content
  // -----------------------------------------------------------------
  if (loading) {
    return (
      <View style={styles.statusContainer} testID="wishlist-loading">
        <ActivityIndicator size="large" color="#1f6feb" />
        <Text style={styles.statusText}>위시리스트를 불러오는 중이에요...</Text>
      </View>
    );
  }

  if (error) {
    return (
      <View style={styles.statusContainer} testID="wishlist-error">
        <Text style={styles.errorTitle}>위시리스트를 불러오지 못했어요.</Text>
        <Pressable
          testID="btn-wishlist-retry"
          style={styles.primaryButton}
          onPress={() => void fetchOnce()}
        >
          <Text style={styles.primaryButtonLabel}>다시 시도</Text>
        </Pressable>
      </View>
    );
  }

  if (!resp) return null;

  const totalActive = resp.totalActive;
  const totalPurchased = resp.totalPurchased;
  const activeTabLabel = `보관 중 (${totalActive})`;
  const purchasedTabLabel = `구매 완료 (${totalPurchased})`;

  return (
    <View style={styles.container} testID="wishlist-screen">
      <Text style={styles.header}>내 위시리스트</Text>

      {/* Segmented control */}
      <View style={styles.tabs}>
        <Pressable
          testID="tab-btn-active"
          onPress={() => setTab('active')}
          style={[styles.tabBtn, tab === 'active' && styles.tabBtnActive]}
        >
          <Text style={[styles.tabLabel, tab === 'active' && styles.tabLabelActive]}>
            {activeTabLabel}
          </Text>
        </Pressable>
        <Pressable
          testID="tab-btn-purchased"
          onPress={() => setTab('purchased')}
          style={[styles.tabBtn, tab === 'purchased' && styles.tabBtnActive]}
        >
          <Text style={[styles.tabLabel, tab === 'purchased' && styles.tabLabelActive]}>
            {purchasedTabLabel}
          </Text>
        </Pressable>
      </View>

      {/* Content */}
      {tab === 'active' ? (
        <View style={styles.tabContent} testID="tab-content-active">
          {activeItems.length === 0 ? (
            <Text style={styles.emptyText} testID="empty-active">
              아직 저장된 가구가 없어요.
            </Text>
          ) : (
            <FlatList
              data={activeItems}
              keyExtractor={(i) => i.wishlistId}
              renderItem={({ item }) => (
                <WishlistCard
                  item={item}
                  onTogglePurchased={() => onTogglePurchased(item)}
                  onDelete={() => onDelete(item)}
                />
              )}
            />
          )}
        </View>
      ) : (
        <View style={styles.tabContent} testID="tab-content-purchased">
          {purchasedItems.length === 0 ? (
            <Text style={styles.emptyText} testID="empty-purchased">
              아직 구매 완료로 표시된 가구가 없어요.
            </Text>
          ) : (
            <FlatList
              data={purchasedItems}
              keyExtractor={(i) => i.wishlistId}
              renderItem={({ item }) => (
                <WishlistCard
                  item={item}
                  onTogglePurchased={() => onTogglePurchased(item)}
                  onDelete={() => onDelete(item)}
                />
              )}
            />
          )}
        </View>
      )}
    </View>
  );
}

// ---------------------------------------------------------------------------
// Card
// ---------------------------------------------------------------------------

function WishlistCard({
  item,
  onTogglePurchased,
  onDelete,
}: {
  item: WishlistItem;
  onTogglePurchased: () => void;
  onDelete: () => void;
}) {
  const [imgFailed, setImgFailed] = useState<boolean>(false);
  const snap = item.furnitureSnapshot;
  const missing = snap == null;
  const name = snap?.name ?? '이름 없음';
  const imageUrl = snap?.imageUrl ?? null;

  return (
    <View style={styles.card} testID={`wishlist-card-${item.wishlistId}`}>
      {imgFailed || imageUrl == null ? (
        <View style={[styles.cardImage, styles.cardImagePlaceholder]} />
      ) : (
        <Image
          source={{ uri: imageUrl }}
          style={styles.cardImage}
          onError={() => setImgFailed(true)}
        />
      )}
      <View style={styles.cardBody}>
        {missing ? (
          <Text
            style={styles.missingText}
            testID={`wishlist-card-missing-${item.wishlistId}`}
          >
            원본 상품이 삭제되었습니다.
          </Text>
        ) : (
          <>
            <Text style={styles.cardName} numberOfLines={1}>{name}</Text>
            <Text style={styles.cardMeta}>
              {CATEGORY_LABELS[item.category] ?? item.category} · {formatKrw(item.price)}
            </Text>
            <Text style={styles.cardDate}>{formatYmd(item.addedAt)}</Text>
          </>
        )}
        <View style={styles.actions}>
          {!missing &&
            (item.status === 'Active' ? (
              <Pressable
                testID={`btn-mark-purchased-${item.wishlistId}`}
                style={styles.btnPrimary}
                onPress={onTogglePurchased}
              >
                <Text style={styles.btnPrimaryLabel}>구매 완료</Text>
              </Pressable>
            ) : (
              <Pressable
                testID={`btn-unmark-purchased-${item.wishlistId}`}
                style={styles.btnSecondary}
                onPress={onTogglePurchased}
              >
                <Text style={styles.btnSecondaryLabel}>보관 중으로 되돌리기</Text>
              </Pressable>
            ))}
          <Pressable
            testID={`btn-delete-${item.wishlistId}`}
            style={styles.btnDelete}
            onPress={onDelete}
          >
            <Text style={styles.btnDeleteLabel}>삭제</Text>
          </Pressable>
        </View>
      </View>
    </View>
  );
}

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16, backgroundColor: '#fafafa' },
  header: { fontSize: 22, fontWeight: '700', marginBottom: 12, color: '#222' },

  tabs: { flexDirection: 'row', marginBottom: 12, gap: 8 },
  tabBtn: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#d6d6d6',
    backgroundColor: '#fff',
    alignItems: 'center',
  },
  tabBtnActive: {
    borderColor: '#1f6feb',
    backgroundColor: '#eef4ff',
  },
  tabLabel: { color: '#555', fontSize: 14 },
  tabLabelActive: { color: '#1f6feb', fontWeight: '600' },

  tabContent: { flex: 1 },
  emptyText: { color: '#888', textAlign: 'center', marginTop: 32, fontSize: 13 },

  card: {
    flexDirection: 'row',
    backgroundColor: '#fff',
    borderRadius: 12,
    borderColor: '#e3e3e3',
    borderWidth: 1,
    marginBottom: 10,
    overflow: 'hidden',
  },
  cardImage: { width: 96, height: 96, backgroundColor: '#eee' },
  cardImagePlaceholder: { backgroundColor: '#ccc' },
  cardBody: { flex: 1, padding: 10 },
  cardName: { fontSize: 15, fontWeight: '600', color: '#111' },
  cardMeta: { fontSize: 13, color: '#444', marginTop: 4 },
  cardDate: { fontSize: 12, color: '#888', marginTop: 4 },
  missingText: { fontSize: 13, color: '#a55', marginBottom: 6 },

  actions: { flexDirection: 'row', gap: 8, marginTop: 8, flexWrap: 'wrap' },

  btnPrimary: {
    paddingVertical: 6,
    paddingHorizontal: 10,
    borderRadius: 8,
    backgroundColor: '#1f6feb',
  },
  btnPrimaryLabel: { color: '#fff', fontSize: 12, fontWeight: '600' },
  btnSecondary: {
    paddingVertical: 6,
    paddingHorizontal: 10,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#1f6feb',
  },
  btnSecondaryLabel: { color: '#1f6feb', fontSize: 12, fontWeight: '600' },
  btnDelete: {
    paddingVertical: 6,
    paddingHorizontal: 10,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#c44',
  },
  btnDeleteLabel: { color: '#c44', fontSize: 12, fontWeight: '600' },

  // status screens
  statusContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 32,
  },
  statusText: { marginTop: 12, fontSize: 14, color: '#444' },
  errorTitle: { fontSize: 15, color: '#222', textAlign: 'center', marginBottom: 16 },
  primaryButton: {
    backgroundColor: '#1f6feb',
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 10,
  },
  primaryButtonLabel: { color: '#fff', fontSize: 15, fontWeight: '600' },
});
