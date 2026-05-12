import React, { useEffect, useRef, useState } from 'react';
import {
  View, Text, ActivityIndicator, Pressable, StyleSheet, AppState,
  AppStateStatus,
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
    // Kick off the cycle again — call getSpace immediately.
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
        <Pressable testID="btn-retry" style={styles.primaryBtn} onPress={restart}>
          <Text style={styles.primaryBtnLabel}>다시 시도</Text>
        </Pressable>
        <Pressable
          testID="btn-reupload"
          style={styles.secondaryBtn}
          onPress={() => navigation.replace('Upload')}
        >
          <Text style={styles.secondaryBtnLabel}>다시 업로드</Text>
        </Pressable>
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
        <Pressable
          testID="btn-reupload"
          style={styles.primaryBtn}
          onPress={() => navigation.replace('Upload')}
        >
          <Text style={styles.primaryBtnLabel}>다시 업로드</Text>
        </Pressable>
      </View>
    );
  }

  return (
    <View style={styles.container} testID="analyzing-polling">
      <ActivityIndicator size="large" />
      <Text style={styles.text}>방을 분석하고 있습니다…</Text>
      <Text testID="room-id" style={styles.roomId}>roomId: {roomId}</Text>
      <Text testID="attempt-count" style={styles.roomId}>시도 {attempt}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  text:      { marginTop: 16, fontSize: 16 },
  roomId:    { marginTop: 8, fontSize: 12, color: '#666' },
  headline:  { fontSize: 20, fontWeight: '700', marginBottom: 12 },
  body:      { fontSize: 14, color: '#444', textAlign: 'center', marginBottom: 24 },
  primaryBtn: {
    backgroundColor: '#1f6feb', paddingVertical: 12, paddingHorizontal: 24,
    borderRadius: 10, marginBottom: 12, minWidth: 200, alignItems: 'center',
  },
  primaryBtnLabel: { color: 'white', fontSize: 16, fontWeight: '600' },
  secondaryBtn: {
    paddingVertical: 12, paddingHorizontal: 24, borderRadius: 10,
    borderWidth: 1, borderColor: '#1f6feb', minWidth: 200, alignItems: 'center',
  },
  secondaryBtnLabel: { color: '#1f6feb', fontSize: 16, fontWeight: '600' },
});
