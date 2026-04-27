import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { NativeStackScreenProps } from '@react-navigation/native-stack';

import { RootStackParamList } from '../../App';

type Props = NativeStackScreenProps<RootStackParamList, 'Home'>;

/**
 * HomeScreen — single primary CTA that matches PRD §6 UC-01 step 1 verbatim.
 *
 * AC-17: the button label MUST read "방 사진 하나로 가구 추천" exactly.
 *
 * UC-02-wishlist FR-19 / AC-48: an additive "위시리스트 보기" button opens
 * the wishlist screen. The primary CTA is untouched.
 */
export default function HomeScreen({ navigation }: Props) {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>AuthenticSelf</Text>
      <Text style={styles.subtitle}>나에게 꼭 맞는 가구를 추천받으세요.</Text>
      <Pressable
        testID="cta-photo-upload"
        style={({ pressed }) => [styles.cta, pressed && styles.ctaPressed]}
        onPress={() => navigation.navigate('Upload')}
      >
        <Text style={styles.ctaLabel}>방 사진 하나로 가구 추천</Text>
      </Pressable>
      <Pressable
        testID="btn-open-wishlist"
        style={({ pressed }) => [styles.secondary, pressed && styles.ctaPressed]}
        onPress={() => navigation.navigate('Wishlist')}
      >
        <Text style={styles.secondaryLabel}>위시리스트 보기</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  title:     { fontSize: 32, fontWeight: '700', marginBottom: 8 },
  subtitle:  { fontSize: 16, color: '#555', marginBottom: 32 },
  cta:       {
    backgroundColor: '#1f6feb',
    paddingVertical: 16,
    paddingHorizontal: 24,
    borderRadius: 12,
    minWidth: 260,
    alignItems: 'center',
  },
  ctaPressed: { opacity: 0.8 },
  ctaLabel:   { color: 'white', fontSize: 16, fontWeight: '600' },
  secondary: {
    marginTop: 16,
    paddingVertical: 14,
    paddingHorizontal: 24,
    borderRadius: 12,
    minWidth: 260,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#1f6feb',
    backgroundColor: '#fff',
  },
  secondaryLabel: { color: '#1f6feb', fontSize: 15, fontWeight: '600' },
});
