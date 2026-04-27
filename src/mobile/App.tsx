import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { StatusBar } from 'expo-status-bar';

import HomeScreen from './src/screens/HomeScreen';
import UploadScreen from './src/screens/UploadScreen';
import AnalyzingScreen from './src/screens/AnalyzingScreen';
import StyleSelectionScreen from './src/screens/StyleSelectionScreen';
import RecommendationScreen from './src/screens/RecommendationScreen';
import WishlistScreen from './src/screens/WishlistScreen';
import ARPlacementScreen from './src/screens/ARPlacementScreen';
import type { RecommendationItem } from './src/api/spaces';
import type { PreferredStyle, Style } from './src/types/style';

export type RootStackParamList = {
  Home: undefined;
  Upload: undefined;
  Analyzing: { roomId: string };
  StyleSelection: {
    roomId: string;
    aiDetectedStyle: Style | null;
    aiDetectedConfidence: number | null;
  };
  // UC-01-recommendation FR-24 — `preferredStyle` is optional so the
  // existing Task-4 nav call (`replace('Recommendation', { roomId })`)
  // remains legal. When absent, the screen reads the echoed
  // `preferredStyle` from the `RecommendationResponse`.
  Recommendation: { roomId: string; preferredStyle?: PreferredStyle };
  // UC-02-wishlist FR-18 — reached from HomeScreen; takes no params.
  Wishlist: undefined;
  // AR-furniture-placement FR-2 — reached from RecommendationScreen
  // (per-card "AR로 배치" button). Receives the selected RecommendationItem
  // plus the current roomId so the AR scene can load without a refetch.
  ARPlacement: { roomId: string; item: RecommendationItem };
};

const Stack = createNativeStackNavigator<RootStackParamList>();

export default function App() {
  return (
    <NavigationContainer>
      <StatusBar style="auto" />
      <Stack.Navigator initialRouteName="Home">
        <Stack.Screen name="Home"           component={HomeScreen}           options={{ title: 'AuthenticSelf' }} />
        <Stack.Screen name="Upload"         component={UploadScreen}         options={{ title: '사진 업로드' }} />
        <Stack.Screen name="Analyzing"      component={AnalyzingScreen}      options={{ title: '분석 중' }} />
        <Stack.Screen name="StyleSelection" component={StyleSelectionScreen} options={{ title: '스타일 선택' }} />
        <Stack.Screen name="Recommendation" component={RecommendationScreen} options={{ title: '가구 추천' }} />
        <Stack.Screen name="Wishlist"       component={WishlistScreen}       options={{ title: '내 위시리스트' }} />
        <Stack.Screen name="ARPlacement"    component={ARPlacementScreen}    options={{ title: 'AR 배치' }} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
