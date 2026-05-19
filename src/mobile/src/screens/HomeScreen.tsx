import React from 'react';
import { SafeAreaView, StyleSheet, View } from 'react-native';
import { NativeStackScreenProps } from '@react-navigation/native-stack';

import { RootStackParamList } from '../../App';
import { useAuth } from '../auth/AuthContext';
import { Button, ScreenHeader } from '../components';
import { colors, spacing } from '../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'Home'>;

/**
 * HomeScreen — single primary CTA that matches PRD §6 UC-01 step 1 verbatim.
 *
 * AC-17: the button label MUST read "방 사진 하나로 가구 추천" exactly.
 * UC-02 AC-48: secondary "위시리스트 보기" navigates to the wishlist screen.
 */
export default function HomeScreen({ navigation }: Props) {
  const { state, logout } = useAuth();
  const subtitle = state?.name
    ? `${state.name}님, 사진 한 장이면 충분합니다.`
    : '사진 한 장이면 충분합니다.';
  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.container}>
        <ScreenHeader
          eyebrow="AUTHENTICSELF"
          title="당신의 방에 꼭 맞는 가구를 찾아드릴게요."
          subtitle={subtitle}
        />
        <View style={styles.actions}>
          <Button
            testID="cta-photo-upload"
            label="방 사진 하나로 가구 추천"
            variant="primary"
            size="lg"
            fullWidth
            onPress={() => navigation.navigate('Upload')}
          />
          <View style={styles.gap} />
          <Button
            testID="btn-open-wishlist"
            label="위시리스트 보기"
            variant="secondary"
            size="lg"
            fullWidth
            onPress={() => navigation.navigate('Wishlist')}
          />
          <View style={styles.gap} />
          <Button
            testID="btn-logout"
            label="로그아웃"
            variant="ghost"
            size="md"
            fullWidth
            onPress={logout}
          />
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  container: {
    flex: 1,
    justifyContent: 'space-between',
    paddingBottom: spacing.xl,
  },
  actions: {
    paddingHorizontal: spacing.lg,
  },
  gap: { height: spacing.sm },
});
