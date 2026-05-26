import React, { useState } from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';

import { colors, radii, typography } from '../theme';
import type { AiDetection } from '../api/admin';

/**
 * UC-ML-PERSIST FR-11 / FR-12 — bounding-box overlay for the admin
 * Space-detail debug view.
 *
 * Geometry is a direct clone of the proven `ObjectsScreen` overlay
 * (`ObjectsScreen.tsx:60-117`): each box is positioned with the absolute-pixel
 * `bbox` scaled by the displayed image size relative to `imageWidth` /
 * `imageHeight`. Each box is labelled with `label` + `confidence%`.
 *
 * FR-12 — when there are no detections (NULL `ai_detections`, or a room where
 * YOLO found nothing) the component renders the photo (or a placeholder) with
 * no boxes plus an empty-state hint. It never crashes on missing data.
 */

const BOX_COLORS = ['#e6194B', '#3cb44b', '#4363d8', '#f58231', '#911eb4', '#42d4f4', '#f032e6'];

function colorFor(label: string): string {
  let h = 0;
  for (let i = 0; i < label.length; i++) h = (h * 31 + label.charCodeAt(i)) >>> 0;
  return BOX_COLORS[h % BOX_COLORS.length];
}

export type DetectionOverlayProps = {
  photoUri?: string | null;
  imageWidth: number;
  imageHeight: number;
  detections: AiDetection[];
};

export function DetectionOverlay({
  photoUri,
  imageWidth,
  imageHeight,
  detections,
}: DetectionOverlayProps) {
  const [containerW, setContainerW] = useState<number>(0);

  // Clone of ObjectsScreen's scale math (lines 59-63). Guard against zero/NaN
  // image dims (FR-12 backward-compat) so the overlay never divides by zero.
  const safeW = imageWidth > 0 ? imageWidth : 1;
  const safeH = imageHeight > 0 ? imageHeight : 1;
  const displayWidth = containerW > 0 ? containerW : 0;
  const aspect = imageHeight > 0 ? imageHeight / safeW : 0.75;
  const displayHeight = displayWidth * aspect;
  const scaleX = displayWidth / safeW;
  const scaleY = displayHeight / safeH;

  const hasDetections = Array.isArray(detections) && detections.length > 0;

  return (
    <View>
      <View
        style={styles.imageWrap}
        onLayout={(e) => setContainerW(e.nativeEvent.layout.width)}
        testID="detection-overlay"
      >
        {photoUri ? (
          <Image
            testID="detection-photo"
            source={{ uri: photoUri }}
            style={{ width: displayWidth, height: displayHeight, backgroundColor: colors.surfaceAlt }}
            resizeMode="cover"
          />
        ) : (
          <View
            testID="detection-photo-placeholder"
            style={[styles.placeholder, { width: displayWidth, height: displayHeight || 240 }]}
          >
            <Text style={styles.placeholderText}>사진 미리보기를 사용할 수 없습니다 (좌표만 표시).</Text>
          </View>
        )}

        {hasDetections &&
          detections.map((d, idx) => {
            const [x1, y1, x2, y2] = d.bbox;
            const left = x1 * scaleX;
            const top = y1 * scaleY;
            const width = (x2 - x1) * scaleX;
            const height = (y2 - y1) * scaleY;
            const color = colorFor(d.label);
            return (
              <View
                key={`box-${idx}`}
                testID={`bbox-${idx}`}
                pointerEvents="none"
                style={[styles.bbox, { left, top, width, height, borderColor: color }]}
              >
                <Text style={[styles.bboxLabel, { backgroundColor: color }]}>
                  {d.label} {Math.round(d.confidence * 100)}%
                </Text>
              </View>
            );
          })}
      </View>

      {!hasDetections && (
        <Text testID="detection-empty" style={styles.empty}>
          검출된 객체가 없습니다.
        </Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  imageWrap: {
    position: 'relative',
    width: '100%',
    backgroundColor: colors.surfaceAlt,
    borderRadius: radii.md,
    overflow: 'hidden',
  },
  placeholder: {
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.surfaceAlt,
  },
  placeholderText: {
    ...typography.bodyM,
    color: colors.textMuted,
    padding: 12,
    textAlign: 'center',
  },
  bbox: {
    position: 'absolute',
    borderWidth: 2,
    borderRadius: 2,
  },
  bboxLabel: {
    position: 'absolute',
    top: -18,
    left: -2,
    color: colors.textInverse,
    paddingHorizontal: 4,
    paddingVertical: 1,
    fontSize: 10,
    fontWeight: '600',
  },
  empty: {
    ...typography.bodyM,
    color: colors.textMuted,
    fontStyle: 'italic',
    marginTop: 8,
  },
});
