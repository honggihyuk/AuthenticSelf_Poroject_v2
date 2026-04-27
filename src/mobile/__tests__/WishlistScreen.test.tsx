/**
 * Tests for WishlistScreen (UC-02-wishlist AC-39..AC-46).
 *
 * getWishlist / updateWishlistState / deleteWishlistItem are mocked so
 * the screen's network surface is fully under test control. Confirm
 * flows go through Alert.alert; we call the "확인" callback directly
 * to avoid depending on React Native's Alert dispatching details.
 */

import React from 'react';
import { Alert } from 'react-native';
import {
  act,
  fireEvent,
  render,
  waitFor,
} from '@testing-library/react-native';

jest.mock('../src/api/wishlist', () => ({
  ...jest.requireActual('../src/api/wishlist'),
  getWishlist: jest.fn(),
  listWishlist: jest.fn(),
  updateWishlistState: jest.fn(),
  patchWishlist: jest.fn(),
  deleteWishlistItem: jest.fn(),
  deleteWishlist: jest.fn(),
}));

import {
  getWishlist,
  updateWishlistState,
  deleteWishlistItem,
  ListWishlistResponse,
  WishlistItem,
} from '../src/api/wishlist';
import { ApiError } from '../src/api/client';
import WishlistScreen from '../src/screens/WishlistScreen';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeItem(overrides: Partial<WishlistItem> = {}): WishlistItem {
  return {
    wishlistId: overrides.wishlistId ?? 'w1',
    userId: overrides.userId ?? 'u_dev',
    furnitureId: overrides.furnitureId ?? 'f_desk_001',
    category: overrides.category ?? 'desk',
    price: overrides.price ?? 189000,
    status: overrides.status ?? 'Active',
    addedAt: overrides.addedAt ?? '2026-04-18T09:14:22',
    purchasedAt: overrides.purchasedAt ?? null,
    furnitureSnapshot:
      overrides.furnitureSnapshot === undefined
        ? {
            name: 'Oslo Slim Desk',
            imageUrl: 'https://cdn.example.com/oslo.jpg',
            colorHex: '#F3E6D2',
            type: 'desk',
          }
        : overrides.furnitureSnapshot,
  };
}

function renderScreen() {
  const navigate = jest.fn();
  const goBack = jest.fn();
  return render(
    <WishlistScreen
      navigation={{ navigate, goBack } as any}
      route={{ key: 'Wishlist', name: 'Wishlist' } as any}
    />,
  );
}

/**
 * Tap "확인" on the most recent Alert.alert call by invoking the
 * matching button's onPress. Works whether the Alert is open or not.
 */
function confirmAlert(alertSpy: jest.SpyInstance) {
  const calls = alertSpy.mock.calls;
  const lastCall = calls[calls.length - 1];
  const buttons: any[] = lastCall[2] ?? [];
  const confirmBtn = buttons.find((b) => b.text === '확인');
  return confirmBtn?.onPress?.();
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('WishlistScreen (UC-02-wishlist AC-39..AC-46)', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  // -----------------------------------------------------------------
  // AC-39 — two tabs with totals; default tab = Active.
  // -----------------------------------------------------------------
  it('AC-39: renders two tabs, header 내 위시리스트, default shows 2 Active', async () => {
    const resp: ListWishlistResponse = {
      items: [
        makeItem({ wishlistId: 'w1', furnitureId: 'f_desk_001' }),
        makeItem({ wishlistId: 'w2', furnitureId: 'f_bed_001', category: 'bed',
          furnitureSnapshot: { name: 'Aurora Bed', imageUrl: null, colorHex: '#FFF', type: 'bed' } }),
        makeItem({ wishlistId: 'w3', furnitureId: 'f_chair_001', category: 'chair',
          status: 'Purchased', purchasedAt: '2026-04-18T10:00:00',
          furnitureSnapshot: { name: 'Noir Chair', imageUrl: null, colorHex: '#000', type: 'chair' } }),
      ],
      totalActive: 2,
      totalPurchased: 1,
    };
    (getWishlist as jest.Mock).mockResolvedValue(resp);

    const { findByText, getByText, getByTestId, queryByTestId } = renderScreen();
    await findByText('내 위시리스트');
    expect(getByText(/보관 중 \(2\)/)).toBeTruthy();
    expect(getByText(/구매 완료 \(1\)/)).toBeTruthy();

    // Active is default: the two Active cards render, the Purchased
    // card does NOT render under the Active tab.
    expect(getByTestId('wishlist-card-w1')).toBeTruthy();
    expect(getByTestId('wishlist-card-w2')).toBeTruthy();
    expect(queryByTestId('wishlist-card-w3')).toBeNull();
  });

  // -----------------------------------------------------------------
  // AC-40 — card content: name / price / category / date; missing snapshot.
  // -----------------------------------------------------------------
  it('AC-40: card renders name, ₩price, category label, YYYY-MM-DD date; missing snapshot placeholder', async () => {
    const resp: ListWishlistResponse = {
      items: [
        makeItem({ wishlistId: 'w1', furnitureId: 'f_desk_001', price: 189000 }),
        makeItem({ wishlistId: 'w2', furnitureId: 'f_gone', furnitureSnapshot: null }),
      ],
      totalActive: 2,
      totalPurchased: 0,
    };
    (getWishlist as jest.Mock).mockResolvedValue(resp);
    const { findByText, getByText, getByTestId } = renderScreen();
    await findByText('Oslo Slim Desk');
    expect(getByText(/책상.*₩189,000/)).toBeTruthy();
    expect(getByText('2026-04-18')).toBeTruthy();
    // Missing snapshot → placeholder text + disabled-ish card (only
    // 삭제 action, no mark-purchased).
    expect(getByTestId('wishlist-card-missing-w2')).toBeTruthy();
    expect(getByTestId('btn-delete-w2')).toBeTruthy();
  });

  // -----------------------------------------------------------------
  // AC-41 — mark purchased → confirm → PATCH → card moves to Purchased tab.
  // -----------------------------------------------------------------
  it('AC-41: markPurchasedFlow — confirm → PATCH PURCHASED → moves to Purchased tab', async () => {
    const initial: ListWishlistResponse = {
      items: [makeItem({ wishlistId: 'w1' })],
      totalActive: 1,
      totalPurchased: 0,
    };
    (getWishlist as jest.Mock).mockResolvedValue(initial);

    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined as any);

    (updateWishlistState as jest.Mock).mockResolvedValue(
      makeItem({ wishlistId: 'w1', status: 'Purchased', purchasedAt: '2026-04-18T10:00:00' }),
    );

    const { findByText, getByTestId, queryByTestId } = renderScreen();
    await findByText('내 위시리스트');

    // Tap 구매 완료 → Alert.alert with the confirm prompt.
    await act(async () => { fireEvent.press(getByTestId('btn-mark-purchased-w1')); });
    expect(alertSpy).toHaveBeenLastCalledWith(
      '구매 완료로 표시할까요?',
      undefined,
      expect.arrayContaining([expect.objectContaining({ text: '확인' })]),
    );

    // Confirm.
    await act(async () => { await confirmAlert(alertSpy); });
    expect(updateWishlistState).toHaveBeenCalledTimes(1);
    expect(updateWishlistState).toHaveBeenCalledWith(
      expect.objectContaining({ wishlistId: 'w1', status: 'PURCHASED' }),
    );

    // Card should now be under the Purchased tab.
    await waitFor(() => {
      expect(getByTestId('tab-content-purchased')).toBeTruthy();
      expect(getByTestId('wishlist-card-w1')).toBeTruthy();
    });
    // And must no longer appear under the Active tab.
    fireEvent.press(getByTestId('tab-btn-active'));
    expect(queryByTestId('wishlist-card-w1')).toBeNull();

    alertSpy.mockRestore();
  });

  // -----------------------------------------------------------------
  // AC-42 — un-mark purchased flow.
  // -----------------------------------------------------------------
  it('AC-42: unmarkPurchasedFlow — confirm → PATCH ACTIVE → moves to Active tab', async () => {
    const initial: ListWishlistResponse = {
      items: [makeItem({ wishlistId: 'w1', status: 'Purchased', purchasedAt: '2026-04-18T10:00:00' })],
      totalActive: 0,
      totalPurchased: 1,
    };
    (getWishlist as jest.Mock).mockResolvedValue(initial);
    (updateWishlistState as jest.Mock).mockResolvedValue(
      makeItem({ wishlistId: 'w1', status: 'Active', purchasedAt: null }),
    );
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined as any);

    const { findByText, getByTestId, queryByTestId } = renderScreen();
    await findByText('내 위시리스트');

    // Switch to Purchased tab to see the card.
    fireEvent.press(getByTestId('tab-btn-purchased'));
    await waitFor(() => expect(getByTestId('wishlist-card-w1')).toBeTruthy());

    await act(async () => { fireEvent.press(getByTestId('btn-unmark-purchased-w1')); });
    await act(async () => { await confirmAlert(alertSpy); });

    expect(updateWishlistState).toHaveBeenCalledWith(
      expect.objectContaining({ wishlistId: 'w1', status: 'ACTIVE' }),
    );
    await waitFor(() => {
      expect(getByTestId('tab-content-active')).toBeTruthy();
      expect(getByTestId('wishlist-card-w1')).toBeTruthy();
    });
    fireEvent.press(getByTestId('tab-btn-purchased'));
    expect(queryByTestId('wishlist-card-w1')).toBeNull();

    alertSpy.mockRestore();
  });

  // -----------------------------------------------------------------
  // AC-43 — delete flow.
  // -----------------------------------------------------------------
  it('AC-43: deleteFlow — confirm → DELETE → card disappears from both tabs', async () => {
    (getWishlist as jest.Mock).mockResolvedValue({
      items: [makeItem({ wishlistId: 'w1' })],
      totalActive: 1,
      totalPurchased: 0,
    });
    (deleteWishlistItem as jest.Mock).mockResolvedValue(undefined);
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined as any);

    const { findByText, getByTestId, queryByTestId } = renderScreen();
    await findByText('내 위시리스트');

    await act(async () => { fireEvent.press(getByTestId('btn-delete-w1')); });
    expect(alertSpy).toHaveBeenLastCalledWith(
      '위시리스트에서 삭제할까요?',
      undefined,
      expect.arrayContaining([expect.objectContaining({ text: '확인' })]),
    );
    await act(async () => { await confirmAlert(alertSpy); });

    expect(deleteWishlistItem).toHaveBeenCalledTimes(1);
    expect(deleteWishlistItem).toHaveBeenCalledWith(
      expect.objectContaining({ wishlistId: 'w1' }),
    );
    await waitFor(() => expect(queryByTestId('wishlist-card-w1')).toBeNull());

    alertSpy.mockRestore();
  });

  // -----------------------------------------------------------------
  // AC-44 — empty state copy on each tab.
  // -----------------------------------------------------------------
  it('AC-44: emptyStates — active empty + purchased empty texts', async () => {
    (getWishlist as jest.Mock).mockResolvedValue({
      items: [],
      totalActive: 0,
      totalPurchased: 0,
    });

    const { findByText, getByText, getByTestId } = renderScreen();
    await findByText('내 위시리스트');
    expect(getByText('아직 저장된 가구가 없어요.')).toBeTruthy();

    fireEvent.press(getByTestId('tab-btn-purchased'));
    expect(getByText('아직 구매 완료로 표시된 가구가 없어요.')).toBeTruthy();
  });

  // -----------------------------------------------------------------
  // AC-45 — loading state.
  // -----------------------------------------------------------------
  it('AC-45: loadingState — spinner + 위시리스트를 불러오는 중이에요...', () => {
    let _resolveOuter: (v: ListWishlistResponse) => void = () => undefined;
    const never = new Promise<ListWishlistResponse>((resolve) => { _resolveOuter = resolve; });
    (getWishlist as jest.Mock).mockReturnValue(never);

    const { getByTestId, getByText } = renderScreen();
    expect(getByTestId('wishlist-loading')).toBeTruthy();
    expect(getByText('위시리스트를 불러오는 중이에요...')).toBeTruthy();
  });

  // -----------------------------------------------------------------
  // AC-46 — error state + retry + WISHLIST_ITEM_NOT_FOUND on mutation.
  // -----------------------------------------------------------------
  it('AC-46a: errorState — rejected fetch shows error text + 다시 시도', async () => {
    (getWishlist as jest.Mock).mockRejectedValue(new Error('boom'));
    const { findByText, getByTestId } = renderScreen();
    await findByText('위시리스트를 불러오지 못했어요.');
    expect(getByTestId('btn-wishlist-retry')).toBeTruthy();

    // Tap retry → fetch called again.
    (getWishlist as jest.Mock).mockResolvedValueOnce({
      items: [], totalActive: 0, totalPurchased: 0,
    });
    await act(async () => { fireEvent.press(getByTestId('btn-wishlist-retry')); });
    expect(getWishlist).toHaveBeenCalledTimes(2);
  });

  it('AC-46b: WISHLIST_ITEM_NOT_FOUND on PATCH → toast + refresh list', async () => {
    (getWishlist as jest.Mock).mockResolvedValue({
      items: [makeItem({ wishlistId: 'w1' })],
      totalActive: 1,
      totalPurchased: 0,
    });
    (updateWishlistState as jest.Mock).mockRejectedValue(
      new ApiError(404, { errorCode: 'WISHLIST_ITEM_NOT_FOUND', message: 'x', correlationId: 'y' }),
    );
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined as any);

    const { findByText, getByTestId } = renderScreen();
    await findByText('내 위시리스트');

    await act(async () => { fireEvent.press(getByTestId('btn-mark-purchased-w1')); });
    await act(async () => { await confirmAlert(alertSpy); });

    // Toast '이미 삭제된 항목이에요.' + refresh (second getWishlist call).
    expect(alertSpy).toHaveBeenCalledWith('', '이미 삭제된 항목이에요.');
    await waitFor(() => expect(getWishlist).toHaveBeenCalledTimes(2));

    alertSpy.mockRestore();
  });
});
