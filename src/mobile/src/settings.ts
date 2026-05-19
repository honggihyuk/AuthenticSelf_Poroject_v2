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

export const settings = {
  apiBaseUrl: resolveWebBaseUrl(),
  /** Dev stub — replaced by real auth in a later task. */
  userId: 'u_dev',
};
