/// <reference types="bun" />
/// <reference lib="dom" />

import { describe, expect, test } from 'bun:test';

import { applyScrim, type ScrimHost } from '../web/scrim';
import type { RawDetent, ResolvedDetent } from '../web/types';

const makeDetent = (position: number, scrimOpacity: number) => ({
  raw: {
    value: position,
    kind: 'points' as const,
    programmatic: false,
    usesStatusBarTopInset: false,
    insetTop: 0,
    insetLeft: 0,
    insetRight: 0,
    insetBottom: 0,
    widestCard: false,
    maxWidth: 0,
    placementBias: 0.5,
    scrimOpacity,
  } satisfies RawDetent,
  resolved: {
    position,
    programmatic: false,
    insetLeft: 0,
    insetRight: 0,
    insetBottom: 0,
  } satisfies ResolvedDetent,
});

const makeHost = (options: {
  detents: Array<{ position: number; scrimOpacity: number }>;
  targetIndex: number;
  reanchorScrimOpacity?: number;
  isSpringActive?: boolean;
}) => {
  const built = options.detents.map((d) =>
    makeDetent(d.position, d.scrimOpacity)
  );
  const scrimStyle = { opacity: '', visibility: '', pointerEvents: '' };
  const dismissStyle = { transform: '', visibility: '' };
  const host: ScrimHost = {
    elements: {
      scrim: { style: scrimStyle } as unknown as HTMLElement,
      scrimDismiss: {
        style: dismissStyle,
        setAttribute: () => {},
        removeAttribute: () => {},
      } as unknown as HTMLButtonElement,
    },
    config: {
      modal: true,
      detents: built.map((d) => d.raw),
    },
    hasLaidOut: true,
    isPanning: false,
    reanchorScrimOpacity: options.reanchorScrimOpacity ?? null,
    targetIndex: options.targetIndex,
    detents: built.map((d) => d.resolved),
    hostHeight: 900,
    canRequestCloseFromScrim: false,
    snapping: {
      isSpringActive: options.isSpringActive ?? true,
      isTargetClosed: () => built[options.targetIndex]?.resolved.position === 0,
    },
  };
  return { host, scrimStyle };
};

describe('applyScrim', () => {
  test('preserves the visible opacity when an open detent grows mid-settle', () => {
    const { host, scrimStyle } = makeHost({
      detents: [
        { position: 0, scrimOpacity: 0 },
        { position: 420, scrimOpacity: 1 },
      ],
      targetIndex: 1,
      reanchorScrimOpacity: 0.7,
    });

    // At 70pt out of 420pt raw opacity would dip to 0.167, but the floor holds 0.7.
    applyScrim(host, 70);
    expect(Number(scrimStyle.opacity)).toBeCloseTo(0.7, 5);

    // Once position passes the floor, scrim continues rising smoothly.
    applyScrim(host, 357);
    expect(Number(scrimStyle.opacity)).toBeCloseTo(0.85, 5);
  });

  test('lets the scrim fade to the target opacity when closing or lowering', () => {
    const closing = makeHost({
      detents: [
        { position: 0, scrimOpacity: 0 },
        { position: 420, scrimOpacity: 1 },
      ],
      targetIndex: 0,
      reanchorScrimOpacity: 0.7,
    });
    applyScrim(closing.host, 42);
    expect(Number(closing.scrimStyle.opacity)).toBeCloseTo(0.1, 5);

    const lowering = makeHost({
      detents: [
        { position: 0, scrimOpacity: 0 },
        { position: 300, scrimOpacity: 0.5 },
        { position: 600, scrimOpacity: 1 },
      ],
      targetIndex: 1,
      reanchorScrimOpacity: 0.8,
    });
    // Interpolating below the new target height must not undershoot its opacity.
    applyScrim(lowering.host, 150);
    expect(Number(lowering.scrimStyle.opacity)).toBeCloseTo(0.5, 5);
  });

  test('keeps a transparent middle detent transparent when the host shrinks', () => {
    const { host, scrimStyle } = makeHost({
      detents: [
        { position: 0, scrimOpacity: 0 },
        { position: 500, scrimOpacity: 0 },
        { position: 1000, scrimOpacity: 1 },
      ],
      targetIndex: 1,
      reanchorScrimOpacity: 0,
    });
    applyScrim(host, 960);
    expect(Number(scrimStyle.opacity)).toBe(0);
  });
});
