import Constants from 'expo-constants';

/**
 * Dev-only settings module. Real authentication is out of scope for UC-01
 * (see spec §3 Preconditions) — we ship a static `X-User-Id` stub so the
 * backend can identify the uploader.
 */
export const settings = {
  apiBaseUrl:
    (Constants.expoConfig?.extra?.apiBaseUrl as string | undefined) ??
    'http://localhost:18080',
  /** Dev stub — replaced by real auth in a later task. */
  userId: 'u_dev',
};
