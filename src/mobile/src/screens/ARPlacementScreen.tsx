/**
 * ARPlacementScreen (AR-furniture-placement FR-5..FR-10 / AC-17..AC-32).
 *
 * Renders a WebView-hosted <model-viewer> scene that lets the user
 * preview a recommended furniture item in AR (WebXR on supported
 * devices, interactive 3-D viewer otherwise). On "배치 완료" the
 * screen surfaces an Alert offering to add the item to the wishlist —
 * the actual API call goes through `addToWishlist` from
 * `src/mobile/src/api/wishlist.ts` (same helper UC-01's
 * RecommendationScreen uses, so toast copy is byte-identical per
 * FR-9 / AC-31).
 *
 * Capability detection is try-and-fallback per D-3: if the scene
 * posts `{event:'error', errorCode:'AR_NOT_SUPPORTED'}`, the screen
 * hides the WebView and renders a Korean fallback panel (AC-26 /
 * AC-27).
 *
 * Teardown (FR-10 / AC-29) sends an `unload` inbound on unmount and
 * clears the WebView ref — `jest.getTimerCount() === 0` afterwards.
 */

import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  Alert,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';

import { colors, radii, spacing, typography } from '../theme';

// react-native-web does not re-export ToastAndroid, so a static import breaks
// the web bundle. Guarded require keeps the Android path identical while the
// web path simply leaves the symbol undefined (Platform.OS check below skips it).
const ToastAndroid: typeof import('react-native').ToastAndroid | undefined =
  Platform.OS === 'android'
    ? (require('react-native') as typeof import('react-native')).ToastAndroid
    : undefined;
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

import type { RootStackParamList } from '../../App';
import { ApiError } from '../api/client';
import { addToWishlist } from '../api/wishlist';
import type { FurnitureType, RecommendationItem } from '../api/spaces';
import ARWebView, { type ARWebViewHandle } from '../ar/ARWebView';
import {
  AR_BRIDGE_VERSION,
  OutboundARErrorCode,
  OutboundARMessage,
  emitArFurniturePlaced,
  encodeInbound,
} from '../ar/bridge';
import { settings } from '../settings';

// ---------------------------------------------------------------------------
// Constants — exported for tests (AC-26 / AC-31)
// ---------------------------------------------------------------------------

/**
 * Korean copy keyed by error code (FR-6 / AC-26).
 *
 * Exported so tests match by reference, not by substring.
 */
export const AR_ERROR_COPY: Record<OutboundARErrorCode, string> = {
  AR_NOT_SUPPORTED: '이 기기에서는 AR 미리보기가 지원되지 않습니다.',
  AR_CAMERA_DENIED: '카메라 권한이 필요합니다. 설정에서 권한을 허용해주세요.',
  AR_MODEL_LOAD_FAILED: '3D 모델을 불러오지 못했습니다. 네트워크를 확인해주세요.',
  AR_SESSION_ERROR: 'AR 세션에 문제가 발생했습니다. 다시 시도해주세요.',
};

/**
 * Per-type default dimension tuples (FR-4 / AC-20).
 *
 * These are explicit approximations because `RecommendationItem` does
 * NOT carry catalog dimensions. A future task can replace this with a
 * `GET /api/v1/furniture/{id}` round-trip or a `furniture.model_url`
 * column on the recommendation payload — the bridge's
 * `roomDimensions` field survives unchanged.
 */
export const DEFAULT_DIMENSIONS_BY_TYPE: Record<
  FurnitureType,
  { widthM: number; lengthM: number; heightM: number }
> = {
  desk:     { widthM: 1.20, lengthM: 0.60, heightM: 0.74 },
  bed:      { widthM: 1.60, lengthM: 2.00, heightM: 0.45 },
  chair:    { widthM: 0.55, lengthM: 0.55, heightM: 0.90 },
  lighting: { widthM: 0.30, lengthM: 0.30, heightM: 1.50 },
};

/**
 * Per-type model URL table (D-2). Resolved at `load`-time; the
 * bundled asset path is platform-agnostic enough that
 * `<model-viewer>` accepts it via `file://` after webview resolution.
 *
 * Exported so tests can assert the four canonical filenames.
 */
export const MODEL_URL_BY_TYPE: Record<FurnitureType, string> = {
  desk:     'asset:///ar/models/desk.glb',
  bed:      'asset:///ar/models/bed.glb',
  chair:    'asset:///ar/models/chair.glb',
  lighting: 'asset:///ar/models/lighting.glb',
};

/**
 * Toast copy matching `RecommendationScreen.ItemCard.onAddToWishlist`
 * byte-for-byte (FR-9 / AC-31). If any of these strings drifts from
 * the ones in `RecommendationScreen.tsx`, AC-31 fails.
 */
export const WISHLIST_TOAST_COPY = {
  SUCCESS_NEW:          '위시리스트에 담았어요.',
  SUCCESS_EXISTS_ACTIVE: '이미 위시리스트에 있어요.',
  SUCCESS_EXISTS_PURCHASED: '이미 구매 완료로 표시된 항목이에요.',
  FAIL_API:             '위시리스트에 추가하지 못했어요.',
  FAIL_NETWORK:         '네트워크 오류로 추가하지 못했어요.',
} as const;

// ---------------------------------------------------------------------------
// Props + internal state
// ---------------------------------------------------------------------------

type Props = NativeStackScreenProps<RootStackParamList, 'ARPlacement'>;

type ARStatus = 'loading' | 'live' | 'placed' | 'cancelled' | 'error';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function showToast(msg: string): void {
  if (Platform.OS === 'android' && ToastAndroid) {
    ToastAndroid.show(msg, ToastAndroid.SHORT);
  } else {
    Alert.alert('', msg);
  }
}

/**
 * Resolve the placement.html bundled asset URL.
 *
 * Expo/Metro exposes assets through `asset:///...` on native; web dev
 * serves them at a relative path. Tests never hit this path (the
 * WebView is mocked), so the exact scheme is only loadbearing in
 * production — a future Expo-asset integration can replace this with
 * `Asset.fromModule(require('../../assets/ar/placement.html')).uri`.
 */
function resolvePlacementHtmlUri(): string {
  return 'asset:///ar/placement.html';
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

export default function ARPlacementScreen({ route, navigation }: Props) {
  const { roomId, item } = route.params;

  const [status, setStatus] = useState<ARStatus>('loading');
  const [errorCode, setErrorCode] = useState<OutboundARErrorCode | null>(null);

  const webviewRef = useRef<ARWebViewHandle | null>(null);

  // -------------------------------------------------------------
  // Build the load payload — memoised so the effect runs once.
  // -------------------------------------------------------------
  const loadPayload = useMemo(() => {
    const type = item.type;
    const dimensions = DEFAULT_DIMENSIONS_BY_TYPE[type] ?? null;
    // Phase A — prefer the per-row curated `modelUrl` (V8 migration); fall
    // back to the per-type bundled GLB placeholder when the catalog row has
    // no 3D asset assigned yet. Relative URLs (e.g. "/static/furniture/...")
    // are resolved against `settings.apiBaseUrl`.
    const itemModelUrl = item.modelUrl ?? null;
    const modelUrl = itemModelUrl
      ? (itemModelUrl.startsWith('http') || itemModelUrl.startsWith('asset:')
          ? itemModelUrl
          : `${settings.apiBaseUrl}${itemModelUrl}`)
      : MODEL_URL_BY_TYPE[type];
    return encodeInbound({
      bridgeVersion: AR_BRIDGE_VERSION,
      event: 'load',
      furnitureId: item.furnitureId,
      modelUrl,
      roomDimensions: dimensions,
      // v1: placeholder GLBs carry their own baked material — no tint.
      colorHex: null,
    });
  }, [item.furnitureId, item.type]);

  // -------------------------------------------------------------
  // On mount — transition loading -> live once the WebView is up.
  // Actual HTML load is fire-and-forget; the scene acknowledges via
  // the `ar-status=session-started` event, but for the RN state
  // machine we flip to 'live' as soon as the inbound is sent.
  // -------------------------------------------------------------
  useEffect(() => {
    setStatus('live');
    // Inject the load message. The ARWebView ref is populated
    // synchronously during the first render via forwardRef's
    // imperative handle. A micro-timeout would be defensive on old
    // RN versions but breaks AC-29's timer-count invariant — we
    // rely on the ref being present by useEffect time.
    try {
      webviewRef.current?.inject(loadPayload);
    } catch {
      // no-op — teardown cleanliness.
    }
  }, [loadPayload]);

  // -------------------------------------------------------------
  // FR-10 teardown — send unload inbound, clear ref. Must not
  // throw even if the WebView is already gone. No setTimeout /
  // setInterval anywhere in this hook path (AC-29).
  //
  // We capture `inject` into the effect's closure at mount time so
  // the unmount cleanup has a stable function to call: React
  // detaches child refs before parent cleanup fires, so
  // `webviewRef.current` is null by then.
  // -------------------------------------------------------------
  useEffect(() => {
    const handle = webviewRef.current;
    const capturedInject = handle ? handle.inject.bind(handle) : null;
    return () => {
      try {
        const unloadJson = encodeInbound({
          bridgeVersion: AR_BRIDGE_VERSION,
          event: 'unload',
        });
        capturedInject?.(unloadJson);
      } catch {
        // Swallow — teardown must not throw.
      }
      webviewRef.current = null;
    };
  }, []);

  // -------------------------------------------------------------
  // Wishlist-add helper — reused logic / copy from
  // RecommendationScreen.ItemCard.onAddToWishlist (FR-9 / AC-31).
  // -------------------------------------------------------------
  const addSelectionToWishlist = useCallback(
    async (chosenItem: RecommendationItem) => {
      try {
        const resp = await addToWishlist({
          baseUrl:     settings.apiBaseUrl,
          userId:      settings.userId,
          furnitureId: chosenItem.furnitureId,
          category:    chosenItem.type,
          price:       chosenItem.price,
        });
        if (resp.alreadyExists) {
          if (resp.status === 'Purchased') {
            showToast(WISHLIST_TOAST_COPY.SUCCESS_EXISTS_PURCHASED);
          } else {
            showToast(WISHLIST_TOAST_COPY.SUCCESS_EXISTS_ACTIVE);
          }
        } else {
          showToast(WISHLIST_TOAST_COPY.SUCCESS_NEW);
        }
      } catch (e) {
        if (e instanceof ApiError) {
          const code = e.body?.errorCode;
          if (code === 'FURNITURE_NOT_FOUND' || code === 'USER_NOT_FOUND') {
            showToast(WISHLIST_TOAST_COPY.FAIL_API);
            return;
          }
        }
        showToast(WISHLIST_TOAST_COPY.FAIL_NETWORK);
      }
    },
    [],
  );

  // -------------------------------------------------------------
  // Outbound message handlers (FR-6 / FR-7 / FR-8)
  // -------------------------------------------------------------
  const handlePlaced = useCallback(
    (msg: OutboundARMessage & { event: 'placed' }) => {
      setStatus('placed');
      // Analytics fires exactly once per placed event (AC-30).
      emitArFurniturePlaced(roomId, item.furnitureId, msg.pose);

      const dismiss = () => {
        // Returning to Recommendation is cheaper than navigate because
        // the stack typically reads Home > Recommendation > ARPlacement,
        // so goBack pops straight back to the list (FR-7 note).
        navigation.goBack();
      };

      Alert.alert(
        '배치 완료',
        '위시리스트에 추가하시겠습니까?',
        [
          {
            text: '위시리스트에 추가',
            onPress: () => {
              void addSelectionToWishlist(item);
              dismiss();
            },
          },
          {
            text: '닫기',
            style: 'cancel',
            onPress: dismiss,
          },
        ],
      );
    },
    [addSelectionToWishlist, item, navigation, roomId],
  );

  const handleCancelled = useCallback(() => {
    setStatus('cancelled');
    navigation.goBack();
  }, [navigation]);

  const handleError = useCallback((code: OutboundARErrorCode) => {
    setErrorCode(code);
    setStatus('error');
  }, []);

  const onMessage = useCallback(
    (msg: OutboundARMessage) => {
      switch (msg.event) {
        case 'placed':
          handlePlaced(msg);
          break;
        case 'cancelled':
          handleCancelled();
          break;
        case 'error':
          handleError(msg.errorCode);
          break;
      }
    },
    [handlePlaced, handleCancelled, handleError],
  );

  const onDecodeError = useCallback(
    (_raw: string, _err: Error) => {
      // A malformed scene payload surfaces as AR_SESSION_ERROR to the
      // user — the scene contract is broken, but we don't crash.
      handleError('AR_SESSION_ERROR');
    },
    [handleError],
  );

  // -------------------------------------------------------------
  // Render
  // -------------------------------------------------------------
  if (status === 'error' && errorCode) {
    return (
      <View style={styles.fallbackContainer} testID="ar-fallback">
        <Text style={styles.fallbackText}>{AR_ERROR_COPY[errorCode]}</Text>
        <Pressable
          testID="btn-ar-back"
          style={styles.backBtn}
          accessibilityLabel="돌아가기"
          accessibilityRole="button"
          onPress={() => navigation.goBack()}
        >
          <Text style={styles.backBtnLabel}>돌아가기</Text>
        </Pressable>
      </View>
    );
  }

  // 'loading' | 'live' | 'placed' | 'cancelled' — the WebView stays
  // mounted during the Alert flow so the scene's canvas doesn't flash
  // to black before goBack finishes.
  return (
    <View style={styles.container}>
      <ARWebView
        ref={webviewRef}
        sourceUri={resolvePlacementHtmlUri()}
        onMessage={onMessage}
        onDecodeError={onDecodeError}
        testID="ar-webview"
      />
    </View>
  );
}

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  // AR scene stays dark for immersion regardless of app theme.
  container: { flex: 1, backgroundColor: '#111' },
  fallbackContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.xl,
    backgroundColor: colors.background,
  },
  fallbackText: {
    ...typography.titleM,
    color: colors.textPrimary,
    textAlign: 'center',
    marginBottom: spacing.lg,
  },
  backBtn: {
    backgroundColor: colors.primary,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.lg,
    borderRadius: radii.md,
    minHeight: 48,
    minWidth: 160,
    alignItems: 'center',
    justifyContent: 'center',
  },
  backBtnLabel: { color: colors.onPrimary, fontSize: 15, fontWeight: '600' },
});
