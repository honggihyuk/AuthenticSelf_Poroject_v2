import React from 'react';
import { act, fireEvent, render, waitFor } from '@testing-library/react-native';

import UploadScreen from '../src/screens/UploadScreen';

/**
 * AC-18 — picking a valid image and tapping "업로드" renders a progress
 * indicator during upload and navigates to `Analyzing` with the returned
 * roomId. The API client and ImagePicker are mocked.
 */
jest.mock('expo-image-picker', () => ({
  MediaTypeOptions: { Images: 'Images' },
  launchImageLibraryAsync: jest.fn().mockResolvedValue({
    canceled: false,
    assets: [{
      uri:       'file:///tmp/room.jpg',
      fileName:  'room.jpg',
      mimeType:  'image/jpeg',
      fileSize:  2_000_000,
    }],
  }),
}));

jest.mock('../src/api/client', () => ({
  uploadPhoto: jest.fn(),
  UploadFailedError: class UploadFailedError extends Error {
    httpStatus: number;
    body: any;
    constructor(status: number, body: any) {
      super('upload failed');
      this.httpStatus = status;
      this.body = body;
    }
  },
}));

import { uploadPhoto } from '../src/api/client';

describe('UploadScreen (AC-18)', () => {
  it('shows progress during upload and navigates to Analyzing with roomId', async () => {
    let resolveUpload: (v: any) => void = () => {};
    (uploadPhoto as jest.Mock).mockImplementation(({ onProgress }: any) => {
      onProgress?.(25);
      return new Promise((resolve) => { resolveUpload = resolve; });
    });
    const replace = jest.fn();
    const navigation: any = { replace };

    const { getByTestId } = render(
      <UploadScreen
        navigation={navigation}
        route={{ key: 'Upload', name: 'Upload' } as any}
      />,
    );

    // pick
    await act(async () => { fireEvent.press(getByTestId('btn-pick')); });

    // submit
    await act(async () => { fireEvent.press(getByTestId('btn-upload')); });

    // progress indicator visible while in-flight
    expect(getByTestId('upload-progress')).toBeTruthy();

    // resolve the upload and assert navigation
    await act(async () => {
      resolveUpload({ roomId: 'r1', uploadUrl: 'file:///x.jpg', status: 'PENDING_ANALYSIS' });
    });
    await waitFor(() => {
      expect(replace).toHaveBeenCalledWith(
        'Analyzing',
        expect.objectContaining({ roomId: 'r1' }),
      );
    });
  });
});
