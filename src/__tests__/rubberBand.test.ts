/// <reference types="bun" />

import { describe, expect, test } from 'bun:test';

import { rubberBand } from '../web/dom';

describe('rubberBand', () => {
  test('follows at 0.55 at first and gives less with every pixel', () => {
    expect(rubberBand(1, 1000)).toBeCloseTo(0.55, 2);
    expect(rubberBand(200, 1000)).toBeLessThan(200 * 0.55);
  });

  test('never reaches the room above the detent', () => {
    expect(rubberBand(1e6, 400)).toBeLessThan(400);
  });

  test('does not move a sheet with no room above its detent', () => {
    expect(rubberBand(100, 0)).toBe(0);
  });
});
