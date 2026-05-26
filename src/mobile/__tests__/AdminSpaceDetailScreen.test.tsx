/**
 * Tests for AdminSpaceDetailScreen (UC-ML-PERSIST AC-11, AC-12).
 *
 * Mocks `getAdminSpaceDetail` so the screen's network surface is under test
 * control. Verifies the bounding-box overlay renders one box per persisted
 * detection (AC-11), and that a NULL `aiDetections` envelope renders the
 * photo with zero boxes + an empty-state hint without crashing (AC-12).
 */

import React from 'react';
import { render, waitFor } from '@testing-library/react-native';

jest.mock('../src/api/admin', () => ({
  ...jest.requireActual('../src/api/admin'),
  getAdminSpaceDetail: jest.fn(),
}));

import { getAdminSpaceDetail, AdminSpaceDetail } from '../src/api/admin';
import AdminSpaceDetailScreen from '../src/screens/AdminSpaceDetailScreen';

function renderScreen() {
  const goBack = jest.fn();
  const navigation: any = { goBack, navigate: jest.fn(), replace: jest.fn() };
  return {
    goBack,
    ...render(
      <AdminSpaceDetailScreen
        navigation={navigation}
        route={
          {
            key: 'AdminSpaceDetail',
            name: 'AdminSpaceDetail',
            params: { roomId: 'r1', photoUri: 'file:///tmp/r1.jpg' },
          } as any
        }
      />,
    ),
  };
}

function detail(overrides: Partial<AdminSpaceDetail> = {}): AdminSpaceDetail {
  return {
    roomId: 'r1',
    status: 'ANALYZED',
    mainColor: '#E8D9B0',
    aiDetections: null,
    ...overrides,
  };
}

describe('AdminSpaceDetailScreen (UC-ML-PERSIST AC-11/AC-12)', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('AC-11: renders one bounding box per persisted detection with label + confidence%', async () => {
    (getAdminSpaceDetail as jest.Mock).mockResolvedValue(
      detail({
        aiDetections: {
          imageWidth: 1280,
          imageHeight: 960,
          detections: [
            { label: 'chair', bbox: [128, 96, 256, 192], confidence: 0.91 },
            { label: 'bed', bbox: [600, 200, 1240, 900], confidence: 0.74 },
          ],
        },
      }),
    );

    const { getByTestId, queryByTestId, getByText } = renderScreen();

    // One box per detection (rendered as soon as the fetch resolves).
    await waitFor(() => expect(getByTestId('bbox-0')).toBeTruthy());
    expect(getByTestId('bbox-1')).toBeTruthy();

    // No third box; labels carry label + confidence%. RN splits the
    // interpolated Text into segments, so match the full composed string.
    expect(queryByTestId('bbox-2')).toBeNull();
    expect(getByText(/chair/)).toBeTruthy();
    expect(getByText(/91%/)).toBeTruthy();
    expect(getByText(/bed/)).toBeTruthy();
    expect(getByText(/74%/)).toBeTruthy();
    // No empty-state hint when detections exist.
    expect(queryByTestId('detection-empty')).toBeNull();
  }, 20000);

  it('AC-12: NULL ai_detections → photo with zero boxes + empty-state hint, no crash', async () => {
    (getAdminSpaceDetail as jest.Mock).mockResolvedValue(detail({ aiDetections: null }));

    const { getByTestId, queryByTestId } = renderScreen();

    await waitFor(() => getByTestId('detection-overlay'));

    // Empty-state hint present; no boxes drawn.
    expect(getByTestId('detection-empty')).toBeTruthy();
    expect(queryByTestId('bbox-0')).toBeNull();
    // Photo still renders.
    expect(getByTestId('detection-photo')).toBeTruthy();
  });

  it('AC-12: empty detections array also renders the empty-state hint', async () => {
    (getAdminSpaceDetail as jest.Mock).mockResolvedValue(
      detail({ aiDetections: { imageWidth: 1280, imageHeight: 960, detections: [] } }),
    );

    const { getByTestId, queryByTestId } = renderScreen();

    await waitFor(() => getByTestId('detection-empty'));
    expect(queryByTestId('bbox-0')).toBeNull();
  });
});
