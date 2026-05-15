/**
 * IKEA-inspired palette: warm white surface, navy primary, sparingly used
 * yellow accent, charcoal body text. Naming is semantic so screens can
 * stay readable even if the underlying hex changes.
 */
export const palette = {
  white: '#FFFFFF',
  cream: '#FAF8F4',
  sand:  '#F2EDE3',
  border:'#E5DFD3',
  divider:'#EFEAE0',

  charcoal: '#111111',
  ink:      '#1F1D1A',
  text:     '#2A2824',
  textMuted:'#6B6963',
  textFaint:'#9A958C',

  navy:        '#0058A3',
  navyDark:    '#003E73',
  navySoft:    '#E6F0FA',

  yellow:      '#FFDA1A',
  yellowDark:  '#E5B800',

  green:       '#007A33',
  greenSoft:   '#E5F1EA',

  red:         '#CC0008',
  redSoft:     '#FBE6E7',
} as const;

export const colors = {
  background:       palette.cream,
  surface:          palette.white,
  surfaceAlt:       palette.sand,
  border:           palette.border,
  divider:          palette.divider,

  textPrimary:      palette.charcoal,
  textSecondary:    palette.text,
  textMuted:        palette.textMuted,
  textFaint:        palette.textFaint,
  textInverse:      palette.white,

  primary:          palette.navy,
  primaryPressed:   palette.navyDark,
  primarySoft:      palette.navySoft,
  onPrimary:        palette.white,

  accent:           palette.yellow,
  accentPressed:    palette.yellowDark,
  onAccent:         palette.charcoal,

  inStock:          palette.green,
  inStockSoft:      palette.greenSoft,

  danger:           palette.red,
  dangerSoft:       palette.redSoft,
} as const;

export type Colors = typeof colors;
