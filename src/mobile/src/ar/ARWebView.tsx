/**
 * ARWebView — thin wrapper around `react-native-webview`.
 *
 * The wrapper exists so tests can mock this single module without
 * depending on `react-native-webview`'s native runtime. Tests replace
 * this file via `jest.mock('../src/ar/ARWebView', ...)` and expose a
 * synchronous `emit(raw: string)` helper that invokes whatever
 * `onMessage` callback the parent registered — see
 * `ARPlacementScreen.test.tsx` for the mock shape.
 *
 * Runtime behaviour (production):
 *  - render a <WebView>
 *  - translate the WebView's `{ nativeEvent: { data } }` payload into
 *    a parsed `OutboundARMessage` via `decodeOutbound`
 *  - expose an imperative `inject(raw: string)` via forwardRef so the
 *    parent can push inbound messages through `injectJavaScript`
 *
 * AC-21 asserts the WebView host node has testID `ar-webview`.
 */

import React, {
  forwardRef,
  useCallback,
  useImperativeHandle,
  useRef,
} from 'react';
import type { ComponentType } from 'react';
import { StyleSheet, View } from 'react-native';

import type { OutboundARMessage } from './bridge';
import { decodeOutbound } from './bridge';

// ---------------------------------------------------------------------------
// react-native-webview import — deferred so Jest environments without the
// native module don't explode at import time. Tests mock this whole
// module, so the production branch is exercised only at runtime.
// ---------------------------------------------------------------------------

/* eslint-disable @typescript-eslint/no-var-requires */
type WebViewLikeProps = {
  ref?: React.Ref<unknown>;
  source: { uri: string } | { html: string };
  originWhitelist?: string[];
  onMessage?: (ev: { nativeEvent: { data: string } }) => void;
  onError?: (ev: unknown) => void;
  javaScriptEnabled?: boolean;
  domStorageEnabled?: boolean;
  mediaPlaybackRequiresUserAction?: boolean;
  allowsInlineMediaPlayback?: boolean;
  style?: Record<string, unknown>;
  testID?: string;
};
type WebViewLike = ComponentType<WebViewLikeProps> & {
  new (...args: unknown[]): {
    injectJavaScript: (script: string) => void;
  };
};

let WebViewImpl: WebViewLike | null = null;
try {
  // Deferred require so Jest / web environments without the native
  // module don't crash at file load. The production RN runtime always
  // resolves this synchronously.
  // eslint-disable-next-line @typescript-eslint/no-require-imports, global-require
  WebViewImpl = require('react-native-webview').WebView as WebViewLike;
} catch {
  WebViewImpl = null;
}
/* eslint-enable @typescript-eslint/no-var-requires */

// ---------------------------------------------------------------------------
// Public prop + ref shape
// ---------------------------------------------------------------------------

export type ARWebViewProps = {
  /** Bundled HTML asset URI. */
  sourceUri: string;
  /** Parsed outbound message. Invalid payloads are routed via `onDecodeError`. */
  onMessage: (msg: OutboundARMessage) => void;
  /** Called when decodeOutbound throws — lets the parent surface AR_SESSION_ERROR. */
  onDecodeError?: (raw: string, err: Error) => void;
  /** Testable identity. */
  testID?: string;
};

export type ARWebViewHandle = {
  /** Inject a bridge-encoded inbound JSON into the scene. */
  inject: (bridgeJson: string) => void;
};

// ---------------------------------------------------------------------------
// Implementation
// ---------------------------------------------------------------------------

const ARWebView = forwardRef<ARWebViewHandle, ARWebViewProps>(function ARWebView(
  props,
  ref,
) {
  const webviewRef = useRef<{
    injectJavaScript: (s: string) => void;
  } | null>(null);

  useImperativeHandle(
    ref,
    (): ARWebViewHandle => ({
      inject(bridgeJson: string) {
        try {
          const wv = webviewRef.current;
          if (!wv) return;
          // Wrap the JSON in a call to the scene-side inbound hook.
          // JSON.stringify-of-a-string produces a safe JS string literal.
          const script =
            'try { if (window.__authenticSelfBridgeLoad) { ' +
            'window.__authenticSelfBridgeLoad(' +
            JSON.stringify(bridgeJson) +
            '); } } catch (e) {} true;';
          wv.injectJavaScript(script);
        } catch {
          // Swallow — teardown cleanliness (FR-10) requires no-throw.
        }
      },
    }),
    [],
  );

  const handleMessage = useCallback(
    (ev: { nativeEvent: { data: string } }) => {
      const raw = ev?.nativeEvent?.data ?? '';
      try {
        const msg = decodeOutbound(raw);
        props.onMessage(msg);
      } catch (e) {
        props.onDecodeError?.(raw, e as Error);
      }
    },
    [props],
  );

  if (!WebViewImpl) {
    // Jest / web fallback — render an empty host so tests can assert
    // testID presence. In production this branch is unreachable.
    return <View testID={props.testID} style={styles.fill} />;
  }

  const WV = WebViewImpl as unknown as ComponentType<WebViewLikeProps>;

  return (
    <WV
      ref={webviewRef as unknown as React.Ref<unknown>}
      source={{ uri: props.sourceUri }}
      originWhitelist={['*']}
      javaScriptEnabled
      domStorageEnabled
      mediaPlaybackRequiresUserAction={false}
      allowsInlineMediaPlayback
      onMessage={handleMessage}
      style={styles.fill}
      testID={props.testID}
    />
  );
});

const styles = StyleSheet.create({
  fill: { flex: 1, backgroundColor: '#111' },
});

export default ARWebView;
