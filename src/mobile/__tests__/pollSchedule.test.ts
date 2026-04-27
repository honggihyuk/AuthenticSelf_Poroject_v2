import {
  POLL_BUDGET_MS,
  POLL_INITIAL_MS,
  POLL_MAX_INTERVAL_MS,
  delayForAttempt,
} from '../src/api/pollSchedule';

describe('pollSchedule (Task-4 FR-19 / NFR)', () => {
  it('starts at 1s, multiplies by 1.5, caps at 10s', () => {
    expect(delayForAttempt(1)).toBe(POLL_INITIAL_MS);
    expect(delayForAttempt(2)).toBe(1500);
    expect(delayForAttempt(3)).toBe(2250);
    expect(delayForAttempt(4)).toBeCloseTo(3375, -1);
    expect(delayForAttempt(5)).toBeCloseTo(5062, -1);
    expect(delayForAttempt(6)).toBeCloseTo(7594, -1);
    expect(delayForAttempt(7)).toBe(POLL_MAX_INTERVAL_MS);
    expect(delayForAttempt(8)).toBe(POLL_MAX_INTERVAL_MS);
    expect(delayForAttempt(20)).toBe(POLL_MAX_INTERVAL_MS);
  });

  it('is non-decreasing across attempts (AC-31)', () => {
    const series = Array.from({ length: 20 }, (_, i) => delayForAttempt(i + 1));
    for (let i = 1; i < series.length; i++) {
      expect(series[i]).toBeGreaterThanOrEqual(series[i - 1]);
    }
  });

  it('every interval is ≤ 10 000 ms (AC-31)', () => {
    for (let i = 1; i <= 50; i++) {
      expect(delayForAttempt(i)).toBeLessThanOrEqual(POLL_MAX_INTERVAL_MS);
    }
  });

  it('budget is 60 000 ms (AC-32)', () => {
    expect(POLL_BUDGET_MS).toBe(60_000);
  });
});
