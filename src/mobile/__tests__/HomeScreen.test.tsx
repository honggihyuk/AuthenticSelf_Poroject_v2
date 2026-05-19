import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';

import HomeScreen from '../src/screens/HomeScreen';
import { AuthProvider } from '../src/auth/AuthContext';

const withAuth = (children: React.ReactElement) => (
  <AuthProvider>{children}</AuthProvider>
);

/**
 * AC-17 — a button with exact visible label "방 사진 하나로 가구 추천"
 * exists and navigates to the `Upload` route when pressed.
 */
describe('HomeScreen (AC-17)', () => {
  it('renders the CTA with the exact PRD label', () => {
    const navigation: any = { navigate: jest.fn() };
    const { getByText } = render(
      withAuth(<HomeScreen navigation={navigation} route={{ key: 'Home', name: 'Home' } as any} />),
    );
    expect(getByText('방 사진 하나로 가구 추천')).toBeTruthy();
  });

  it('navigates to Upload on CTA press', () => {
    const navigate = jest.fn();
    const navigation: any = { navigate };
    const { getByText } = render(
      withAuth(<HomeScreen navigation={navigation} route={{ key: 'Home', name: 'Home' } as any} />),
    );
    fireEvent.press(getByText('방 사진 하나로 가구 추천'));
    expect(navigate).toHaveBeenCalledWith('Upload');
  });

  // UC-02-wishlist AC-48 — additive "위시리스트 보기" button navigates to
  // the Wishlist screen. Primary CTA is unchanged by this test.
  it('UC-02 AC-48: opensWishlist — 위시리스트 보기 navigates to Wishlist', () => {
    const navigate = jest.fn();
    const navigation: any = { navigate };
    const { getByTestId, getByText } = render(
      withAuth(<HomeScreen navigation={navigation} route={{ key: 'Home', name: 'Home' } as any} />),
    );

    // Both buttons coexist.
    expect(getByText('방 사진 하나로 가구 추천')).toBeTruthy();

    const btn = getByTestId('btn-open-wishlist');
    fireEvent.press(btn);
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith('Wishlist');
  });
});
