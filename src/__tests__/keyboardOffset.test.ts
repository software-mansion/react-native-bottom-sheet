/// <reference types="bun" />

import { beforeAll, describe, expect, mock, test } from 'bun:test';

mock.module('react-native', () => ({
  I18nManager: { isRTL: false, doLeftAndRightSwapInRTL: true },
  Platform: { OS: 'ios' },
  processColor: (color: unknown) => color,
  StyleSheet: { flatten: (style: unknown) => style },
}));

let validateKeyboardOffset: typeof import('../bottomSheetUtils').validateKeyboardOffset;

beforeAll(async () => {
  ({ validateKeyboardOffset } = await import('../bottomSheetUtils'));
});

describe('validateKeyboardOffset', () => {
  test('accepts unset, zero, positive and negative offsets', () => {
    expect(() => validateKeyboardOffset(undefined)).not.toThrow();
    expect(() => validateKeyboardOffset(0)).not.toThrow();
    expect(() => validateKeyboardOffset(12)).not.toThrow();
    expect(() => validateKeyboardOffset(-40)).not.toThrow();
  });

  test('rejects non-finite offsets', () => {
    expect(() => validateKeyboardOffset(NaN)).toThrow(/keyboardOffset/);
    expect(() => validateKeyboardOffset(Infinity)).toThrow(/keyboardOffset/);
  });
});
