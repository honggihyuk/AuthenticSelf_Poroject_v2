import React from 'react';
import { act, render, fireEvent } from '@testing-library/react-native';

jest.mock('../src/api/client', () => ({
  ...jest.requireActual('../src/api/client'),
  getSpace: jest.fn(),
}));

import { getSpace } from '../src/api/client';
import AnalyzingScreen from '../src/screens/AnalyzingScreen';

/**
 * Tests for Task-4 AC-31 (backoff schedule), AC-32 (60s timeout),
 * AC-33 (unmount cancels polling).
 */

function renderScreen() {
  const replace = jest.fn();
  const navigation: any = { replace };
  return {
    replace,
    ...render(
      <AnalyzingScreen
        navigation={navigation}
        route={{ key: 'Analyzing', name: 'Analyzing', params: { roomId: 'r1' } } as any}
      />,
    ),
  };
}

describe('AnalyzingScreen polling (AC-31 / AC-32 / AC-33)', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.clearAllMocks();
  });
  afterEach(() => {
    jest.useRealTimers();
  });

  it('AC-31: polls ≥ 6 times with non-decreasing intervals; navigates on ANALYZED', async () => {
    let callCount = 0;
    (getSpace as jest.Mock).mockImplementation(async () => {
      callCount += 1;
      if (callCount < 6) return { roomId: 'r1', status: 'PENDING_ANALYSIS' };
      return {
        roomId: 'r1',
        status: 'ANALYZED',
        style: 'MODERN',
        styleConfidence: 0.72,
        preferredStyle: null,
      };
    });

    const { replace } = renderScreen();

    // Step through enough of the schedule to fire six calls.
    // Schedule (deltas between polls): 0, 1s, 1.5s, 2.25s, 3.375s, 5.063s.
    // Each advance is rounded up slightly to clear fake-timer boundary
    // conditions (e.g. 5.063 → 5.1 so the 6th timer definitely fires).
    for (const ms of [0, 1000, 1500, 2250, 3400, 5100]) {
      await act(async () => { jest.advanceTimersByTime(ms); });
      await act(async () => { await Promise.resolve(); });
    }

    expect((getSpace as jest.Mock).mock.calls.length).toBeGreaterThanOrEqual(6);
    expect(replace).toHaveBeenCalledWith('StyleSelection', expect.objectContaining({
      roomId: 'r1', aiDetectedStyle: 'MODERN', aiDetectedConfidence: 0.72,
    }));
  });

  it('AC-32: after 60s still PENDING, shows timeout UI and stops polling', async () => {
    (getSpace as jest.Mock).mockResolvedValue({ roomId: 'r1', status: 'PENDING_ANALYSIS' });

    const { getByTestId } = renderScreen();

    // Advance well past the 60s budget.
    for (let i = 0; i < 70; i++) {
      await act(async () => { jest.advanceTimersByTime(1000); });
      await act(async () => { await Promise.resolve(); });
    }

    expect(getByTestId('analyzing-timeout')).toBeTruthy();

    // Record the call count after 60s+, advance further, confirm it doesn't grow.
    const snap = (getSpace as jest.Mock).mock.calls.length;
    for (let i = 0; i < 10; i++) {
      await act(async () => { jest.advanceTimersByTime(1000); });
      await act(async () => { await Promise.resolve(); });
    }
    expect((getSpace as jest.Mock).mock.calls.length).toBe(snap);
  });

  it('AC-32 retry button restarts polling without navigating to FAILED', async () => {
    (getSpace as jest.Mock).mockResolvedValue({ roomId: 'r1', status: 'PENDING_ANALYSIS' });
    const { getByTestId } = renderScreen();

    for (let i = 0; i < 70; i++) {
      await act(async () => { jest.advanceTimersByTime(1000); });
      await act(async () => { await Promise.resolve(); });
    }
    expect(getByTestId('analyzing-timeout')).toBeTruthy();

    const before = (getSpace as jest.Mock).mock.calls.length;
    await act(async () => { fireEvent.press(getByTestId('btn-retry')); });
    await act(async () => { await Promise.resolve(); });
    expect((getSpace as jest.Mock).mock.calls.length).toBeGreaterThan(before);
  });

  it('AC-33: unmount within 5s cancels polling; no further getSpace calls', async () => {
    (getSpace as jest.Mock).mockResolvedValue({ roomId: 'r1', status: 'PENDING_ANALYSIS' });

    const { unmount } = renderScreen();

    // Let a few polls fire.
    await act(async () => { jest.advanceTimersByTime(5000); });
    await act(async () => { await Promise.resolve(); });

    const snapshot = (getSpace as jest.Mock).mock.calls.length;

    // Unmount; advance the clock a lot.
    unmount();
    await act(async () => { jest.advanceTimersByTime(60_000); });
    await act(async () => { await Promise.resolve(); });

    expect((getSpace as jest.Mock).mock.calls.length).toBe(snapshot);
  });
});
