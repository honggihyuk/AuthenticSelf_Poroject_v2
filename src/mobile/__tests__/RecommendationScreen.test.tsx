/**
 * Tests for RecommendationScreen (UC-01-recommendation AC-44..AC-50).
 *
 * Uses a mocked `getRecommendations` and `emitWishlistAddClicked` so the
 * screen's network + analytics surface is fully under test control.
 * Alerts (iOS toast fallback) are spied via `Alert.alert`.
 */

import React from 'react';
import { Alert } from 'react-native';
import {
  act,
  fireEvent,
  render,
  waitFor,
} from '@testing-library/react-native';

jest.mock('../src/api/spaces', () => ({
  ...jest.requireActual('../src/api/spaces'),
  getRecommendations: jest.fn(),
  emitWishlistAddClicked: jest.fn(),
}));

// UC-02-wishlist FR-16 / AC-36 — RecommendationScreen now calls the
// real wishlist client. Mock it so the test suite stays deterministic.
jest.mock('../src/api/wishlist', () => ({
  ...jest.requireActual('../src/api/wishlist'),
  addToWishlist: jest.fn(),
}));

import {
  emitWishlistAddClicked,
  getRecommendations,
  RecommendationResponse,
} from '../src/api/spaces';
import { addToWishlist } from '../src/api/wishlist';
import { ApiError } from '../src/api/client';
import RecommendationScreen from '../src/screens/RecommendationScreen';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeItem(overrides: Partial<any> = {}) {
  return {
    furnitureId: overrides.furnitureId ?? 'f_desk_001',
    name: overrides.name ?? 'Oslo Slim Desk',
    type: overrides.type ?? 'desk',
    price: overrides.price ?? 189000,
    imageUrl: overrides.imageUrl ?? 'https://cdn.example.com/img.jpg',
    fitScore: overrides.fitScore ?? 0.87,
    scoreBreakdown: overrides.scoreBreakdown ?? {
      sizeFit: 1.0,
      styleMatch: 1.0,
      colorHarmony: 0.85,
      objectConflict: 1.0,
    },
    rationale: overrides.rationale ?? '모던 스타일 일치, 공간에 여유롭게 들어맞음',
  };
}

function twoItemsPerCategory(): RecommendationResponse {
  return {
    roomId: 'r1',
    status: 'OK',
    resolvedStyle: 'MODERN',
    preferredStyle: 'MODERN',
    generatedAt: '2026-04-18T09:14:22Z',
    cacheHit: false,
    recommendations: {
      desk: [
        makeItem({ furnitureId: 'f_desk_001', name: 'Oslo Slim Desk', price: 189000, fitScore: 0.87 }),
        makeItem({ furnitureId: 'f_desk_006', name: 'Aarhus Multi Desk', price: 205000, fitScore: 0.8, rationale: '수납이 넉넉한 대형 책상' }),
      ],
      bed: [
        makeItem({ furnitureId: 'f_bed_001', type: 'bed', name: 'Aurora Modern Bed', price: 680000, fitScore: 0.84, rationale: '침실 크기와 잘 어울림' }),
        makeItem({ furnitureId: 'f_bed_006', type: 'bed', name: 'Stockholm Hybrid Bed', price: 665000, fitScore: 0.78, rationale: '심플한 프레임이 공간에 깔끔' }),
      ],
      chair: [
        makeItem({ furnitureId: 'f_chair_001', type: 'chair', name: 'Noir Modern Chair', price: 89000, fitScore: 0.82, rationale: '책상 옆에 자연스러운 짝' }),
        makeItem({ furnitureId: 'f_chair_006', type: 'chair', name: 'Studio Hybrid Chair', price: 105000, fitScore: 0.76, rationale: '다목적 활용 가능한 스타일' }),
      ],
      lighting: [
        makeItem({ furnitureId: 'f_lighting_001', type: 'lighting', name: 'Pola Modern Lamp', price: 135000, fitScore: 0.79, rationale: '따뜻한 조도로 공간 완성' }),
        makeItem({ furnitureId: 'f_lighting_006', type: 'lighting', name: 'Helsinki Hybrid Lamp', price: 115000, fitScore: 0.73, rationale: '부드러운 간접 조명' }),
      ],
    },
    warning: null,
    processingMs: 18,
  };
}

function renderScreen(overrides?: { preferredStyle?: any }) {
  const goBack = jest.fn();
  const navigate = jest.fn();
  const navigation: any = { goBack, navigate, replace: jest.fn() };
  return {
    goBack,
    navigate,
    ...render(
      <RecommendationScreen
        navigation={navigation}
        route={{
          key: 'Recommendation',
          name: 'Recommendation',
          params: { roomId: 'r1', ...(overrides ?? {}) },
        } as any}
      />,
    ),
  };
}

// ---------------------------------------------------------------------------
// Test suite
// ---------------------------------------------------------------------------

describe('RecommendationScreen (UC-01-recommendation AC-44..AC-50)', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  // -----------------------------------------------------------------
  // AC-44 — four sections, header, 8 cards with two items/category.
  // -----------------------------------------------------------------
  it('AC-44: renders four Korean section headers + "모던 스타일 추천" + 8 cards', async () => {
    (getRecommendations as jest.Mock).mockResolvedValue(twoItemsPerCategory());

    const { findByText, getByText, getAllByTestId } = renderScreen();

    // header with resolved preferredStyle label.
    await findByText('모던 스타일 추천');

    // Four section headers.
    expect(getByText('책상')).toBeTruthy();
    expect(getByText('침대')).toBeTruthy();
    expect(getByText('의자')).toBeTruthy();
    expect(getByText('조명')).toBeTruthy();

    // Eight cards — one testID per card.
    await waitFor(() => {
      const wishlistButtons = getAllByTestId(/^btn-wishlist-/);
      expect(wishlistButtons).toHaveLength(8);
    });
  });

  // -----------------------------------------------------------------
  // AC-45 — item card content (name, price, match pct, rationale).
  // -----------------------------------------------------------------
  it('AC-45: item card shows name, ₩-formatted price, "매칭 87%" and rationale', async () => {
    (getRecommendations as jest.Mock).mockResolvedValue(twoItemsPerCategory());

    const { findByText, getByText } = renderScreen();

    await findByText('Oslo Slim Desk');
    expect(getByText('\u20A9189,000')).toBeTruthy();
    expect(getByText('매칭 87%')).toBeTruthy();
    expect(
      getByText(/모던 스타일 일치, 공간에 여유롭게 들어맞음/),
    ).toBeTruthy();
  });

  // -----------------------------------------------------------------
  // AC-46 — empty-section placeholder for bed.
  // -----------------------------------------------------------------
  it('AC-46: empty bed array renders the bed placeholder and zero bed cards', async () => {
    const resp = twoItemsPerCategory();
    resp.recommendations.bed = [];
    (getRecommendations as jest.Mock).mockResolvedValue(resp);

    const { findByText, queryByTestId } = renderScreen();

    await findByText('침대는 현재 추천할 가구가 없습니다.');

    // No bed cards rendered.
    expect(queryByTestId('card-f_bed_001')).toBeNull();
    expect(queryByTestId('card-f_bed_006')).toBeNull();
  });

  // -----------------------------------------------------------------
  // AC-47 — all arrays empty + warning triggers full-empty state.
  // -----------------------------------------------------------------
  it('AC-47: NO_FIT_ANY_CATEGORY renders "딱 맞는 가구를 찾지 못했" + goBack on tap', async () => {
    (getRecommendations as jest.Mock).mockResolvedValue({
      roomId: 'r1',
      status: 'OK',
      resolvedStyle: 'MODERN',
      preferredStyle: 'MODERN',
      generatedAt: '2026-04-18T09:14:22Z',
      cacheHit: false,
      recommendations: { desk: [], bed: [], chair: [], lighting: [] },
      warning: 'NO_FIT_ANY_CATEGORY',
      processingMs: 3,
    } as RecommendationResponse);

    const { findByText, getByTestId, goBack } = renderScreen();

    await findByText(/딱 맞는 가구를 찾지 못했/);

    await act(async () => {
      fireEvent.press(getByTestId('btn-retry-style'));
    });

    expect(goBack).toHaveBeenCalledTimes(1);
  });

  // -----------------------------------------------------------------
  // UC-02 AC-36 — wishlist button calls real POST + still emits analytics
  // (UC-01 AC-48 analytics invariant preserved by UC-02 AC-49).
  // -----------------------------------------------------------------
  it('UC-02 AC-36: tapping 위시리스트에 추가 emits analytics AND POSTs', async () => {
    (getRecommendations as jest.Mock).mockResolvedValue(twoItemsPerCategory());
    (addToWishlist as jest.Mock).mockResolvedValue({
      wishlistId: 'w1', userId: 'u_dev', furnitureId: 'f_desk_001',
      category: 'desk', price: 189000, status: 'Active',
      addedAt: '2026-04-18T09:14:22', purchasedAt: null,
      furnitureSnapshot: null, alreadyExists: false,
    });
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined as any);

    const { getByTestId, findByText } = renderScreen();
    await findByText('Oslo Slim Desk');

    await act(async () => {
      fireEvent.press(getByTestId('btn-wishlist-f_desk_001'));
    });

    expect(emitWishlistAddClicked).toHaveBeenCalledTimes(1);
    expect(emitWishlistAddClicked).toHaveBeenCalledWith('r1', 'f_desk_001');
    expect(addToWishlist).toHaveBeenCalledTimes(1);
    expect(addToWishlist).toHaveBeenCalledWith(
      expect.objectContaining({
        userId: expect.any(String),
        furnitureId: 'f_desk_001',
        category: 'desk',
        price: 189000,
      }),
    );

    alertSpy.mockRestore();
  });

  // -----------------------------------------------------------------
  // UC-02 AC-37 — toast mapping for three success variants.
  // -----------------------------------------------------------------
  it('UC-02 AC-37: toast mapping on success (new / exists-active / exists-purchased)', async () => {
    (getRecommendations as jest.Mock).mockResolvedValue(twoItemsPerCategory());
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined as any);

    // 1. alreadyExists=false, Active → "위시리스트에 담았어요."
    (addToWishlist as jest.Mock).mockResolvedValueOnce({
      wishlistId: 'w1', userId: 'u_dev', furnitureId: 'f_desk_001',
      category: 'desk', price: 189000, status: 'Active',
      addedAt: '2026-04-18T09:14:22', purchasedAt: null,
      furnitureSnapshot: null, alreadyExists: false,
    });
    const { getByTestId, findByText } = renderScreen();
    await findByText('Oslo Slim Desk');

    await act(async () => { fireEvent.press(getByTestId('btn-wishlist-f_desk_001')); });
    expect(alertSpy).toHaveBeenLastCalledWith('', '위시리스트에 담았어요.');

    // 2. alreadyExists=true, Active → "이미 위시리스트에 있어요."
    (addToWishlist as jest.Mock).mockResolvedValueOnce({
      wishlistId: 'w1', userId: 'u_dev', furnitureId: 'f_desk_001',
      category: 'desk', price: 189000, status: 'Active',
      addedAt: '2026-04-18T09:14:22', purchasedAt: null,
      furnitureSnapshot: null, alreadyExists: true,
    });
    await act(async () => { fireEvent.press(getByTestId('btn-wishlist-f_desk_001')); });
    expect(alertSpy).toHaveBeenLastCalledWith('', '이미 위시리스트에 있어요.');

    // 3. alreadyExists=true, Purchased → "이미 구매 완료로 표시된 항목이에요."
    (addToWishlist as jest.Mock).mockResolvedValueOnce({
      wishlistId: 'w1', userId: 'u_dev', furnitureId: 'f_desk_001',
      category: 'desk', price: 189000, status: 'Purchased',
      addedAt: '2026-04-18T09:14:22', purchasedAt: '2026-04-18T10:00:00',
      furnitureSnapshot: null, alreadyExists: true,
    });
    await act(async () => { fireEvent.press(getByTestId('btn-wishlist-f_desk_001')); });
    expect(alertSpy).toHaveBeenLastCalledWith('', '이미 구매 완료로 표시된 항목이에요.');

    alertSpy.mockRestore();
  });

  // -----------------------------------------------------------------
  // UC-02 AC-38 — error toast mapping + no navigation change.
  // -----------------------------------------------------------------
  it('UC-02 AC-38: error toast mapping (FURNITURE_NOT_FOUND / USER_NOT_FOUND / other)', async () => {
    (getRecommendations as jest.Mock).mockResolvedValue(twoItemsPerCategory());
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined as any);

    // a. 404 FURNITURE_NOT_FOUND → "위시리스트에 추가하지 못했어요."
    (addToWishlist as jest.Mock).mockRejectedValueOnce(
      new ApiError(404, { errorCode: 'FURNITURE_NOT_FOUND', message: 'x', correlationId: 'y' }),
    );
    const navigate = jest.fn();
    const goBack = jest.fn();
    const replace = jest.fn();
    const { getByTestId, findByText } = render(
      <RecommendationScreen
        navigation={{ navigate, goBack, replace, push: jest.fn() } as any}
        route={{ key: 'R', name: 'Recommendation', params: { roomId: 'r1' } } as any}
      />,
    );
    await findByText('Oslo Slim Desk');

    await act(async () => { fireEvent.press(getByTestId('btn-wishlist-f_desk_001')); });
    expect(alertSpy).toHaveBeenLastCalledWith('', '위시리스트에 추가하지 못했어요.');

    // b. 502 AI_SERVICE_UNAVAILABLE (other error code) → generic network toast.
    (addToWishlist as jest.Mock).mockRejectedValueOnce(
      new ApiError(502, { errorCode: 'AI_SERVICE_UNAVAILABLE', message: 'x', correlationId: 'y' }),
    );
    await act(async () => { fireEvent.press(getByTestId('btn-wishlist-f_desk_001')); });
    expect(alertSpy).toHaveBeenLastCalledWith('', '네트워크 오류로 추가하지 못했어요.');

    // Navigation spy receives zero calls attributable to the button.
    expect(navigate).not.toHaveBeenCalled();
    expect(goBack).not.toHaveBeenCalled();
    expect(replace).not.toHaveBeenCalled();

    alertSpy.mockRestore();
  });

  // -----------------------------------------------------------------
  // UC-02 AC-49 — regression guard for UC-01 AC-48 analytics invariant.
  // -----------------------------------------------------------------
  it('UC-02 AC-49: emitWishlistAddClicked fires exactly once per tap', async () => {
    (getRecommendations as jest.Mock).mockResolvedValue(twoItemsPerCategory());
    // Even when the real API rejects, the analytics emit must still
    // fire exactly once (it runs BEFORE the fetch by design — FR-16).
    (addToWishlist as jest.Mock).mockRejectedValue(
      new ApiError(500, { errorCode: 'SERVER', message: 'x', correlationId: 'y' }),
    );
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined as any);

    const { getByTestId, findByText } = renderScreen();
    await findByText('Oslo Slim Desk');

    await act(async () => { fireEvent.press(getByTestId('btn-wishlist-f_desk_001')); });
    expect(emitWishlistAddClicked).toHaveBeenCalledTimes(1);

    alertSpy.mockRestore();
  });

  // -----------------------------------------------------------------
  // AC-49 — 409 PREFERRED_STYLE_NOT_SET shows error copy + goBack button.
  // -----------------------------------------------------------------
  it('AC-49: 409 PREFERRED_STYLE_NOT_SET shows "스타일을 먼저 선택" and "스타일 선택" button', async () => {
    (getRecommendations as jest.Mock).mockRejectedValue(
      new ApiError(409, {
        errorCode: 'PREFERRED_STYLE_NOT_SET',
        message: 'pick style',
        correlationId: 'abc',
      }),
    );

    const { findByText, getByTestId, goBack } = renderScreen();

    await findByText('스타일을 먼저 선택해주세요.');

    await act(async () => {
      fireEvent.press(getByTestId('btn-go-style-select'));
    });

    expect(goBack).toHaveBeenCalledTimes(1);
  });

  // -----------------------------------------------------------------
  // AC-50 — loading spinner + text while the fetch is pending.
  // -----------------------------------------------------------------
  it('AC-50: pending fetch renders spinner + "추천을 준비하고 있어요..."', async () => {
    // Never-resolving fetch — the screen stays in loading state.
    let _resolveOuter: (v: RecommendationResponse) => void = () => undefined;
    const neverResolve = new Promise<RecommendationResponse>((resolve) => {
      _resolveOuter = resolve;
    });
    (getRecommendations as jest.Mock).mockReturnValue(neverResolve);

    const { getByTestId, getByText } = renderScreen();

    expect(getByTestId('recommendation-loading')).toBeTruthy();
    expect(getByText('추천을 준비하고 있어요...')).toBeTruthy();

    // silence the dangling promise — not needed but tidy.
    // (jest will GC it after the test exits)
  });

  // -----------------------------------------------------------------
  // FR-23 error bucket — 502 AI_SERVICE_UNAVAILABLE shows the generic
  // "연결할 수 없습니다" copy and a "다시 시도" button.
  // -----------------------------------------------------------------
  it('FR-23: 502 AI_SERVICE_UNAVAILABLE shows retry copy + button', async () => {
    (getRecommendations as jest.Mock).mockRejectedValue(
      new ApiError(502, {
        errorCode: 'AI_SERVICE_UNAVAILABLE',
        message: 'boom',
        correlationId: 'x',
      }),
    );

    const { findByText, getByTestId } = renderScreen();

    await findByText(/추천 서비스에 연결할 수 없습니다/);
    expect(getByTestId('btn-retry')).toBeTruthy();
  });

  // ===================================================================
  // AR-furniture-placement FR-16 / AC-34 / AC-35 / AC-39 — additive.
  // These are appended per the task's "additive only" rule; the prior
  // UC-01 / UC-02 tests above are not modified.
  // ===================================================================

  it('AR AC-34: btn-ar-{furnitureId} testID is present for every rendered card', async () => {
    (getRecommendations as jest.Mock).mockResolvedValue(twoItemsPerCategory());

    const { findByText, getAllByTestId } = renderScreen();
    await findByText('Oslo Slim Desk');

    const arButtons = getAllByTestId(/^btn-ar-/);
    // 8 items (2 per category × 4 categories) → 8 buttons.
    expect(arButtons).toHaveLength(8);

    // Every wishlist button still has a matching AR sibling — preserves
    // UC-01 AC-48 / UC-02 AC-49 structural invariants.
    const wishBtns = getAllByTestId(/^btn-wishlist-/);
    expect(wishBtns).toHaveLength(arButtons.length);
  });

  it('AR AC-35: tapping btn-ar-{fid} navigates to ARPlacement with {roomId, item}', async () => {
    (getRecommendations as jest.Mock).mockResolvedValue(twoItemsPerCategory());

    const { findByText, getByTestId, navigate } = renderScreen();
    await findByText('Oslo Slim Desk');

    await act(async () => {
      fireEvent.press(getByTestId('btn-ar-f_desk_001'));
    });

    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(
      'ARPlacement',
      expect.objectContaining({
        roomId: 'r1',
        item: expect.objectContaining({
          furnitureId: 'f_desk_001',
          type: 'desk',
          price: 189000,
        }),
      }),
    );
  });

  it('AR AC-39: btn-ar-{fid} has accessibilityLabel="AR로 배치" + role=button', async () => {
    (getRecommendations as jest.Mock).mockResolvedValue(twoItemsPerCategory());

    const { findByText, getByTestId } = renderScreen();
    await findByText('Oslo Slim Desk');

    const btn = getByTestId('btn-ar-f_desk_001');
    expect(btn.props.accessibilityLabel).toBe('AR로 배치');
    expect(btn.props.accessibilityRole).toBe('button');
  });
});
