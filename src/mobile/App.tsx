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
import ObjectsScreen from './src/screens/ObjectsScreen';
import LoginScreen from './src/screens/LoginScreen';
import AdminDashboardScreen from './src/screens/AdminDashboardScreen';
import { AuthProvider, useAuth } from './src/auth/AuthContext';
import type { PreferredStyle, Style } from './src/types/style';

export type RootStackParamList = {
  // Auth stack
  Login: undefined;
  // User stack
  Home: undefined;
  Upload: undefined;
  Analyzing: { roomId: string; photoUri?: string };
  StyleSelection: {
    roomId: string;
    aiDetectedStyle: Style | null;
    aiDetectedConfidence: number | null;
    photoUri?: string;
  };
  Objects: { roomId: string; photoUri?: string };
  // UC-01-recommendation FR-24 — `preferredStyle` is optional so the
  // existing Task-4 nav call (`replace('Recommendation', { roomId })`)
  // remains legal. When absent, the screen reads the echoed
  // `preferredStyle` from the `RecommendationResponse`.
  Recommendation: { roomId: string; preferredStyle?: PreferredStyle };
  // UC-02-wishlist FR-18 — reached from HomeScreen; takes no params.
  Wishlist: undefined;
  // Admin stack
  AdminDashboard: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();

/**
 * Root navigator — picks one of three Screen sets based on AuthContext.
 * Swapping conditional `<Stack.Screen>` children automatically resets the
 * navigation history, so Android back from a logged-in screen can never
 * land on the LoginScreen.
 */
function RootNavigator() {
  const { isLoggedIn, state } = useAuth();

  if (!isLoggedIn) {
    return (
      <Stack.Navigator initialRouteName="Login">
        <Stack.Screen name="Login" component={LoginScreen} options={{ title: '로그인' }} />
      </Stack.Navigator>
    );
  }

  if (state?.role === 'ADMIN') {
    return (
      <Stack.Navigator initialRouteName="AdminDashboard">
        <Stack.Screen
          name="AdminDashboard"
          component={AdminDashboardScreen}
          options={{ title: '관리자 대시보드' }}
        />
      </Stack.Navigator>
    );
  }

  return (
    <Stack.Navigator initialRouteName="Home">
      <Stack.Screen name="Home"           component={HomeScreen}           options={{ title: 'AuthenticSelf' }} />
      <Stack.Screen name="Upload"         component={UploadScreen}         options={{ title: '사진 업로드' }} />
      <Stack.Screen name="Analyzing"      component={AnalyzingScreen}      options={{ title: '분석 중' }} />
      <Stack.Screen name="StyleSelection" component={StyleSelectionScreen} options={{ title: '스타일 선택' }} />
      <Stack.Screen name="Recommendation" component={RecommendationScreen} options={{ title: '가구 추천' }} />
      <Stack.Screen name="Wishlist"       component={WishlistScreen}       options={{ title: '내 위시리스트' }} />
      <Stack.Screen name="Objects"        component={ObjectsScreen}        options={{ title: '객체 검출' }} />
    </Stack.Navigator>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <NavigationContainer>
        <StatusBar style="auto" />
        <RootNavigator />
      </NavigationContainer>
    </AuthProvider>
  );
}
