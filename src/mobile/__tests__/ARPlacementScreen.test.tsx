/**
 * Tests for ARPlacementScreen (AR-furniture-placement AC-21..AC-32 / AC-39).
 *
 * The `ARWebView` wrapper is mocked — production-side `react-native-webview`
 * is never imported in Jest. The mock exposes a module-level `arEmit(raw)`
 * helper that synchronously invokes the last-registered `onMessage` prop;
 * the same mock captures the latest `inject(bridgeJson)` so FR-10 teardown
 * can be asserted.
 *
 * `addToWishlist` is mocked (same convention as RecommendationScreen.test.tsx).
 * `Alert.alert` is spied so we can tap buttons by calling the captured
 * `onPress` callbacks directly — byte-identical to WishlistScreen.test.tsx's
 * `confirmAlert` helper.
 */

import React from 'react';
import { Alert } from 'react-native';
import { act, fireEvent, render } from '@testing-library/react-native';

// ---------------------------------------------------------------------------
// Mocks — registered before the SUT imports resolve.
// ---------------------------------------------------------------------------

// The ARWebView mock. Holds the most recent onMessage callback and
// exposes it as a module-level emit() — tests drive state transitions
// by calling emit() synchronously instead of through the native bridge.
jest.mock('../src/ar/ARWebView', () => {
  const React = require('react');
  const { View } = require('react-native');

  // Module-scoped — reset in beforeEach.
  const state: {
    lastOnMessage: ((msg: unknown) => void) | null;
    lastOnDecodeError: ((raw: string, err: Error) => void) | null;
    injected: string[];
  } = {
    lastOnMessage: null,
    lastOnDecodeError: null,
    injected: [],
  };

  const ARWebViewMock = React.forwardRef(function ARWebViewMock(
    props: {
      onMessage: (msg: unknown) => void;
      onDecodeError?: (raw: string, err: Error) => void;
      testID?: string;
    },
    ref: React.Ref<{ inject: (s: string) => void }>,
  ) {
    state.lastOnMessage = props.onMessage;
    state.lastOnDecodeError = props.onDecodeError ?? null;
    React.useImperativeHandle(
      ref,
      () => ({
        inject(s: string) {
          state.injected.push(s);
        },
      }),
      [],
    );
    return React.createElement(View, { testID: props.testID ?? 'ar-webview' });
  });

  return {
    __esModule: true,
    default: ARWebViewMock,
    /** Test helper — reset both the on-message reference and the inject log. */
    __reset(): void {
      state.lastOnMessage = null;
      state.lastOnDecodeError = null;
      state.injected = [];
    },
    /** Synchronously fire the latest onMessage with a parsed bridge message. */
    __emit(msg: unknown): void {
      if (!state.lastOnMessage) throw new Error('ARWebView mock: no onMessage registered');
      state.lastOnMessage(msg);
    },
    /** Synchronously fire the latest onDecodeError. */
    __emitDecodeError(raw: string, err: Error): void {
      if (!state.lastOnDecodeError) throw new Error('ARWebView mock: no onDecodeError registered');
      state.lastOnDecodeError(raw, err);
    },
    /** All inject() calls in order. */
    __getInjected(): string[] {
      return state.injected.slice();
    },
  };
});

jest.mock('../src/api/wishlist', () => ({
  ...jest.requireActual('../src/api/wishlist'),
  addToWishlist: jest.fn(),
}));

// `emitArFurniturePlaced` lives on `../src/ar/bridge`. Tests spy on it
// via `setArAnalyticsSink` rather than rewriting the module — this
// keeps `decodeOutbound`'s real code path under test.

// ---------------------------------------------------------------------------
// Imports under test (after mocks).
// ---------------------------------------------------------------------------

import ARPlacementScreen, {
  AR_ERROR_COPY,
  WISHLIST_TOAST_COPY,
  DEFAULT_DIMENSIONS_BY_TYPE,
} from '../src/screens/ARPlacementScreen';
import { addToWishlist } from '../src/api/wishlist';
import { ApiError } from '../src/api/client';
import {
  AR_BRIDGE_VERSION,
  setArAnalyticsSink,
  type ArAnalyticsEvent,
} from '../src/ar/bridge';
import type { RecommendationItem } from '../src/api/spaces';

// Re-import the mocked module to pluck test helpers.
// eslint-disable-next-line @typescript-eslint/no-var-requires
const ARWebViewMockMod = require('../src/ar/ARWebView') as {
  __reset: () => void;
  __emit: (msg: unknown) => void;
  __emitDecodeError: (raw: string, err: Error) => void;
  __getInjected: () => string[];
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeItem(overrides: Partial<RecommendationItem> = {}): RecommendationItem {
  return {
    furnitureId: overrides.furnitureId ?? 'f_desk_001',
    name: overrides.name ?? 'Oslo Slim Desk',
    type: overrides.type ?? 'desk',
    price: overrides.price ?? 189000,
    imageUrl: overrides.imageUrl ?? null,
    fitScore: overrides.fitScore ?? 0.87,
    scoreBreakdown: overrides.scoreBreakdown ?? {
      sizeFit: 1, styleMatch: 1, colorHarmony: 1, objectConflict: 1,
    },
    rationale: overrides.rationale ?? '테스트 이유',
  };
}

type Nav = {
  goBack: jest.Mock;
  navigate: jest.Mock;
  replace: jest.Mock;
  push: jest.Mock;
};
function makeNav(): Nav {
  return {
    goBack: jest.fn(),
    navigate: jest.fn(),
    replace: jest.fn(),
    push: jest.fn(),
  };
}

function renderScreen(
  opts: { item?: RecommendationItem; roomId?: string; nav?: Nav } = {},
) {
  const nav: Nav = opts.nav ?? makeNav();
  const item: RecommendationItem = opts.item ?? makeItem();
  const roomId = opts.roomId ?? 'r1';
  const utils = render(
    <ARPlacementScreen
      navigation={nav as unknown as never}
      route={{
        key: 'ARPlacement',
        name: 'ARPlacement',
        params: { roomId, item },
      } as unknown as never}
    />,
  );
  return { ...utils, nav, item, roomId };
}

/**
 * Inspect the latest Alert.alert call and invoke the button whose text
 * matches `label`. Mirrors WishlistScreen.test.tsx's confirmAlert helper.
 */
function pressAlertButton(spy: jest.SpyInstance, label: string): void {
  const calls = spy.mock.calls;
  const last = calls[calls.length - 1];
  const buttons: Array<{ text: string; onPress?: () => void }> = last[2] ?? [];
  const btn = buttons.find((b) => b.text === label);
  if (!btn) {
    throw new Error(`pressAlertButton: no button with text=${label} in Alert`);
  }
  btn.onPress?.();
}

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

describe('ARPlacementScreen (AR-furniture-placement AC-21..AC-32)', () => {
  let alertSpy: jest.SpyInstance;
  let analyticsSink: jest.Mock;

  beforeEach(() => {
    jest.clearAllMocks();
    ARWebViewMockMod.__reset();
    alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined as never);
    analyticsSink = jest.fn();
    setArAnalyticsSink(analyticsSink);
  });

  afterEach(() => {
    alertSpy.mockRestore();
    setArAnalyticsSink(() => undefined);
  });

  // -----------------------------------------------------------------
  // AC-21 — renders ar-webview on mount.
  // -----------------------------------------------------------------
  it('renders ar-webview on mount', () => {
    const { getByTestId } = renderScreen();
    expect(getByTestId('ar-webview')).toBeTruthy();
  });

  // -----------------------------------------------------------------
  // AC-21 sub-assertion — mount injects a `load` message with the
  // expected bridgeVersion / furnitureId / modelUrl / roomDimensions.
  // Also proves FR-4 per-type dimensions reach the bridge.
  // -----------------------------------------------------------------
  it('injects a load message with the per-type default dimensions on mount', () => {
    const item = makeItem({ furnitureId: 'f_bed_001', type: 'bed' });
    renderScreen({ item });
    const injected = ARWebViewMockMod.__getInjected();
    expect(injected.length).toBeGreaterThanOrEqual(1);
    const payload = JSON.parse(injected[0]);
    expect(payload).toMatchObject({
      bridgeVersion: AR_BRIDGE_VERSION,
      event: 'load',
      furnitureId: 'f_bed_001',
      modelUrl: expect.stringContaining('bed.glb'),
      roomDimensions: DEFAULT_DIMENSIONS_BY_TYPE.bed,
      colorHex: null,
    });
  });

  // -----------------------------------------------------------------
  // AC-22 — placed triggers Alert with the spec title + message.
  // -----------------------------------------------------------------
  it('placed triggers Alert with title and message', () => {
    renderScreen();
    act(() => {
      ARWebViewMockMod.__emit({
        bridgeVersion: 1,
        event: 'placed',
        pose: { x: 0, y: 0, z: 0, yaw: 0 },
      });
    });
    expect(alertSpy).toHaveBeenCalledTimes(1);
    const [title, message] = alertSpy.mock.calls[0];
    expect(title).toBe('배치 완료');
    expect(message).toBe('위시리스트에 추가하시겠습니까?');
  });

  // -----------------------------------------------------------------
  // AC-23 — Alert buttons are 위시리스트에 추가 and 닫기.
  // -----------------------------------------------------------------
  it('placed Alert buttons are 위시리스트에 추가 and 닫기', () => {
    renderScreen();
    act(() => {
      ARWebViewMockMod.__emit({
        bridgeVersion: 1,
        event: 'placed',
        pose: { x: 0, y: 0, z: 0, yaw: 0 },
      });
    });
    const buttons = alertSpy.mock.calls[0][2] as Array<{ text: string }>;
    expect(buttons).toHaveLength(2);
    expect(buttons.map((b) => b.text)).toEqual(['위시리스트에 추가', '닫기']);
  });

  // -----------------------------------------------------------------
  // AC-24 — Tapping "위시리스트에 추가" calls addToWishlist with
  // {furnitureId, category=item.type, price=item.price}.
  // -----------------------------------------------------------------
  it('Alert 위시리스트에 추가 calls addToWishlist', async () => {
    const item = makeItem({ furnitureId: 'f_chair_001', type: 'chair', price: 89000 });
    (addToWishlist as jest.Mock).mockResolvedValue({
      wishlistId: 'w1', userId: 'u_dev', furnitureId: 'f_chair_001',
      category: 'chair', price: 89000, status: 'Active',
      addedAt: '2026-04-18T10:00:00', purchasedAt: null,
      furnitureSnapshot: null, alreadyExists: false,
    });

    renderScreen({ item });
    act(() => {
      ARWebViewMockMod.__emit({
        bridgeVersion: 1,
        event: 'placed',
        pose: { x: 0, y: 0, z: 0, yaw: 0 },
      });
    });

    await act(async () => {
      pressAlertButton(alertSpy, '위시리스트에 추가');
    });

    expect(addToWishlist).toHaveBeenCalledTimes(1);
    expect(addToWishlist).toHaveBeenCalledWith(
      expect.objectContaining({
        furnitureId: 'f_chair_001',
        category: 'chair',
        price: 89000,
      }),
    );
  });

  // -----------------------------------------------------------------
  // AC-25 — cancelled → goBack; addToWishlist not called; no Alert.
  // -----------------------------------------------------------------
  it('cancelled calls goBack and skips Alert', () => {
    const nav = makeNav();
    renderScreen({ nav });

    act(() => {
      ARWebViewMockMod.__emit({ bridgeVersion: 1, event: 'cancelled' });
    });

    expect(nav.goBack).toHaveBeenCalledTimes(1);
    expect(addToWishlist).not.toHaveBeenCalled();
    expect(alertSpy).not.toHaveBeenCalled();
  });

  // -----------------------------------------------------------------
  // AC-26 — each error code renders the matching Korean copy.
  // -----------------------------------------------------------------
  it.each([
    'AR_NOT_SUPPORTED',
    'AR_CAMERA_DENIED',
    'AR_MODEL_LOAD_FAILED',
    'AR_SESSION_ERROR',
  ] as const)('error code %s renders Korean copy', (code) => {
    const { queryByText, getByTestId } = renderScreen();
    act(() => {
      ARWebViewMockMod.__emit({ bridgeVersion: 1, event: 'error', errorCode: code });
    });
    // Korean copy (from AR_ERROR_COPY) is rendered.
    expect(queryByText(AR_ERROR_COPY[code])).toBeTruthy();
    // btn-ar-back is present on the fallback panel.
    expect(getByTestId('btn-ar-back')).toBeTruthy();
  });

  // -----------------------------------------------------------------
  // AC-27 — error hides ar-webview, shows ar-fallback.
  // -----------------------------------------------------------------
  it('error hides ar-webview and shows ar-fallback', () => {
    const { queryByTestId, getByTestId } = renderScreen();
    // Pre-condition.
    expect(queryByTestId('ar-webview')).toBeTruthy();

    act(() => {
      ARWebViewMockMod.__emit({
        bridgeVersion: 1, event: 'error', errorCode: 'AR_NOT_SUPPORTED',
      });
    });

    expect(queryByTestId('ar-webview')).toBeNull();
    expect(getByTestId('ar-fallback')).toBeTruthy();
  });

  // -----------------------------------------------------------------
  // AC-28 — Alert dismissal (both branches) unmounts the screen
  // (goBack side-effect fires).
  // -----------------------------------------------------------------
  it('Alert dismissal unmounts screen (goBack called on both buttons)', async () => {
    (addToWishlist as jest.Mock).mockResolvedValue({
      wishlistId: 'w1', userId: 'u_dev', furnitureId: 'f_desk_001',
      category: 'desk', price: 189000, status: 'Active',
      addedAt: 't', purchasedAt: null, furnitureSnapshot: null,
      alreadyExists: false,
    });

    // Path A — "위시리스트에 추가"
    {
      const nav = makeNav();
      renderScreen({ nav });
      act(() => {
        ARWebViewMockMod.__emit({
          bridgeVersion: 1, event: 'placed',
          pose: { x: 0, y: 0, z: 0, yaw: 0 },
        });
      });
      await act(async () => {
        pressAlertButton(alertSpy, '위시리스트에 추가');
      });
      expect(nav.goBack).toHaveBeenCalledTimes(1);
    }

    // Reset between sub-cases.
    ARWebViewMockMod.__reset();
    alertSpy.mockClear();

    // Path B — "닫기"
    {
      const nav = makeNav();
      renderScreen({ nav });
      act(() => {
        ARWebViewMockMod.__emit({
          bridgeVersion: 1, event: 'placed',
          pose: { x: 0, y: 0, z: 0, yaw: 0 },
        });
      });
      await act(async () => {
        pressAlertButton(alertSpy, '닫기');
      });
      expect(nav.goBack).toHaveBeenCalledTimes(1);
    }
  });

  // -----------------------------------------------------------------
  // AC-29 — teardown leaves no pending timers; unload is injected.
  // -----------------------------------------------------------------
  it('teardown leaves no pending timers and injects unload', () => {
    jest.useFakeTimers();
    try {
      const { unmount } = renderScreen();

      // Fresh mount injects `load`.
      const afterMount = ARWebViewMockMod.__getInjected();
      expect(afterMount.length).toBeGreaterThanOrEqual(1);
      expect(JSON.parse(afterMount[0])).toMatchObject({ event: 'load' });

      unmount();

      // After unmount, an `unload` inbound must have been injected.
      const afterUnmount = ARWebViewMockMod.__getInjected();
      expect(afterUnmount.length).toBeGreaterThan(afterMount.length);
      const last = JSON.parse(afterUnmount[afterUnmount.length - 1]);
      expect(last).toEqual({ bridgeVersion: 1, event: 'unload' });

      // No lingering timers on teardown.
      expect(jest.getTimerCount()).toBe(0);
    } finally {
      jest.useRealTimers();
    }
  });

  // -----------------------------------------------------------------
  // AC-30 — emitArFurniturePlaced fires exactly once per placed event.
  // -----------------------------------------------------------------
  it('emitArFurniturePlaced called exactly once per placed event', () => {
    const item = makeItem({ furnitureId: 'f_desk_001' });
    renderScreen({ item, roomId: 'r7' });

    act(() => {
      ARWebViewMockMod.__emit({
        bridgeVersion: 1, event: 'placed',
        pose: { x: 1, y: 2, z: 3, yaw: 4 },
      });
    });

    expect(analyticsSink).toHaveBeenCalledTimes(1);
    const call = analyticsSink.mock.calls[0][0] as ArAnalyticsEvent;
    expect(call).toEqual({
      type: 'ar_furniture_placed',
      payload: {
        roomId: 'r7',
        furnitureId: 'f_desk_001',
        pose: { x: 1, y: 2, z: 3, yaw: 4 },
      },
    });
  });

  // -----------------------------------------------------------------
  // AC-31 — wishlist-add toast strings match RecommendationScreen
  // byte-for-byte. Exercises all five branches.
  // -----------------------------------------------------------------
  it('wishlist-add toast strings match RecommendationScreen (5 branches)', async () => {
    async function runAdd(
      mockImpl: (opts?: unknown) => Promise<unknown> | never,
    ): Promise<void> {
      (addToWishlist as jest.Mock).mockImplementationOnce(mockImpl);

      ARWebViewMockMod.__reset();
      alertSpy.mockClear();
      renderScreen();

      act(() => {
        ARWebViewMockMod.__emit({
          bridgeVersion: 1, event: 'placed',
          pose: { x: 0, y: 0, z: 0, yaw: 0 },
        });
      });
      await act(async () => {
        pressAlertButton(alertSpy, '위시리스트에 추가');
      });
    }

    // Branch 1 — success new
    await runAdd(async () => ({
      wishlistId: 'w1', userId: 'u', furnitureId: 'f_desk_001',
      category: 'desk', price: 1, status: 'Active',
      addedAt: 't', purchasedAt: null, furnitureSnapshot: null,
      alreadyExists: false,
    }));
    expect(alertSpy).toHaveBeenLastCalledWith('', WISHLIST_TOAST_COPY.SUCCESS_NEW);
    expect(WISHLIST_TOAST_COPY.SUCCESS_NEW).toBe('위시리스트에 담았어요.');

    // Branch 2 — exists-active
    await runAdd(async () => ({
      wishlistId: 'w1', userId: 'u', furnitureId: 'f_desk_001',
      category: 'desk', price: 1, status: 'Active',
      addedAt: 't', purchasedAt: null, furnitureSnapshot: null,
      alreadyExists: true,
    }));
    expect(alertSpy).toHaveBeenLastCalledWith('', WISHLIST_TOAST_COPY.SUCCESS_EXISTS_ACTIVE);
    expect(WISHLIST_TOAST_COPY.SUCCESS_EXISTS_ACTIVE).toBe('이미 위시리스트에 있어요.');

    // Branch 3 — exists-purchased
    await runAdd(async () => ({
      wishlistId: 'w1', userId: 'u', furnitureId: 'f_desk_001',
      category: 'desk', price: 1, status: 'Purchased',
      addedAt: 't', purchasedAt: 'tp', furnitureSnapshot: null,
      alreadyExists: true,
    }));
    expect(alertSpy).toHaveBeenLastCalledWith('', WISHLIST_TOAST_COPY.SUCCESS_EXISTS_PURCHASED);
    expect(WISHLIST_TOAST_COPY.SUCCESS_EXISTS_PURCHASED).toBe(
      '이미 구매 완료로 표시된 항목이에요.',
    );

    // Branch 4 — ApiError FURNITURE_NOT_FOUND → fail-api toast
    await runAdd(async () => {
      throw new ApiError(404, {
        errorCode: 'FURNITURE_NOT_FOUND', message: 'x', correlationId: 'y',
      });
    });
    expect(alertSpy).toHaveBeenLastCalledWith('', WISHLIST_TOAST_COPY.FAIL_API);
    expect(WISHLIST_TOAST_COPY.FAIL_API).toBe('위시리스트에 추가하지 못했어요.');

    // Branch 5 — other error → fail-network
    await runAdd(async () => {
      throw new ApiError(502, {
        errorCode: 'AI_SERVICE_UNAVAILABLE', message: 'x', correlationId: 'y',
      });
    });
    expect(alertSpy).toHaveBeenLastCalledWith('', WISHLIST_TOAST_COPY.FAIL_NETWORK);
    expect(WISHLIST_TOAST_COPY.FAIL_NETWORK).toBe('네트워크 오류로 추가하지 못했어요.');
  });

  // -----------------------------------------------------------------
  // AC-32 — btn-ar-back on the fallback panel calls goBack.
  // -----------------------------------------------------------------
  it('btn-ar-back calls goBack', () => {
    const nav = makeNav();
    const { getByTestId } = renderScreen({ nav });

    act(() => {
      ARWebViewMockMod.__emit({
        bridgeVersion: 1, event: 'error', errorCode: 'AR_NOT_SUPPORTED',
      });
    });

    fireEvent.press(getByTestId('btn-ar-back'));
    expect(nav.goBack).toHaveBeenCalledTimes(1);
  });

  // -----------------------------------------------------------------
  // AC-39 — accessibility labels present on btn-ar-back.
  // -----------------------------------------------------------------
  it('accessibility labels present on fallback 돌아가기 button', () => {
    const { getByTestId } = renderScreen();
    act(() => {
      ARWebViewMockMod.__emit({
        bridgeVersion: 1, event: 'error', errorCode: 'AR_NOT_SUPPORTED',
      });
    });
    const btn = getByTestId('btn-ar-back');
    expect(btn.props.accessibilityLabel).toBe('돌아가기');
    expect(btn.props.accessibilityRole).toBe('button');
  });

  // -----------------------------------------------------------------
  // Bonus — malformed payload from scene surfaces as AR_SESSION_ERROR.
  // -----------------------------------------------------------------
  it('decode error surfaces AR_SESSION_ERROR fallback', () => {
    const { getByTestId, queryByText } = renderScreen();
    act(() => {
      ARWebViewMockMod.__emitDecodeError('not json', new Error('boom'));
    });
    expect(getByTestId('ar-fallback')).toBeTruthy();
    expect(queryByText(AR_ERROR_COPY.AR_SESSION_ERROR)).toBeTruthy();
  });
});
