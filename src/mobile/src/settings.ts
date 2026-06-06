import Constants from 'expo-constants';
import { Platform } from 'react-native';

const BACKEND_PORT = 18080;

const configuredBaseUrl =
  (Constants.expoConfig?.extra?.apiBaseUrl as string | undefined) ??
  `http://localhost:${BACKEND_PORT}`;

// On web, when the page is loaded from a non-localhost host (e.g. a phone
// hitting the dev PC's LAN IP), the configured `localhost` would point back at
// the *client* device. Rewrite to the same hostname the page came from so
// remote browsers reach the backend on the dev machine.
function resolveWebBaseUrl(): string {
  if (Platform.OS !== 'web' || typeof window === 'undefined') return configuredBaseUrl;
  const host = window.location.hostname;
  if (host === 'localhost' || host === '127.0.0.1' || host === '') return configuredBaseUrl;
  return `${window.location.protocol}//${host}:${BACKEND_PORT}`;
}

/**
 * Runtime app settings. `userId` is intentionally mutable — AuthContext
 * writes the logged-in user's id here on login and clears it on logout, so
 * the existing screens that read `settings.userId` keep working without a
 * per-screen refactor. Screens that mount only inside UserStack/AdminStack
 * always observe a non-empty value.
 */
export const settings = {
  apiBaseUrl: resolveWebBaseUrl(),
  userId: '' as string,
  // JWT mirrored here by AuthContext on login (cleared on logout) so the api
  // layer can attach `Authorization: Bearer <token>` without a per-screen
  // refactor — same rationale as `userId` above. Backend's JwtAuthFilter
  // requires this header on every /api/v1/** call except login.
  token: '' as string,
};
