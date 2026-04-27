/**
 * Barrel export for the AR module (AR-furniture-placement).
 *
 * Kept tiny on purpose — downstream code should import named items
 * from this barrel rather than reaching into sub-files, so a future
 * refactor (e.g. moving `bridge.ts` into `bridge/` with split files)
 * is a non-event.
 */

export {
  AR_BRIDGE_VERSION,
  encodeInbound,
  decodeOutbound,
  emitArFurniturePlaced,
  setArAnalyticsSink,
} from './bridge';

export type {
  InboundARMessage,
  InboundARLoadMessage,
  InboundARUnloadMessage,
  OutboundARMessage,
  OutboundARPlacedMessage,
  OutboundARCancelledMessage,
  OutboundARErrorMessage,
  OutboundARErrorCode,
  ArAnalyticsEvent,
} from './bridge';

export { default as ARWebView } from './ARWebView';
export type { ARWebViewHandle, ARWebViewProps } from './ARWebView';
