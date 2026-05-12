import React, { useEffect, useState } from 'react';
import {
  View, Text, Image, ScrollView, ActivityIndicator, Pressable, StyleSheet,
} from 'react-native';
import { NativeStackScreenProps } from '@react-navigation/native-stack';

import { RootStackParamList } from '../../App';
import { settings } from '../settings';
import { getRoomObjects, DetectedObject, ObjectsResponse } from '../api/objects';

type Props = NativeStackScreenProps<RootStackParamList, 'Objects'>;

const BOX_COLORS = ['#e6194B', '#3cb44b', '#4363d8', '#f58231', '#911eb4', '#42d4f4', '#f032e6'];

function colorFor(label: string): string {
  let h = 0;
  for (let i = 0; i < label.length; i++) h = (h * 31 + label.charCodeAt(i)) >>> 0;
  return BOX_COLORS[h % BOX_COLORS.length];
}

export default function ObjectsScreen({ route, navigation }: Props) {
  const { roomId, photoUri } = route.params;

  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<ObjectsResponse | null>(null);
  const [containerW, setContainerW] = useState<number>(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    getRoomObjects({
      baseUrl: settings.apiBaseUrl,
      userId:  settings.userId,
      roomId,
    })
      .then((res) => {
        if (cancelled) return;
        setData(res);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setError(e instanceof Error ? e.message : String(e));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [roomId]);

  // Display dimensions: keep aspect ratio of original image, fit width.
  const displayWidth  = containerW > 0 ? containerW : 0;
  const aspect = data ? data.imageHeight / Math.max(1, data.imageWidth) : 0.75;
  const displayHeight = displayWidth * aspect;
  const scaleX = data ? displayWidth  / Math.max(1, data.imageWidth)  : 1;
  const scaleY = data ? displayHeight / Math.max(1, data.imageHeight) : 1;

  return (
    <ScrollView contentContainerStyle={styles.container} testID="objects-screen">
      <Text style={styles.title}>객체 검출</Text>
      <Text style={styles.subtitle}>roomId: {roomId}</Text>

      <View
        style={styles.imageWrap}
        onLayout={(e) => setContainerW(e.nativeEvent.layout.width)}
      >
        {photoUri ? (
          <Image
            testID="objects-photo"
            source={{ uri: photoUri }}
            style={{ width: displayWidth, height: displayHeight, backgroundColor: '#f2f2f2' }}
            resizeMode="cover"
          />
        ) : (
          <View
            style={[styles.placeholder, { width: displayWidth, height: displayHeight || 240 }]}
          >
            <Text style={styles.placeholderText}>
              사진 미리보기를 사용할 수 없습니다 (좌표만 표시).
            </Text>
          </View>
        )}

        {data?.objects.map((o, idx) => {
          const [x1, y1, x2, y2] = o.bbox;
          const left   = x1 * scaleX;
          const top    = y1 * scaleY;
          const width  = (x2 - x1) * scaleX;
          const height = (y2 - y1) * scaleY;
          const color  = colorFor(o.label);
          return (
            <View
              key={`box-${idx}`}
              testID={`bbox-${idx}`}
              pointerEvents="none"
              style={[
                styles.bbox,
                { left, top, width, height, borderColor: color },
              ]}
            >
              <Text style={[styles.bboxLabel, { backgroundColor: color }]}>
                {o.label} {Math.round(o.confidence * 100)}%
              </Text>
            </View>
          );
        })}
      </View>

      {loading && (
        <View style={styles.loadingRow}>
          <ActivityIndicator />
          <Text style={styles.loadingText}>객체 검출 중…</Text>
        </View>
      )}

      {error != null && (
        <Text testID="objects-error" style={styles.errorText}>{error}</Text>
      )}

      {data && (
        <View style={styles.listSection}>
          <Text style={styles.sectionHeading}>
            검출 결과 {data.objects.length}개 · {data.processingMs} ms
          </Text>
          {data.objects.length === 0 && (
            <Text style={styles.empty}>검출된 객체가 없습니다.</Text>
          )}
          {data.objects.map((o: DetectedObject, idx: number) => (
            <View key={`row-${idx}`} style={styles.listRow}>
              <View style={[styles.swatch, { backgroundColor: colorFor(o.label) }]} />
              <Text style={styles.listLabel}>{o.label}</Text>
              <Text style={styles.listConf}>{Math.round(o.confidence * 100)}%</Text>
              <Text style={styles.listBbox}>
                [{o.bbox.map((n) => Math.round(n)).join(', ')}]
              </Text>
            </View>
          ))}
        </View>
      )}

      <Pressable
        testID="btn-back"
        style={styles.secondaryBtn}
        onPress={() => navigation.goBack()}
      >
        <Text style={styles.secondaryBtnLabel}>돌아가기</Text>
      </Pressable>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container:   { padding: 16, paddingBottom: 80 },
  title:       { fontSize: 20, fontWeight: '700', marginBottom: 4 },
  subtitle:    { fontSize: 12, color: '#666', marginBottom: 12 },
  imageWrap:   { position: 'relative', width: '100%', backgroundColor: '#fafafa', borderRadius: 8, overflow: 'hidden' },
  placeholder: { alignItems: 'center', justifyContent: 'center', backgroundColor: '#eee' },
  placeholderText: { color: '#888', fontSize: 13, padding: 16, textAlign: 'center' },
  bbox: {
    position: 'absolute',
    borderWidth: 2,
    borderRadius: 2,
  },
  bboxLabel: {
    position: 'absolute',
    top: -18,
    left: -2,
    color: 'white',
    paddingHorizontal: 4,
    paddingVertical: 1,
    fontSize: 10,
    fontWeight: '600',
  },
  loadingRow:  { flexDirection: 'row', alignItems: 'center', marginTop: 16 },
  loadingText: { marginLeft: 8, color: '#555' },
  errorText:   { color: '#c22', marginTop: 12, fontSize: 13 },
  listSection: { marginTop: 16 },
  sectionHeading: { fontSize: 14, fontWeight: '600', marginBottom: 8 },
  empty:       { color: '#888', fontSize: 13, fontStyle: 'italic' },
  listRow: {
    flexDirection: 'row', alignItems: 'center',
    paddingVertical: 6, borderBottomWidth: 1, borderBottomColor: '#eee',
  },
  swatch:     { width: 12, height: 12, borderRadius: 2, marginRight: 8 },
  listLabel:  { flex: 1, fontSize: 14 },
  listConf:   { width: 48, textAlign: 'right', fontSize: 13, color: '#444' },
  listBbox:   { width: 140, textAlign: 'right', fontSize: 11, color: '#888', fontFamily: 'monospace' },
  secondaryBtn: {
    marginTop: 24, paddingVertical: 12, paddingHorizontal: 24, borderRadius: 10,
    borderWidth: 1, borderColor: '#1f6feb', alignItems: 'center',
  },
  secondaryBtnLabel: { color: '#1f6feb', fontSize: 16, fontWeight: '600' },
});
