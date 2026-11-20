/// <reference lib="dom" />
/// <reference types="bun" />

import { beforeAll, describe, expect, mock, test } from 'bun:test';
import type { CardHost } from '../web/card';
import type { BottomSheetWebElements, RawDetent } from '../web/types';

mock.module('react-native', () => ({
  I18nManager: { isRTL: false, doLeftAndRightSwapInRTL: true },
  Platform: { OS: 'web' },
  processColor: (color: unknown) => color,
  StyleSheet: { flatten: (style: unknown) => style },
}));

let SheetCard: typeof import('../web/card').SheetCard;

beforeAll(async () => {
  ({ SheetCard } = await import('../web/card'));
});

const rawDetent = (
  value: number,
  cornerRadius = -1,
  cornerRadiusTransitionDistance = 0
): RawDetent => ({
  value,
  kind: 'points',
  programmatic: false,
  usesStatusBarTopInset: false,
  insetTop: 0,
  insetLeft: 0,
  insetRight: 0,
  insetBottom: 0,
  widestCard: true,
  maxWidth: 0,
  placementBias: 0.5,
  scrimOpacity: 1,
  cornerRadius,
  cornerRadiusTransitionDistance,
});

const createCard = (rawDetents: RawDetent[]) => {
  const elements = {
    host: { clientWidth: 400 },
    card: { style: {} },
    shadow: { style: {} },
    surface: { style: {} },
    content: { style: {} },
  } as BottomSheetWebElements;
  const host: CardHost & { translateY: number } = {
    elements,
    config: {
      detents: rawDetents,
      index: 1,
      animateIn: true,
      animateContentHeight: true,
      modal: false,
      hasCloseRequestHandler: false,
      openOrder: 0,
      scrollExpandCoordination: 2,
      scrollCollapseCoordination: 1,
      settleDuration: 0,
      cornerRadii: [32, 24, 16, 8],
      shadows: [],
    },
    hostHeight: 800,
    translateY: 800,
    detents: rawDetents.map((detent) => ({
      ...detent,
      position: detent.value,
    })),
    rawDetents,
    widestCardLeft: 0,
    widestCardInsets: { insetLeft: 0, insetRight: 0 },
  };
  const card = new SheetCard(host);
  card.updateCardShape();
  return {
    renderAt(position: number) {
      host.translateY = host.hostHeight - position;
      card.updateCardClip();
      return {
        radius: elements.shadow.style.borderRadius,
        clip: elements.card.style.clipPath,
      };
    },
  };
};

describe('web card corners', () => {
  beforeAll(() => {
    (globalThis as { window?: unknown }).window = { devicePixelRatio: 1 };
  });

  test('keeps the surface shape until the final 50 units, in either direction', () => {
    const card = createCard([
      rawDetent(0),
      rawDetent(400),
      rawDetent(800, 0, 50),
    ]);
    const baseline = '32px 24px 16px 8px';
    expect(card.renderAt(749).radius).toBe(baseline);
    expect(card.renderAt(750).radius).toBe(baseline);
    const midway = card.renderAt(775);
    expect(midway.radius).toBe('16px 12px 8px 4px');
    expect(midway.clip).toContain('round 16px 12px 8px 4px');
    const square = card.renderAt(800);
    expect(square.radius).toBe('0px 0px 0px 0px');
    expect(square.clip).toContain('round 0px 0px 0px 0px');
    expect(card.renderAt(775)).toEqual(midway);
  });

  test('uses the first explicit style throughout a coincident detent group', () => {
    for (const coincident of [
      [rawDetent(800, 0, 50), rawDetent(800)],
      [rawDetent(800), rawDetent(800, 0, 50)],
      [rawDetent(800, 0, 50), rawDetent(800, 12)],
    ]) {
      const card = createCard([rawDetent(0), rawDetent(400), ...coincident]);
      expect(card.renderAt(775).radius).toBe('16px 12px 8px 4px');
      expect(card.renderAt(800).radius).toBe('0px 0px 0px 0px');
      expect(card.renderAt(825).radius).toBe('0px 0px 0px 0px');
    }
  });

  test('caps a transition distance at the interval and retains the first open shape while closing', () => {
    const card = createCard([
      rawDetent(0),
      rawDetent(770, 32),
      rawDetent(800, 0, 50),
    ]);
    expect(card.renderAt(785).radius).toBe('16px 16px 16px 16px');
    expect(card.renderAt(100).radius).toBe('32px 32px 32px 32px');
  });
});
