import React, { useMemo, useState } from 'react';
import {
  Alert,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
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
import { Button, ScreenHeader } from '../components';
import { colors, radii, spacing, typography } from '../theme';

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
  const { roomId, aiDetectedStyle, aiDetectedConfidence, photoUri } = route.params;

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
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.container}
      testID="style-selection-screen"
    >
      <ScreenHeader
        eyebrow="STEP 2"
        title="원하는 스타일을 선택하세요"
        subtitle="추천에 반영할 분위기를 골라주세요."
      />

      <View style={styles.list}>
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
              accessibilityRole="radio"
              accessibilityState={{ selected: isSelected }}
              style={({ pressed }) => [
                styles.row,
                isSelected && styles.rowSelected,
                pressed && !isSelected && styles.rowPressed,
              ]}
              onPress={() => setSelected(style)}
            >
              <View style={[styles.radio, isSelected && styles.radioSelected]}>
                {isSelected && <View style={styles.radioDot} />}
              </View>
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
      </View>

      <View style={styles.actions}>
        <Button
          testID="btn-objects"
          label="객체 검출 보기"
          variant="ghost"
          size="md"
          fullWidth
          onPress={() => navigation.navigate('Objects', { roomId, photoUri })}
        />
        <View style={styles.gap} />
        <Button
          testID="btn-next"
          label="다음"
          variant="primary"
          size="lg"
          fullWidth
          disabled={submitting}
          onPress={submit}
        />
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1, backgroundColor: colors.background },
  container: { paddingBottom: spacing.xxl },

  list: { paddingHorizontal: spacing.lg, marginTop: spacing.md },

  fallback: {
    ...typography.caption,
    color: colors.textMuted,
    marginBottom: spacing.sm,
  },

  row: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.md,
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.border,
    marginBottom: spacing.xs,
    backgroundColor: colors.surface,
  },
  rowSelected: {
    borderColor: colors.primary,
    backgroundColor: colors.primarySoft,
  },
  rowPressed: { backgroundColor: colors.surfaceAlt },

  radio: {
    width: 22, height: 22, borderRadius: 11,
    borderWidth: 2, borderColor: colors.textMuted,
    marginRight: spacing.sm, marginTop: 2,
    alignItems: 'center', justifyContent: 'center',
  },
  radioSelected: { borderColor: colors.primary },
  radioDot: {
    width: 10, height: 10, borderRadius: 5,
    backgroundColor: colors.primary,
  },

  rowText: { flex: 1 },
  rowLabel: { ...typography.bodyL, color: colors.textPrimary },
  aiBadge: {
    ...typography.caption,
    color: colors.primary,
    marginTop: spacing.xxs,
  },

  errorText: {
    ...typography.bodyM,
    color: colors.danger,
    marginTop: spacing.sm,
  },

  actions: {
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.lg,
  },
  gap: { height: spacing.sm },
});
