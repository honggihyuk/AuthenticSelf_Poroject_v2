import React, { useMemo, useState } from 'react';
import {
  View, Text, Pressable, StyleSheet, ScrollView, Alert,
} from 'react-native';
import { NativeStackScreenProps } from '@react-navigation/native-stack';

import { RootStackParamList } from '../../App';
import { settings } from '../settings';
import { setPreferredStyle, ApiError } from '../api/client';
import {
  PREFERRED_STYLES,
  PREFERRED_STYLE_LABELS,
  STYLE_DISPLAY_NAMES,
  PreferredStyle,
  Style,
} from '../types/style';

type Props = NativeStackScreenProps<RootStackParamList, 'StyleSelection'>;

/**
 * Style-selection screen (Task-4 FR-18 / AC-28 / AC-29 / AC-30).
 *
 * - Six radio-like options from `PREFERRED_STYLES`.
 * - When `aiDetectedStyle` is non-null, renders an "AI가 감지한 스타일: X (YY%)"
 *   badge next to the corresponding row (AC-28).
 * - When `aiDetectedStyle` is null, shows fallback copy and pre-selects
 *   `CURRENT` (AC-29).
 * - On Next button tap, calls `setPreferredStyle` and navigates to
 *   `Recommendation` on success (AC-30).
 */
export default function StyleSelectionScreen({ route, navigation }: Props) {
  const { roomId, aiDetectedStyle, aiDetectedConfidence } = route.params;

  // Pre-select: AI-detected style if present, otherwise CURRENT.
  const initialSelection: PreferredStyle = useMemo(() => {
    if (aiDetectedStyle) return aiDetectedStyle as PreferredStyle;
    return 'CURRENT';
  }, [aiDetectedStyle]);

  const [selected, setSelected] = useState<PreferredStyle>(initialSelection);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [inlineError, setInlineError] = useState<string | null>(null);

  const confidencePct =
    aiDetectedConfidence != null
      ? Math.round(aiDetectedConfidence * 100)
      : null;

  const submit = async () => {
    setSubmitting(true);
    setInlineError(null);
    try {
      await setPreferredStyle({
        baseUrl: settings.apiBaseUrl,
        userId:  settings.userId,
        roomId,
        preferredStyle: selected,
      });
      navigation.replace('Recommendation', { roomId });
    } catch (err) {
      const code =
        err instanceof ApiError ? err.body?.errorCode : undefined;
      const msg =
        code === 'INVALID_PREFERRED_STYLE'
          ? '지원하지 않는 스타일 값입니다.'
          : code === 'ANALYSIS_NOT_READY'
          ? '사진 분석이 아직 완료되지 않았습니다.'
          : code === 'SPACE_ACCESS_DENIED'
          ? '해당 공간에 접근 권한이 없습니다.'
          : '스타일 저장에 실패했습니다. 잠시 후 다시 시도해주세요.';
      setInlineError(msg);
      Alert.alert('저장 실패', msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.container} testID="style-selection-screen">
      <Text style={styles.title}>원하는 스타일을 선택하세요</Text>

      {aiDetectedStyle == null && (
        <Text testID="ai-fallback" style={styles.fallback}>
          AI 스타일 감지에 실패했습니다. 직접 선택해주세요.
        </Text>
      )}

      {PREFERRED_STYLES.map((style) => {
        const isSelected  = selected === style;
        const isAiPick    = aiDetectedStyle != null && aiDetectedStyle === style;
        const korean      = PREFERRED_STYLE_LABELS[style];
        const englishName =
          style === 'CURRENT' ? null : STYLE_DISPLAY_NAMES[style as Style];

        return (
          <Pressable
            key={style}
            testID={`style-option-${style}`}
            style={[styles.row, isSelected && styles.rowSelected]}
            onPress={() => setSelected(style)}
          >
            <View style={[styles.radio, isSelected && styles.radioSelected]} />
            <View style={styles.rowText}>
              <Text style={styles.rowLabel}>
                {korean}
                {englishName ? ` (${englishName})` : ''}
              </Text>
              {isAiPick && confidencePct != null && (
                <Text testID={`ai-badge-${style}`} style={styles.aiBadge}>
                  AI가 감지한 스타일: {englishName ?? korean} ({confidencePct}%)
                </Text>
              )}
              {isAiPick && confidencePct == null && (
                <Text testID={`ai-badge-${style}`} style={styles.aiBadge}>
                  AI가 감지한 스타일: {englishName ?? korean}
                </Text>
              )}
            </View>
          </Pressable>
        );
      })}

      {inlineError != null && (
        <Text testID="inline-error" style={styles.errorText}>{inlineError}</Text>
      )}

      <Pressable
        testID="btn-next"
        disabled={submitting}
        style={[styles.submit, submitting && { opacity: 0.6 }]}
        onPress={submit}
      >
        <Text style={styles.submitLabel}>다음</Text>
      </Pressable>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { padding: 20, paddingBottom: 80 },
  title:     { fontSize: 20, fontWeight: '700', marginBottom: 16 },
  fallback:  { fontSize: 13, color: '#666', marginBottom: 12 },
  row: {
    flexDirection: 'row', alignItems: 'flex-start',
    paddingVertical: 14, paddingHorizontal: 12,
    borderRadius: 10, borderWidth: 1, borderColor: '#ddd',
    marginBottom: 10, backgroundColor: '#fff',
  },
  rowSelected: { borderColor: '#1f6feb', backgroundColor: '#eef4ff' },
  radio: {
    width: 20, height: 20, borderRadius: 10,
    borderWidth: 2, borderColor: '#888', marginRight: 12, marginTop: 2,
  },
  radioSelected: {
    borderColor: '#1f6feb',
    backgroundColor: '#1f6feb',
  },
  rowText:   { flex: 1 },
  rowLabel:  { fontSize: 16, color: '#222' },
  aiBadge:   { fontSize: 12, color: '#1f6feb', marginTop: 4 },
  submit: {
    marginTop: 16, backgroundColor: '#1f6feb',
    paddingVertical: 14, borderRadius: 12, alignItems: 'center',
  },
  submitLabel: { color: 'white', fontSize: 16, fontWeight: '600' },
  errorText:   { color: '#c22', marginTop: 12, fontSize: 13 },
});
