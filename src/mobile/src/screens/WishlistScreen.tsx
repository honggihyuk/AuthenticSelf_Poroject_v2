/**
 * WishlistScreen — UC-02-wishlist FR-17 / AC-39..AC-46.
 *
 * Two-tab view (보관 중 / 구매 완료) over the user's wishlist. Visual
 * layer uses the IKEA-style design system; testIDs, copy, alert flow,
 * and effects are preserved verbatim.
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
import { Button, Card } from '../components';
import { colors, radii, spacing, typography } from '../theme';

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
  } else if (Platform.OS === 'web' && typeof window !== 'undefined') {
    // react-native-web's Alert.alert is a no-op, so use the DOM dialog.
    window.alert(msg);
  } else {
    Alert.alert('', msg);
  }
}

/**
 * Cross-platform confirm dialog. react-native-web's `Alert.alert` is a
 * complete no-op — it ignores the button array, so the "확인" `onPress`
 * never fires and actions (구매 완료 전환 / 삭제) silently do nothing on web.
 * Use the DOM `window.confirm` on web; keep the native two-button Alert
 * on iOS/Android.
 */
function confirmAction(message: string, onConfirm: () => void): void {
  if (Platform.OS === 'web') {
    if (typeof window !== 'undefined' && window.confirm(message)) onConfirm();
    return;
  }
  Alert.alert(message, undefined, [
    { text: '취소', style: 'cancel' },
    { text: '확인', onPress: onConfirm },
  ]);
}

function formatKrw(v: number): string {
  return `₩${Number(v).toLocaleString('ko-KR')}`;
}

function formatYmd(iso: string | null | undefined): string {
  if (!iso) return '';
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

  const onTogglePurchased = useCallback(
    async (item: WishlistItem) => {
      const target = item.status === 'Active' ? 'PURCHASED' : 'ACTIVE';
      const confirmMsg =
        target === 'PURCHASED' ? '구매 완료로 표시할까요?' : '보관 중 상태로 되돌릴까요?';
      confirmAction(confirmMsg, async () => {
        try {
          const updated = await updateWishlistState({
            baseUrl: settings.apiBaseUrl,
            userId: settings.userId,
            wishlistId: item.wishlistId,
            status: target,
          });
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
      });
    },
    [fetchOnce],
  );

  const onDelete = useCallback(
    async (item: WishlistItem) => {
      confirmAction('위시리스트에서 삭제할까요?', async () => {
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
      });
    },
    [fetchOnce],
  );

  if (loading) {
    return (
      <View style={styles.statusContainer} testID="wishlist-loading">
        <ActivityIndicator size="large" color={colors.primary} />
        <Text style={styles.statusText}>위시리스트를 불러오는 중이에요...</Text>
      </View>
    );
  }

  if (error) {
    return (
      <View style={styles.statusContainer} testID="wishlist-error">
        <Text style={styles.errorTitle}>위시리스트를 불러오지 못했어요.</Text>
        <Button
          testID="btn-wishlist-retry"
          label="다시 시도"
          variant="primary"
          size="md"
          onPress={() => void fetchOnce()}
        />
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

      <View style={styles.tabs}>
        <Pressable
          testID="tab-btn-active"
          onPress={() => setTab('active')}
          accessibilityRole="tab"
          accessibilityState={{ selected: tab === 'active' }}
          style={[styles.tabBtn, tab === 'active' && styles.tabBtnActive]}
        >
          <Text style={[styles.tabLabel, tab === 'active' && styles.tabLabelActive]}>
            {activeTabLabel}
          </Text>
        </Pressable>
        <Pressable
          testID="tab-btn-purchased"
          onPress={() => setTab('purchased')}
          accessibilityRole="tab"
          accessibilityState={{ selected: tab === 'purchased' }}
          style={[styles.tabBtn, tab === 'purchased' && styles.tabBtnActive]}
        >
          <Text style={[styles.tabLabel, tab === 'purchased' && styles.tabLabelActive]}>
            {purchasedTabLabel}
          </Text>
        </Pressable>
      </View>

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
    <Card
      padding="none"
      style={styles.card}
      testID={`wishlist-card-${item.wishlistId}`}
    >
      <View style={styles.cardRow}>
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
                  style={({ pressed }) => [styles.btnPrimary, pressed && styles.btnPressed]}
                  onPress={onTogglePurchased}
                  accessibilityRole="button"
                  accessibilityLabel="구매 완료"
                >
                  <Text style={styles.btnPrimaryLabel}>구매 완료</Text>
                </Pressable>
              ) : (
                <Pressable
                  testID={`btn-unmark-purchased-${item.wishlistId}`}
                  style={({ pressed }) => [styles.btnSecondary, pressed && styles.btnPressed]}
                  onPress={onTogglePurchased}
                  accessibilityRole="button"
                  accessibilityLabel="보관 중으로 되돌리기"
                >
                  <Text style={styles.btnSecondaryLabel}>보관 중으로 되돌리기</Text>
                </Pressable>
              ))}
            <Pressable
              testID={`btn-delete-${item.wishlistId}`}
              style={({ pressed }) => [styles.btnDelete, pressed && styles.btnPressed]}
              onPress={onDelete}
              accessibilityRole="button"
              accessibilityLabel="삭제"
            >
              <Text style={styles.btnDeleteLabel}>삭제</Text>
            </Pressable>
          </View>
        </View>
      </View>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: spacing.md,
    backgroundColor: colors.background,
  },
  header: {
    ...typography.displayM,
    marginBottom: spacing.md,
  },

  tabs: {
    flexDirection: 'row',
    marginBottom: spacing.sm,
    gap: spacing.xs,
  },
  tabBtn: {
    flex: 1,
    paddingVertical: spacing.sm,
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    alignItems: 'center',
  },
  tabBtnActive: {
    borderColor: colors.primary,
    backgroundColor: colors.primarySoft,
  },
  tabLabel: { ...typography.bodyM, color: colors.textMuted },
  tabLabelActive: { color: colors.primary, fontWeight: '600' },

  tabContent: { flex: 1 },
  emptyText: {
    ...typography.bodyM,
    color: colors.textMuted,
    textAlign: 'center',
    marginTop: spacing.xl,
  },

  card: { marginBottom: spacing.sm },
  cardRow: {
    flexDirection: 'row',
  },
  cardImage: {
    width: 96, height: 96,
    backgroundColor: colors.surfaceAlt,
  },
  cardImagePlaceholder: { backgroundColor: colors.surfaceAlt },
  cardBody: { flex: 1, padding: spacing.sm },
  cardName: { ...typography.titleM },
  cardMeta: { ...typography.bodyM, color: colors.textSecondary, marginTop: spacing.xxs },
  cardDate: { ...typography.caption, color: colors.textFaint, marginTop: spacing.xxs },
  missingText: {
    ...typography.bodyM,
    color: colors.danger,
    marginBottom: spacing.xs,
  },

  actions: {
    flexDirection: 'row',
    gap: spacing.xs,
    marginTop: spacing.xs,
    flexWrap: 'wrap',
  },

  btnPressed: { opacity: 0.85 },
  btnPrimary: {
    paddingVertical: 6,
    paddingHorizontal: spacing.sm,
    borderRadius: radii.md,
    backgroundColor: colors.primary,
  },
  btnPrimaryLabel: { color: colors.onPrimary, fontSize: 12, fontWeight: '600' },
  btnSecondary: {
    paddingVertical: 6,
    paddingHorizontal: spacing.sm,
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.primary,
  },
  btnSecondaryLabel: { color: colors.primary, fontSize: 12, fontWeight: '600' },
  btnDelete: {
    paddingVertical: 6,
    paddingHorizontal: spacing.sm,
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.danger,
  },
  btnDeleteLabel: { color: colors.danger, fontSize: 12, fontWeight: '600' },

  // Status screens
  statusContainer: {
    flex: 1,
    backgroundColor: colors.background,
    alignItems: 'center',
    justifyContent: 'center',
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
