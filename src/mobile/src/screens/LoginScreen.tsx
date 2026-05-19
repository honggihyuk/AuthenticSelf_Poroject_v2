import React, { useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Platform,
  SafeAreaView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';

const ToastAndroid: typeof import('react-native').ToastAndroid | undefined =
  Platform.OS === 'android'
    ? (require('react-native') as typeof import('react-native')).ToastAndroid
    : undefined;

import { useAuth } from '../auth/AuthContext';
import { ApiError } from '../api/client';
import { Button, ScreenHeader } from '../components';
import { colors, radii, spacing, typography } from '../theme';

function showToast(msg: string): void {
  if (Platform.OS === 'android' && ToastAndroid) {
    ToastAndroid.show(msg, ToastAndroid.SHORT);
  } else {
    Alert.alert('', msg);
  }
}

/**
 * Demo login screen. Validates against the backend's hardcoded
 * {@code admin/1234} and {@code user/1234} credentials. On success the
 * AuthContext transition causes App.tsx to swap the stack to UserStack
 * or AdminStack — there is no explicit post-login navigation here.
 */
export default function LoginScreen() {
  const { login } = useAuth();
  const [username, setUsername] = useState<string>('');
  const [password, setPassword] = useState<string>('');
  const [busy, setBusy]         = useState<boolean>(false);

  const submit = async () => {
    if (busy) return;
    setBusy(true);
    try {
      await login(username.trim(), password);
    } catch (err) {
      const code = err instanceof ApiError ? err.body?.errorCode : undefined;
      if (code === 'MISSING_FIELDS') {
        showToast('아이디와 비밀번호를 입력해주세요.');
      } else if (code === 'INVALID_CREDENTIALS') {
        showToast('아이디 또는 비밀번호가 올바르지 않습니다.');
      } else {
        showToast('로그인에 실패했습니다. 잠시 후 다시 시도해주세요.');
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe} testID="login-screen">
      <ScreenHeader
        eyebrow="AUTHENTICSELF"
        title="로그인"
        subtitle="데모용 계정으로 접속해주세요."
      />

      <View style={styles.body}>
        <View style={styles.field}>
          <Text style={styles.label}>아이디</Text>
          <TextInput
            testID="input-username"
            style={styles.input}
            value={username}
            onChangeText={setUsername}
            autoCapitalize="none"
            autoCorrect={false}
            placeholder="admin / user"
            placeholderTextColor={colors.textFaint}
          />
        </View>

        <View style={styles.field}>
          <Text style={styles.label}>비밀번호</Text>
          <TextInput
            testID="input-password"
            style={styles.input}
            value={password}
            onChangeText={setPassword}
            secureTextEntry
            placeholder="1234"
            placeholderTextColor={colors.textFaint}
            onSubmitEditing={submit}
          />
        </View>

        <View style={styles.hintBox}>
          <Text style={styles.hint}>관리자: admin / 1234</Text>
          <Text style={styles.hint}>이용자: user / 1234</Text>
        </View>
      </View>

      <View style={styles.footer}>
        {busy ? (
          <View style={styles.busy} testID="login-busy">
            <ActivityIndicator color={colors.primary} />
          </View>
        ) : (
          <Button
            testID="btn-login"
            label="로그인"
            variant="primary"
            size="lg"
            fullWidth
            onPress={submit}
          />
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  body: {
    flex: 1,
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.md,
  },
  field: { marginBottom: spacing.md },
  label: {
    ...typography.bodyM,
    color: colors.textSecondary,
    marginBottom: spacing.xs,
  },
  input: {
    ...typography.bodyL,
    color: colors.textPrimary,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    height: 48,
  },
  hintBox: {
    marginTop: spacing.md,
    padding: spacing.sm,
    backgroundColor: colors.surfaceAlt,
    borderRadius: radii.md,
  },
  hint: { ...typography.caption, color: colors.textMuted },
  footer: {
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.xl,
  },
  busy: { height: 56, alignItems: 'center', justifyContent: 'center' },
});
