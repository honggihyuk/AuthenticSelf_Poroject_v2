/**
 * Exponential backoff schedule for `AnalyzingScreen` (Task-4 FR-19 / NFR
 * "RN polling" / AC-31 / AC-32 / AC-33).
 *
 * The schedule is:
 *   [1000, 1500, 2250, 3375, 5062, 7594, 10000, 10000, 10000, ...]
 *
 * - Multiplier 1.5 up to a 10 000 ms cap per interval.
 * - Total wall-clock budget 60 000 ms from the first poll.
 * - Intervals are non-decreasing (AC-31) and each ≤ 10 000 ms.
 */

export const POLL_INITIAL_MS = 1000;
export const POLL_MULTIPLIER = 1.5;
export const POLL_MAX_INTERVAL_MS = 10_000;
export const POLL_BUDGET_MS = 60_000;

/**
 * Compute the delay before the `nthAttempt`-th poll (0-indexed).
 *
 * `nth=0` is the first poll itself — in the screen we fire the first poll
 * on mount, then wait `delayForAttempt(1)` ms before the second poll,
 * `delayForAttempt(2)` ms before the third, and so on. Capped at
 * {@link POLL_MAX_INTERVAL_MS}.
 */
export function delayForAttempt(nth: number): number {
  if (nth <= 0) return 0;
  // After the first poll, the first inter-poll interval is POLL_INITIAL_MS.
  const raw = POLL_INITIAL_MS * Math.pow(POLL_MULTIPLIER, nth - 1);
  return Math.min(Math.round(raw), POLL_MAX_INTERVAL_MS);
}
