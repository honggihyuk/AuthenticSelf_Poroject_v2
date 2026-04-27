import {
  AI_STYLES,
  PREFERRED_STYLES,
  PREFERRED_STYLE_LABELS,
  isPreferredStyle,
  isStyle,
} from '../src/types/style';

describe('Shared style types (Task-4 FR-20 / AC-27)', () => {
  it('exports exactly the five AI-output values', () => {
    expect([...AI_STYLES]).toEqual([
      'MODERN', 'SIMPLE', 'CLASSIC', 'SCANDINAVIAN', 'INDUSTRIAL',
    ]);
  });

  it('exports exactly the six user-choice values, CURRENT first', () => {
    expect([...PREFERRED_STYLES]).toEqual([
      'CURRENT', 'MODERN', 'SIMPLE', 'CLASSIC', 'SCANDINAVIAN', 'INDUSTRIAL',
    ]);
  });

  it('has Korean labels for all six options', () => {
    expect(PREFERRED_STYLE_LABELS).toEqual({
      CURRENT:      '현재 디자인 그대로',
      MODERN:       '모던',
      SIMPLE:       '심플',
      CLASSIC:      '클래식',
      SCANDINAVIAN: '스칸디나비안',
      INDUSTRIAL:   '인더스트리얼',
    });
  });

  it('CURRENT is a valid PreferredStyle but NOT a valid AI Style', () => {
    expect(isPreferredStyle('CURRENT')).toBe(true);
    expect(isStyle('CURRENT')).toBe(false);
  });

  it('isStyle accepts all five AI values', () => {
    for (const v of AI_STYLES) {
      expect(isStyle(v)).toBe(true);
      expect(isPreferredStyle(v)).toBe(true);
    }
  });

  it('rejects unknown values', () => {
    expect(isStyle('BAROQUE')).toBe(false);
    expect(isPreferredStyle('BAROQUE')).toBe(false);
  });
});
