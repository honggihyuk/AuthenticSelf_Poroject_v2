/**
 * AR message bridge (AR-furniture-placement FR-3 / AC-9 / AC-19 / AC-43).
 *
 * Typed, versioned message contract between the React Native host
 * (`ARPlacementScreen`) and the embedded HTML scene (`placement.html`).
 *
 * Inbound messages flow RN -> scene via
 * `WebView.injectJavaScript("window.__authenticSelfBridgeLoad(<json>)")`.
 *
 * Outbound messages flow scene -> RN via
 * `window.ReactNativeWebView.postMessage(JSON.stringify(msg))` and are
 * received by the WebView's `onMessage` prop as a JSON string.
 *
 * Every message carries a literal `bridgeVersion: 1` so future breaking
 * changes are detected by the decoder instead of silently misbehaving.
 *
 * Hand-rolled narrowing matches the convention established by
 * `assertWishlistItem` / `assertRecommendationResponse` — no runtime
 * schema library.
 */
// ---------------------------------------------------------------------------
// Version + error-code enumerations
// ---------------------------------------------------------------------------

export const AR_BRIDGE_VERSION = 1 as const;

/**
 * Client-side error codes emitted by the HTML scene.
 *
 * These are NOT backend `errorCode` values — the scene does not make
 * HTTP calls, so the shared `ErrorResponse` envelope does not apply.
 * `ARPlacementScreen.tsx` maps each code to a Korean copy via
 * `AR_ERROR_COPY`.
 */
export type OutboundARErrorCode =
  | 'AR_NOT_SUPPORTED'
  | 'AR_CAMERA_DENIED'
  | 'AR_MODEL_LOAD_FAILED'
  | 'AR_SESSION_ERROR';

// ---------------------------------------------------------------------------
// Inbound — RN -> scene
// ---------------------------------------------------------------------------

export type InboundARLoadMessage = {
  bridgeVersion: typeof AR_BRIDGE_VERSION;
  event: 'load';
  furnitureId: string;
  modelUrl: string;
  roomDimensions: { widthM: number; lengthM: number; heightM: number } | null;
  colorHex: string | null;
};

export type InboundARUnloadMessage = {
  bridgeVersion: typeof AR_BRIDGE_VERSION;
  event: 'unload';
};

export type InboundARMessage = InboundARLoadMessage | InboundARUnloadMessage;

// ---------------------------------------------------------------------------
// Outbound — scene -> RN
// ---------------------------------------------------------------------------

export type OutboundARPlacedMessage = {
  bridgeVersion: typeof AR_BRIDGE_VERSION;
  event: 'placed';
  pose: { x: number; y: number; z: number; yaw: number };
};

export type OutboundARCancelledMessage = {
  bridgeVersion: typeof AR_BRIDGE_VERSION;
  event: 'cancelled';
};

export type OutboundARErrorMessage = {
  bridgeVersion: typeof AR_BRIDGE_VERSION;
  event: 'error';
  errorCode: OutboundARErrorCode;
  message?: string;
};

export type OutboundARMessage =
  | OutboundARPlacedMessage
  | OutboundARCancelledMessage
  | OutboundARErrorMessage;

// ---------------------------------------------------------------------------
// Inbound encoder — always pure, always JSON
// ---------------------------------------------------------------------------

/**
 * JSON-stringify an inbound message for transport through
 * `WebView.injectJavaScript`.
 *
 * Deterministic: no Date/Math usage; identical input -> identical output.
 * AC-19 exercises both branches (load + unload).
 */
export function encodeInbound(msg: InboundARMessage): string {
  if (msg.bridgeVersion !== AR_BRIDGE_VERSION) {
    throw new Error(`encodeInbound: bridgeVersion must be ${AR_BRIDGE_VERSION}`);
  }
  if (msg.event !== 'load' && msg.event !== 'unload') {
    // Exhaustiveness guard — TypeScript will have already refused but a
    // runtime guard protects JS callers too.
    throw new Error(`encodeInbound: unknown event=${(msg as { event: string }).event}`);
  }
  return JSON.stringify(msg);
}

// ---------------------------------------------------------------------------
// Outbound decoder — rejects malformed inputs (AC-19 / AC-43)
// ---------------------------------------------------------------------------

const ERROR_CODES: ReadonlyArray<OutboundARErrorCode> = [
  'AR_NOT_SUPPORTED',
  'AR_CAMERA_DENIED',
  'AR_MODEL_LOAD_FAILED',
  'AR_SESSION_ERROR',
];

function isErrorCode(v: unknown): v is OutboundARErrorCode {
  return typeof v === 'string' && (ERROR_CODES as ReadonlyArray<string>).includes(v);
}

/**
 * Parse + shape-narrow a raw outbound payload from the scene.
 *
 * Throws on:
 *  - non-JSON input
 *  - missing or mismatched `bridgeVersion`
 *  - unknown `event` name
 *  - missing event-specific fields (e.g. `placed` without `pose`)
 *
 * AC-43 covers every throw branch.
 */
export function decodeOutbound(raw: string): OutboundARMessage {
  if (typeof raw !== 'string' || raw.length === 0) {
    throw new Error('decodeOutbound: raw payload is empty');
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch (e) {
    throw new Error(`decodeOutbound: invalid JSON — ${(e as Error).message}`);
  }
  if (!parsed || typeof parsed !== 'object') {
    throw new Error('decodeOutbound: payload is not an object');
  }
  const obj = parsed as Record<string, unknown>;

  if (obj.bridgeVersion !== AR_BRIDGE_VERSION) {
    throw new Error(
      `decodeOutbound: bridgeVersion mismatch — got ${String(obj.bridgeVersion)}, expected ${AR_BRIDGE_VERSION}`,
    );
  }

  switch (obj.event) {
    case 'placed': {
      const pose = obj.pose as Record<string, unknown> | undefined;
      if (!pose || typeof pose !== 'object') {
        throw new Error('decodeOutbound: placed event missing pose');
      }
      if (
        typeof pose.x !== 'number' ||
        typeof pose.y !== 'number' ||
        typeof pose.z !== 'number' ||
        typeof pose.yaw !== 'number'
      ) {
        throw new Error('decodeOutbound: placed pose missing x/y/z/yaw numbers');
      }
      return {
        bridgeVersion: AR_BRIDGE_VERSION,
        event: 'placed',
        pose: {
          x: pose.x,
          y: pose.y,
          z: pose.z,
          yaw: pose.yaw,
        },
      };
    }
    case 'cancelled': {
      return {
        bridgeVersion: AR_BRIDGE_VERSION,
        event: 'cancelled',
      };
    }
    case 'error': {
      if (!isErrorCode(obj.errorCode)) {
        throw new Error(
          `decodeOutbound: error event has invalid errorCode=${String(obj.errorCode)}`,
        );
      }
      const message = typeof obj.message === 'string' ? obj.message : undefined;
      return {
        bridgeVersion: AR_BRIDGE_VERSION,
        event: 'error',
        errorCode: obj.errorCode,
        ...(message !== undefined ? { message } : {}),
      };
    }
    default: {
      throw new Error(`decodeOutbound: unknown event=${String(obj.event)}`);
    }
  }
}

// ---------------------------------------------------------------------------
// Analytics stub (FR-7 AC-30)
//
// Shape mirrors `emitWishlistAddClicked` in `spaces.ts`. Lives alongside
// the bridge because the "placed" event is inherently an AR-layer
// concern. The sink is swappable so tests can spy without globals.
// ---------------------------------------------------------------------------

export type ArAnalyticsEvent = {
  type: 'ar_furniture_placed';
  payload: {
    roomId: string;
    furnitureId: string;
    pose: { x: number; y: number; z: number; yaw: number };
  };
};

type ArAnalyticsSink = (ev: ArAnalyticsEvent) => void;

let arAnalyticsSink: ArAnalyticsSink = () => {
  // default — no-op in production; tests override via setArAnalyticsSink.
};

export function setArAnalyticsSink(sink: ArAnalyticsSink): void {
  arAnalyticsSink = sink;
}

/**
 * Emit the `ar_furniture_placed` event exactly once per committed
 * placement. Called by `ARPlacementScreen` in its `placed`-handler
 * BEFORE the Alert is shown so analytics fire regardless of wishlist
 * choice.
 */
export function emitArFurniturePlaced(
  roomId: string,
  furnitureId: string,
  pose: { x: number; y: number; z: number; yaw: number },
): void {
  arAnalyticsSink({
    type: 'ar_furniture_placed',
    payload: { roomId, furnitureId, pose },
  });
}
