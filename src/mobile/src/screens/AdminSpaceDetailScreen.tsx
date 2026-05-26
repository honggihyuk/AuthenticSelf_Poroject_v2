import React, { useEffect, useState } from 'react';
import { ActivityIndicator, ScrollView, StyleSheet, Text, View } from 'react-native';
import { NativeStackScreenProps } from '@react-navigation/native-stack';

import { RootStackParamList } from '../../App';
import { settings } from '../settings';
import { AdminSpaceDetail, getAdminSpaceDetail } from '../api/admin';
import { Button, DetectionOverlay, ScreenHeader } from '../components';
import { colors, spacing, typography } from '../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'AdminSpaceDetail'>;

/**
 * UC-ML-PERSIST FR-11 / FR-12 — admin Space-detail debug view.
 *
 * Fetches the persisted space (via GET /api/v1/spaces/{roomId}) and renders
 * the persisted YOLO detections as a bounding-box overlay over the room photo
 * (reusing the {@link DetectionOverlay} clone of the ObjectsScreen pattern).
 *
 * FR-12 — a space with `aiDetections == null` (pre-migration / FAILED /
 * transport-fail / zero objects) renders the photo with zero boxes and an
 * empty-state hint, never crashing.
 */
export default function AdminSpaceDetailScreen({ route, navigation }: Props) {
  const { roomId, photoUri } = route.params;

  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<AdminSpaceDetail | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    getAdminSpaceDetail({
      baseUrl: settings.apiBaseUrl,
      userId: settings.userId,
      roomId,
    })
      .then((res) => {
        if (!cancelled) setData(res);
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [roomId]);

  const envelope = data?.aiDetections ?? null;
  // FR-12 — default to safe values so the overlay renders the empty-state
  // hint (no boxes) for NULL / unparseable detections without crashing.
  const imageWidth = envelope?.imageWidth ?? 0;
  const imageHeight = envelope?.imageHeight ?? 0;
  const detections = envelope?.detections ?? [];

  return (
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.container}
      testID="admin-space-detail"
    >
      <ScreenHeader
        eyebrow="ADMIN · DEBUG"
        title="검출 디버그 뷰"
        subtitle={`roomId: ${roomId}`}
      />

      <View style={styles.body}>
        {loading && (
          <View style={styles.loadingRow} testID="admin-space-loading">
            <ActivityIndicator color={colors.primary} />
            <Text style={styles.loadingText}>불러오는 중…</Text>
          </View>
        )}

        {error != null && (
          <Text testID="admin-space-error" style={styles.errorText}>
            {error}
          </Text>
        )}

        {!loading && error == null && (
          <>
            <DetectionOverlay
              photoUri={photoUri}
              imageWidth={imageWidth}
              imageHeight={imageHeight}
              detections={detections}
            />
            <Text style={styles.meta} testID="admin-space-count">
              검출 {detections.length}개 · 상태 {data?.status ?? '-'}
            </Text>
          </>
        )}

        <View style={styles.footer}>
          <Button
            testID="btn-back"
            label="돌아가기"
            variant="secondary"
            size="md"
            fullWidth
            onPress={() => navigation.goBack()}
          />
        </View>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1, backgroundColor: colors.background },
  container: { paddingBottom: spacing.xxl },
  body: { paddingHorizontal: spacing.lg },
  loadingRow: { flexDirection: 'row', alignItems: 'center', marginVertical: spacing.md },
  loadingText: { ...typography.bodyM, color: colors.textMuted, marginLeft: spacing.xs },
  errorText: { ...typography.bodyM, color: colors.danger, marginVertical: spacing.md },
  meta: { ...typography.bodyM, color: colors.textMuted, marginTop: spacing.md },
  footer: { marginTop: spacing.xl },
});
