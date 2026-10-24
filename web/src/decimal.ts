/** The API accepts decimal strings, not JavaScript scientific notation. */
export function decimalString(value: number): string {
  if (!Number.isFinite(value)) throw new Error('Invalid decimal value');
  const raw = String(value);
  if (!/[eE]/.test(raw)) return raw;
  const [coefficient, exponent] = raw.split('e');
  const sign = coefficient.startsWith('-') ? '-' : '';
  const unsigned = coefficient.replace(/^-/, '');
  const digits = unsigned.replace('.', '');
  const point = unsigned.split('.')[0].length + Number(exponent);
  if (point <= 0) return `${sign}0.${'0'.repeat(-point)}${digits}`;
  if (point >= digits.length) return sign + digits + '0'.repeat(point - digits.length);
  return sign + digits.slice(0, point) + '.' + digits.slice(point);
}
