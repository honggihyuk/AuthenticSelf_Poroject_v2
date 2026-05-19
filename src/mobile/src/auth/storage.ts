import { Platform } from 'react-native';

/**
 * Tiny persistence shim used only by AuthContext.
 *
 * The PoC ships without `@react-native-async-storage/async-storage` to keep
 * the dependency tree light. On web we use `window.localStorage` (the user
 * actually tests in Safari), on native we fall back to an in-memory store —
 * good enough for a demo, since native screens have their own login flow.
 */

const memoryStore: Record<string, string> = {};

function canUseLocalStorage(): boolean {
  return Platform.OS === 'web'
      && typeof window !== 'undefined'
      && typeof window.localStorage !== 'undefined';
}

export function loadItem(key: string): string | null {
  if (canUseLocalStorage()) {
    try { return window.localStorage.getItem(key); } catch { return null; }
  }
  return memoryStore[key] ?? null;
}

export function saveItem(key: string, value: string): void {
  if (canUseLocalStorage()) {
    try { window.localStorage.setItem(key, value); return; } catch { /* fall through */ }
  }
  memoryStore[key] = value;
}

export function removeItem(key: string): void {
  if (canUseLocalStorage()) {
    try { window.localStorage.removeItem(key); return; } catch { /* fall through */ }
  }
  delete memoryStore[key];
}
