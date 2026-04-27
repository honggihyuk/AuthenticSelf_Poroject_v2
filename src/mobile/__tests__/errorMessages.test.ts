import { errorMessages, messageForCode } from '../src/api/errorMessages';

/**
 * AC-19 — the mapping MUST expose every FR-6 errorCode key with a non-empty
 * Korean string. Also sanity-check the fallback behaviour.
 */
describe('errorMessages (AC-19)', () => {
  const requiredCodes = [
    'EMPTY_FILE',
    'MISSING_USER_HEADER',
    'UNKNOWN_USER',
    'UNSUPPORTED_MEDIA_TYPE',
    'FILE_TOO_LARGE',
    'IMAGE_UNREADABLE',
    'RESOLUTION_TOO_LOW',
    'STORAGE_PERSIST_FAILED',
  ] as const;

  it.each(requiredCodes)('exports a non-empty Korean message for %s', (code) => {
    const msg = (errorMessages as Record<string, string>)[code];
    expect(msg).toBeDefined();
    expect(msg.length).toBeGreaterThan(0);
    // At least one Hangul syllable — smoke check for Korean content.
    expect(/[\uAC00-\uD7A3]/.test(msg)).toBe(true);
  });

  it('messageForCode falls back for unknown codes', () => {
    expect(messageForCode('NOT_A_REAL_CODE')).toMatch(/[\uAC00-\uD7A3]/);
    expect(messageForCode(undefined)).toMatch(/[\uAC00-\uD7A3]/);
  });
});
