import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';

import { useAuth } from '../auth/AuthContext';
import { settings } from '../settings';
import { AdminOverview, AdminWindow, getAdminOverview } from '../api/admin';
import { Button, Card, ScreenHeader } from '../components';
import { colors, radii, spacing, typography } from '../theme';

const WINDOWS: { value: AdminWindow; label: string }[] = [
  { value: 'LAST_7D',  label: '7일' },
  { value: 'LAST_30D', label: '30일' },
  { value: 'ALL',      label: '전체' },
];

const ROOMS_STATUS_LABELS: Record<string, string> = {
  ANALYZED:         '분석 완료',
  FAILED:           '분석 실패',
  PENDING_ANALYSIS: '대기 중',
};

const WISHLIST_STATUS_LABELS: Record<string, string> = {
  ACTIVE:    '보관 중',
  PURCHASED: '구매 완료',
};

const CATEGORY_LABELS: Record<string, string> = {
  desk:     '책상',
  bed:      '침대',
  chair:    '의자',
  lighting: '조명',
};

function formatKrw(v: number): string {
  return `₩${Number(v).toLocaleString('ko-KR')}`;
}

function formatPct(v: number): string {
  return `${(v * 100).toFixed(1)}%`;
}

function formatDateTime(iso: string): string {
  try {
    return new Date(iso).toLocaleString('ko-KR', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit',
    });
  } catch {
    return iso;
  }
}

/**
 * Admin operations console. Renders the four tiles returned by
 * {@code GET /api/v1/admin/overview} (users, rooms, wishlist, sales) and
 * exposes the time-window toggle (7D / 30D / ALL). Distribution maps render
 * as simple proportional bars — no chart library, no extra dependency.
 */
export default function AdminDashboardScreen() {
  const { state, logout } = useAuth();
  const [data, setData] = useState<AdminOverview | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<unknown | null>(null);
  const [windowSel, setWindowSel] = useState<AdminWindow>('LAST_30D');

  const load = useCallback(async (w: AdminWindow) => {
    setLoading(true);
    setError(null);
    try {
      const resp = await getAdminOverview({
        baseUrl: settings.apiBaseUrl,
        userId:  settings.userId,
        window:  w,
      });
      setData(resp);
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(windowSel); }, [load, windowSel]);

  return (
    <SafeAreaView style={styles.safe} testID="admin-dashboard">
      <ScreenHeader
        eyebrow="ADMIN"
        title={`${state?.name ?? '관리자'}님 환영합니다`}
        subtitle="전체 운영 현황을 한눈에 확인하세요."
      />

      <ScrollView contentContainerStyle={styles.body}>
        <View style={styles.windowRow} accessibilityRole="tablist">
          {WINDOWS.map((w) => {
            const active = w.value === windowSel;
            return (
              <Pressable
                key={w.value}
                testID={`btn-window-${w.value}`}
                onPress={() => setWindowSel(w.value)}
                accessibilityRole="tab"
                accessibilityState={{ selected: active }}
                style={[styles.windowBtn, active && styles.windowBtnActive]}
              >
                <Text style={[styles.windowLabel, active && styles.windowLabelActive]}>
                  {w.label}
                </Text>
              </Pressable>
            );
          })}
        </View>

        {loading ? (
          <View style={styles.statusRow} testID="admin-loading">
            <ActivityIndicator color={colors.primary} />
          </View>
        ) : error ? (
          <View testID="admin-error">
            <Text style={styles.errorText}>현황을 불러오지 못했습니다.</Text>
            <View style={styles.gap} />
            <Button
              testID="btn-admin-retry"
              label="다시 시도"
              variant="secondary"
              size="md"
              onPress={() => void load(windowSel)}
            />
          </View>
        ) : data ? (
          <>
            <Text style={styles.metaNote}>
              생성 {formatDateTime(data.generatedAt)} · 기간 {data.window}
            </Text>

            <UsersCard tile={data.users} />
            <RoomsCard tile={data.rooms} />
            <WishlistCard tile={data.wishlist} />
            <SalesCard tile={data.sales} />
          </>
        ) : null}

        <View style={styles.divider} />

        <Text style={styles.sectionTitle}>운영 작업</Text>
        <Button
          testID="btn-trigger-batch"
          label="야간 배치 수동 트리거 (placeholder)"
          variant="secondary"
          size="md"
          fullWidth
          onPress={() =>
            Alert.alert('준비 중', 'Phase B 배치 트리거 엔드포인트가 연결되면 동작합니다.')
          }
        />
      </ScrollView>

      <View style={styles.footer}>
        <Button
          testID="btn-admin-logout"
          label="로그아웃"
          variant="secondary"
          size="md"
          fullWidth
          onPress={logout}
        />
      </View>
    </SafeAreaView>
  );
}

// ---------------------------------------------------------------------------
// Tile cards
// ---------------------------------------------------------------------------

function UsersCard({ tile }: { tile: AdminOverview['users'] }) {
  return (
    <Card padding="md" style={styles.tileCard} testID="tile-users">
      <Text style={styles.tileTitle}>이용자</Text>
      <View style={styles.kpiRow}>
        <Kpi label="누적 이용자" value={tile.totalUsers} />
        <Kpi label="관리자"      value={tile.totalAdmins} />
      </View>
      <View style={styles.kpiRow}>
        <Kpi label="기간 내 신규 가입" value={tile.newSignups} />
        <Kpi label="기간 내 활동 유저" value={tile.activeUsers} />
      </View>
    </Card>
  );
}

function RoomsCard({ tile }: { tile: AdminOverview['rooms'] }) {
  const failedPct = tile.totalSpaces > 0
    ? (tile.statusDistribution['FAILED'] ?? 0) / tile.totalSpaces
    : 0;
  return (
    <Card padding="md" style={styles.tileCard} testID="tile-rooms">
      <Text style={styles.tileTitle}>방 분석</Text>
      <View style={styles.kpiRow}>
        <Kpi label="누적 방 분석" value={tile.totalSpaces} />
        <Kpi label="분석 실패율" value={formatPct(failedPct)} />
      </View>

      <Text style={styles.subHeading}>분석 상태</Text>
      <DistributionBars
        items={Object.entries(tile.statusDistribution)}
        labelMap={ROOMS_STATUS_LABELS}
      />

      <Text style={styles.subHeading}>선호 스타일</Text>
      <DistributionBars items={Object.entries(tile.styleDistribution)} />

      <Text style={styles.subHeading}>주요 색상 Top 5</Text>
      <View style={styles.colorRow}>
        {tile.mainColorTop5.length === 0 ? (
          <Text style={styles.muted}>데이터 없음</Text>
        ) : (
          tile.mainColorTop5.map((c) => (
            <View key={c.color} style={styles.colorChip} testID={`color-${c.color}`}>
              <View style={[styles.colorSwatch, { backgroundColor: c.color }]} />
              <Text style={styles.colorLabel}>{c.color}</Text>
              <Text style={styles.colorCount}>{c.count}</Text>
            </View>
          ))
        )}
      </View>
    </Card>
  );
}

function WishlistCard({ tile }: { tile: AdminOverview['wishlist'] }) {
  return (
    <Card padding="md" style={styles.tileCard} testID="tile-wishlist">
      <Text style={styles.tileTitle}>위시리스트</Text>
      <View style={styles.kpiRow}>
        <Kpi label="총 항목"      value={tile.totalItems} />
        <Kpi label="구매 전환율" value={formatPct(tile.conversionRate)} />
      </View>

      <Text style={styles.subHeading}>상태</Text>
      <DistributionBars
        items={Object.entries(tile.statusDistribution)}
        labelMap={WISHLIST_STATUS_LABELS}
      />

      <Text style={styles.subHeading}>카테고리</Text>
      <DistributionBars
        items={Object.entries(tile.categoryDistribution)}
        labelMap={CATEGORY_LABELS}
      />
    </Card>
  );
}

function SalesCard({ tile }: { tile: AdminOverview['sales'] }) {
  return (
    <Card padding="md" style={styles.tileCard} testID="tile-sales">
      <Text style={styles.tileTitle}>매출</Text>
      <View style={styles.kpiRow}>
        <Kpi label="총 매출"     value={formatKrw(tile.totalSalesKrw)} />
        <Kpi label="구매 건수"   value={tile.purchasedItemCount} />
      </View>
      <View style={styles.kpiRow}>
        <Kpi label="평균 객단가" value={formatKrw(tile.averageOrderKrw)} />
      </View>

      <Text style={styles.subHeading}>카테고리별 매출</Text>
      <DistributionBars
        items={Object.entries(tile.salesByCategory)}
        labelMap={CATEGORY_LABELS}
        formatValue={(n) => formatKrw(n)}
      />
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Atoms
// ---------------------------------------------------------------------------

function Kpi({ label, value }: { label: string; value: number | string }) {
  const display = typeof value === 'number' ? value.toLocaleString('ko-KR') : value;
  return (
    <View style={styles.kpi}>
      <Text style={styles.kpiValue} testID={`kpi-${label}`}>{display}</Text>
      <Text style={styles.kpiLabel}>{label}</Text>
    </View>
  );
}

function DistributionBars({
  items,
  labelMap,
  formatValue,
}: {
  items: Array<[string, number]>;
  labelMap?: Record<string, string>;
  formatValue?: (n: number) => string;
}) {
  if (items.length === 0) {
    return <Text style={styles.muted}>데이터 없음</Text>;
  }
  const max = Math.max(1, ...items.map(([, v]) => v));
  return (
    <View>
      {items.map(([key, val]) => {
        const widthPct = (val / max) * 100;
        const label = labelMap?.[key] ?? key;
        const displayVal = formatValue ? formatValue(val) : val.toLocaleString('ko-KR');
        return (
          <View key={key} style={styles.barRow}>
            <Text style={styles.barLabel} numberOfLines={1}>{label}</Text>
            <View style={styles.barTrack}>
              <View style={[styles.barFill, { width: `${widthPct}%` }]} />
            </View>
            <Text style={styles.barValue}>{displayVal}</Text>
          </View>
        );
      })}
    </View>
  );
}

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  body: {
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.md,
    paddingBottom: spacing.xl,
  },

  windowRow: {
    flexDirection: 'row',
    backgroundColor: colors.surfaceAlt,
    borderRadius: radii.md,
    padding: 4,
    marginBottom: spacing.md,
  },
  windowBtn: {
    flex: 1,
    paddingVertical: spacing.xs,
    borderRadius: radii.sm,
    alignItems: 'center',
  },
  windowBtnActive: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.primary,
  },
  windowLabel: { ...typography.bodyM, color: colors.textMuted },
  windowLabelActive: { color: colors.primary, fontWeight: '600' },

  metaNote: {
    ...typography.caption,
    color: colors.textFaint,
    marginBottom: spacing.sm,
  },

  tileCard: { marginBottom: spacing.md },
  tileTitle: { ...typography.titleM, marginBottom: spacing.sm },
  subHeading: {
    ...typography.label,
    color: colors.textSecondary,
    marginTop: spacing.md,
    marginBottom: spacing.xs,
  },

  kpiRow: {
    flexDirection: 'row',
    marginBottom: spacing.xs,
  },
  kpi: { flex: 1 },
  kpiValue: { ...typography.displayM, color: colors.primary },
  kpiLabel: { ...typography.caption, color: colors.textMuted, marginTop: spacing.xxs },

  barRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: spacing.xxs,
  },
  barLabel: {
    ...typography.bodyM,
    width: 96,
    color: colors.textSecondary,
  },
  barTrack: {
    flex: 1,
    height: 10,
    backgroundColor: colors.surfaceAlt,
    borderRadius: radii.sm,
    marginHorizontal: spacing.sm,
    overflow: 'hidden',
  },
  barFill: {
    height: '100%',
    backgroundColor: colors.primary,
  },
  barValue: {
    ...typography.caption,
    color: colors.textPrimary,
    minWidth: 56,
    textAlign: 'right',
  },

  colorRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginTop: spacing.xxs,
  },
  colorChip: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 4,
    paddingHorizontal: spacing.xs,
    backgroundColor: colors.surfaceAlt,
    borderRadius: radii.sm,
    marginRight: spacing.xs,
    marginBottom: spacing.xs,
  },
  colorSwatch: {
    width: 14,
    height: 14,
    borderRadius: 7,
    borderWidth: 1,
    borderColor: colors.border,
    marginRight: spacing.xs,
  },
  colorLabel: { ...typography.caption, color: colors.textSecondary, marginRight: spacing.xs },
  colorCount: { ...typography.caption, color: colors.textPrimary, fontWeight: '600' },

  muted: { ...typography.bodyM, color: colors.textMuted },

  divider: {
    height: 1,
    backgroundColor: colors.divider,
    marginVertical: spacing.lg,
  },
  sectionTitle: { ...typography.titleM, marginBottom: spacing.sm },
  statusRow: { paddingVertical: spacing.lg, alignItems: 'center' },
  errorText: { ...typography.bodyM, color: colors.danger },
  gap: { height: spacing.sm },
  footer: {
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.lg,
    paddingTop: spacing.sm,
    borderTopWidth: 1,
    borderTopColor: colors.divider,
  },
});
