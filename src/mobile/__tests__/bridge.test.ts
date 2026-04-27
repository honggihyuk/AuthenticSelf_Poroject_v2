/**
 * Tests for the AR message bridge (AR-furniture-placement AC-19 / AC-43).
 *
 * Pure unit coverage — no WebView, no RN runtime. Every throw branch of
 * `decodeOutbound` + every encoder branch of `encodeInbound` is
 * exercised.
 */

import {
  AR_BRIDGE_VERSION,
  decodeOutbound,
  emitArFurniturePlaced,
  encodeInbound,
  setArAnalyticsSink,
  type ArAnalyticsEvent,
  type InboundARMessage,
  type OutboundARErrorCode,
} from '../src/ar/bridge';

// ---------------------------------------------------------------------------
// AR_BRIDGE_VERSION — literal 1 (FR-3 / NFR bridge version stability)
// ---------------------------------------------------------------------------

describe('AR_BRIDGE_VERSION', () => {
  it('is literal 1', () => {
    expect(AR_BRIDGE_VERSION).toBe(1);
  });
});

// ---------------------------------------------------------------------------
// encodeInbound — load + unload (AC-19)
// ---------------------------------------------------------------------------

describe('encodeInbound', () => {
  it('encodes a load message round-trip safe', () => {
    const load: InboundARMessage = {
      bridgeVersion: AR_BRIDGE_VERSION,
      event: 'load',
      furnitureId: 'f_desk_001',
      modelUrl: 'asset:///ar/models/desk.glb',
      roomDimensions: { widthM: 3.6, lengthM: 4.2, heightM: 2.4 },
      colorHex: null,
    };
    const encoded = encodeInbound(load);
    const reparsed = JSON.parse(encoded);
    expect(reparsed).toEqual(load);
  });

  it('encodes an unload message', () => {
    const unload: InboundARMessage = {
      bridgeVersion: AR_BRIDGE_VERSION,
      event: 'unload',
    };
    expect(JSON.parse(encodeInbound(unload))).toEqual(unload);
  });

  it('rejects bad bridgeVersion', () => {
    expect(() =>
      encodeInbound({
        bridgeVersion: 99 as typeof AR_BRIDGE_VERSION,
        event: 'unload',
      }),
    ).toThrow(/bridgeVersion/);
  });

  it('rejects unknown event', () => {
    expect(() =>
      encodeInbound({
        bridgeVersion: AR_BRIDGE_VERSION,
        event: 'bogus' as 'unload',
      } as InboundARMessage),
    ).toThrow(/unknown event/);
  });
});

// ---------------------------------------------------------------------------
// decodeOutbound — valid inputs (AC-19)
// ---------------------------------------------------------------------------

describe('decodeOutbound — happy paths', () => {
  it('parses placed with numeric pose', () => {
    const raw = JSON.stringify({
      bridgeVersion: 1,
      event: 'placed',
      pose: { x: 1.1, y: 0, z: -0.5, yaw: 1.5707 },
    });
    const msg = decodeOutbound(raw);
    expect(msg).toEqual({
      bridgeVersion: 1,
      event: 'placed',
      pose: { x: 1.1, y: 0, z: -0.5, yaw: 1.5707 },
    });
  });

  it('parses cancelled', () => {
    const raw = JSON.stringify({ bridgeVersion: 1, event: 'cancelled' });
    expect(decodeOutbound(raw)).toEqual({
      bridgeVersion: 1,
      event: 'cancelled',
    });
  });

  it.each<OutboundARErrorCode>([
    'AR_NOT_SUPPORTED',
    'AR_CAMERA_DENIED',
    'AR_MODEL_LOAD_FAILED',
    'AR_SESSION_ERROR',
  ])('parses error with code %s', (code) => {
    const raw = JSON.stringify({
      bridgeVersion: 1,
      event: 'error',
      errorCode: code,
      message: `detail for ${code}`,
    });
    const parsed = decodeOutbound(raw);
    expect(parsed).toMatchObject({
      bridgeVersion: 1,
      event: 'error',
      errorCode: code,
      message: `detail for ${code}`,
    });
  });

  it('parses error without optional message field', () => {
    const raw = JSON.stringify({
      bridgeVersion: 1,
      event: 'error',
      errorCode: 'AR_NOT_SUPPORTED',
    });
    const parsed = decodeOutbound(raw);
    expect(parsed).toEqual({
      bridgeVersion: 1,
      event: 'error',
      errorCode: 'AR_NOT_SUPPORTED',
    });
  });
});

// ---------------------------------------------------------------------------
// decodeOutbound — rejection paths (AC-43)
// ---------------------------------------------------------------------------

describe('decodeOutbound — rejects malformed input', () => {
  it('throws on empty string', () => {
    expect(() => decodeOutbound('')).toThrow();
  });

  it('throws on invalid JSON', () => {
    expect(() => decodeOutbound('not json')).toThrow(/invalid JSON/);
  });

  it('throws on non-object (null)', () => {
    expect(() => decodeOutbound('null')).toThrow();
  });

  it('throws on non-object (array)', () => {
    // JSON.parse('[]') is an object (typeof === 'object') — but obj.bridgeVersion is undefined.
    expect(() => decodeOutbound('[]')).toThrow(/bridgeVersion mismatch/);
  });

  it('throws on mismatched bridgeVersion', () => {
    expect(() =>
      decodeOutbound(
        JSON.stringify({ bridgeVersion: 2, event: 'cancelled' }),
      ),
    ).toThrow(/bridgeVersion mismatch/);
  });

  it('throws on unknown event name', () => {
    expect(() =>
      decodeOutbound(
        JSON.stringify({ bridgeVersion: 1, event: 'placedx' }),
      ),
    ).toThrow(/unknown event/);
  });

  it('throws on placed with missing pose', () => {
    expect(() =>
      decodeOutbound(
        JSON.stringify({ bridgeVersion: 1, event: 'placed' }),
      ),
    ).toThrow(/missing pose/);
  });

  it('throws on placed with non-numeric pose axis', () => {
    expect(() =>
      decodeOutbound(
        JSON.stringify({
          bridgeVersion: 1,
          event: 'placed',
          pose: { x: 0, y: 0, z: 0, yaw: 'nope' },
        }),
      ),
    ).toThrow(/pose/);
  });

  it('throws on error with unknown code', () => {
    expect(() =>
      decodeOutbound(
        JSON.stringify({
          bridgeVersion: 1,
          event: 'error',
          errorCode: 'AR_ALIEN_INVASION',
        }),
      ),
    ).toThrow(/errorCode/);
  });

  it('throws on error missing code', () => {
    expect(() =>
      decodeOutbound(
        JSON.stringify({ bridgeVersion: 1, event: 'error' }),
      ),
    ).toThrow(/errorCode/);
  });
});

// ---------------------------------------------------------------------------
// emitArFurniturePlaced — sink swap (AC-30)
// ---------------------------------------------------------------------------

describe('emitArFurniturePlaced / setArAnalyticsSink', () => {
  it('invokes the swapped sink exactly once with the given args', () => {
    const seen: ArAnalyticsEvent[] = [];
    setArAnalyticsSink((ev) => seen.push(ev));

    emitArFurniturePlaced('r1', 'f_desk_001', { x: 0, y: 0, z: 0, yaw: 0 });

    expect(seen).toHaveLength(1);
    expect(seen[0]).toEqual({
      type: 'ar_furniture_placed',
      payload: {
        roomId: 'r1',
        furnitureId: 'f_desk_001',
        pose: { x: 0, y: 0, z: 0, yaw: 0 },
      },
    });

    // Reset to no-op so sibling tests don't leak.
    setArAnalyticsSink(() => undefined);
  });
});
