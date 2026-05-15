import React, { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  AppState,
  AppStateStatus,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { NativeStackScreenProps } from '@react-navigation/native-stack';

import { RootStackParamList } from '../../App';
import { settings } from '../settings';
import { getSpace, SpaceState } from '../api/client';
import {
  POLL_BUDGET_MS,
  POLL_INITIAL_MS,
  delayForAttempt,
} from '../api/pollSchedule';
import { Button } from '../components';
import { colors, spacing, typography } from '../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'Analyzing'>;

type PollPhase = 'polling' | 'timeout' | 'failed';

/**
 * Polls `GET /api/v1/spaces/{roomId}` with exponential backoff (Task-4 FR-19).
 *
 * - On `status='ANALYZED'`: navigate to `StyleSelection` with the AI-detected
 *   style + confidence.
 * - On `status='FAILED'`: show inline error.
 * - After the 60 s budget elapses while still `PENDING_ANALYSIS`: show timeout
 *   UI with retry / re-upload buttons. Crucially, does NOT mark the row FAILED
 *   server-side.
 * - Cleaned up on screen unmount AND on app-state transition to background.
 */
export default function AnalyzingScreen({ route, navigation }: Props) {
  const { roomId, photoUri } = route.params;
  const [phase, setPhase] = useState<PollPhase>('polling');
  const [attempt, setAttempt] = useState<number>(0);

  // Refs for cancellation without stale closures.
  const cancelledRef = useRef<boolean>(false);
  const timerRef     = useRef<ReturnType<typeof setTimeout> | null>(null);
  const startedAtRef = useRef<number>(Date.now());
  const attemptsRef  = useRef<number>(0);

  useEffect(() => {
    startedAtRef.current = Date.now();
    attemptsRef.current  = 0;
    cancelledRef.current = false;

    const run = () => {
      if (cancelledRef.current) return;
      attemptsRef.current += 1;
      setAttempt(attemptsRef.current);

      getSpace({
        baseUrl: settings.apiBaseUrl,
        userId:  settings.userId,
        roomId,
      })
        .then((state: SpaceState) => {
          if (cancelledRef.current) return;

          if (state.status === 'ANALYZED') {
            navigation.replace('StyleSelection', {
              roomId,
              aiDetectedStyle:      state.style,
              aiDetectedConfidence: state.styleConfidence,
              photoUri,
            });
            return;
          }
          if (state.status === 'FAILED') {
            setPhase('failed');
            return;
          }
          // still PENDING — schedule next poll
          scheduleNext();
        })
        .catch(() => {
          // Network glitch — keep polling silently within the budget.
          scheduleNext();
        });
    };

    const scheduleNext = () => {
      if (cancelledRef.current) return;
      const elapsed = Date.now() - startedAtRef.current;
      if (elapsed >= POLL_BUDGET_MS) {
        setPhase('timeout');
        return;
      }
      const delay = delayForAttempt(attemptsRef.current);
      const remaining = POLL_BUDGET_MS - elapsed;
      const actual = Math.min(delay, Math.max(0, remaining));
      timerRef.current = setTimeout(run, actual);
    };

    // First poll fires immediately.
    const initialDelay = attemptsRef.current === 0 ? 0 : POLL_INITIAL_MS;
    timerRef.current = setTimeout(run, initialDelay);

    // AppState — cancel polling when the app backgrounds.
    const sub = AppState.addEventListener('change', (next: AppStateStatus) => {
      if (next !== 'active') {
        cancelPolling();
      }
    });

    return () => {
      cancelPolling();
      sub.remove();
    };
    function cancelPolling() {
      cancelledRef.current = true;
      if (timerRef.current != null) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId]);

  const restart = () => {
    setPhase('polling');
    startedAtRef.current = Date.now();
    attemptsRef.current  = 0;
    cancelledRef.current = false;
    setAttempt((n) => n + 1);
    getSpace({
      baseUrl: settings.apiBaseUrl,
      userId:  settings.userId,
      roomId,
    })
      .then((state: SpaceState) => {
        if (state.status === 'ANALYZED') {
          navigation.replace('StyleSelection', {
            roomId,
            aiDetectedStyle:      state.style,
            aiDetectedConfidence: state.styleConfidence,
          });
        } else if (state.status === 'FAILED') {
          setPhase('failed');
        }
      })
      .catch(() => { /* swallow; user can tap retry again */ });
  };

  if (phase === 'timeout') {
    return (
      <View style={styles.container} testID="analyzing-timeout">
        <Text style={styles.headline}>분석 시간 초과</Text>
        <Text style={styles.body}>
          분석이 예상보다 오래 걸리고 있습니다. 다시 시도하거나 사진을 다시 업로드해주세요.
        </Text>
        <Button
          testID="btn-retry"
          label="다시 시도"
          variant="primary"
          size="md"
          onPress={restart}
        />
        <View style={styles.gap} />
        <Button
          testID="btn-reupload"
          label="다시 업로드"
          variant="secondary"
          size="md"
          onPress={() => navigation.replace('Upload')}
        />
      </View>
    );
  }

  if (phase === 'failed') {
    return (
      <View style={styles.container} testID="analyzing-failed">
        <Text style={styles.headline}>분석 실패</Text>
        <Text style={styles.body}>
          분석에 실패했습니다. 다른 사진으로 다시 시도해주세요.
        </Text>
        <Button
          testID="btn-reupload"
          label="다시 업로드"
          variant="primary"
          size="md"
          onPress={() => navigation.replace('Upload')}
        />
      </View>
    );
  }

  return (
    <View style={styles.container} testID="analyzing-polling">
      <ActivityIndicator size="large" color={colors.primary} />
      <Text style={styles.text}>방을 분석하고 있습니다…</Text>
      <Text testID="room-id" style={styles.roomId}>roomId: {roomId}</Text>
      <Text testID="attempt-count" style={styles.roomId}>시도 {attempt}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.lg,
    backgroundColor: colors.background,
  },
  text: { ...typography.bodyL, marginTop: spacing.md, color: colors.textPrimary },
  roomId: { ...typography.caption, color: colors.textMuted, marginTop: spacing.xs },
  headline: { ...typography.displayM, marginBottom: spacing.sm },
  body: {
    ...typography.bodyM,
    color: colors.textMuted,
    textAlign: 'center',
    marginBottom: spacing.lg,
    maxWidth: 360,
  },
  gap: { height: spacing.sm },
});
