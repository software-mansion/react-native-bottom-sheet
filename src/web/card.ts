/// <reference lib="dom" />

import { columnWidth } from '../cardPlacement';
import {
  cardSpan,
  contentScale,
  firstOpenPosition,
  type SideInsets,
} from './detents';
import { clamp, processedColorToCss } from './dom';
import type {
  BottomSheetWebConfig,
  BottomSheetWebElements,
  RawDetent,
  ResolvedDetent,
} from './types';

/** The controller's state that the card's shape derives from. */
export type CardHost = {
  readonly elements: BottomSheetWebElements | null;
  readonly config: BottomSheetWebConfig | null;
  readonly hostHeight: number;
  readonly translateY: number;
  readonly detents: ResolvedDetent[];
  readonly rawDetents: ReadonlyArray<RawDetent>;
  readonly widestCardLeft: number;
  readonly widestCardInsets: SideInsets;
};

const NO_INSETS = { insetLeft: 0, insetRight: 0, insetBottom: 0 };

export class SheetCard {
  /** The card's CSS corner radii, even while none is rounded. */
  private cardCorners = '';
  private hasDetentCornerRadii = false;
  private cornerRadiiDirty = true;
  private appliedCornerRadii = [NaN, NaN, NaN, NaN];
  private appliedContentTransform: string | null = null;
  private appliedCardHeight = NaN;
  private appliedCardLeft = NaN;
  private appliedCardRight = NaN;
  private appliedClipInset = NaN;

  constructor(private readonly host: CardHost) {}

  resetContentTransform() {
    this.appliedContentTransform = null;
    this.appliedCornerRadii.fill(NaN);
  }

  /**
   * Applies the card's shadow style and invalidates its cached geometry.
   */
  updateCardShape() {
    const { elements, config } = this.host;
    if (elements === null || config === null) return;
    const { shadow } = elements;
    this.cornerRadiiDirty = true;
    this.hasDetentCornerRadii = config.detents.some(
      (detent) => (detent.cornerRadius ?? -1) >= 0
    );
    shadow.style.boxShadow = config.shadows
      .map(
        ({ offsetX, offsetY, blurRadius, spreadDistance, color }) =>
          `${offsetX}px ${offsetY}px ${blurRadius}px ${spreadDistance}px ${processedColorToCss(color)}`
      )
      .join(', ');
    this.appliedCardHeight = NaN;
    this.updateCardClip();
  }

  /**
   * Clips the surface and content to the card. At or above the first open
   * detent the card's bottom stays above the bottom gap and its side gaps blend
   * between detents; below it, it keeps that detent's shape and slides down
   * rigidly, so a closing sheet leaves through the gap with its shape intact.
   * Cards with square corners and no gaps stay unclipped.
   */
  updateCardClip() {
    const hostState = this.host;
    const elements = hostState.elements;
    if (elements === null || hostState.config === null) return;
    const { card, content, host, shadow, surface } = elements;
    const position = this.currentPosition();
    const insets = this.cardInsets(position);
    const bottom = insets.insetBottom;
    const span = cardSpan(hostState, insets, host.clientWidth);
    const left = span.left;
    const right = host.clientWidth - span.left - span.width;
    const cardHeight = clamp(position - bottom, 0, hostState.hostHeight);
    const clipInset = hostState.hostHeight - cardHeight;

    // Scale the surface and content to the card's width, with their top-left
    // corners on the card's. The transform origin is the box's, which starts at
    // the container's left, so the layout margin scales along. Ahead of the
    // check below: the widest card can change while this card stays the same.
    const scale = contentScale(hostState, insets);
    const transform =
      scale === 1 && left === hostState.widestCardLeft
        ? ''
        : `translateX(${left - scale * hostState.widestCardLeft}px) scale(${scale})`;
    if (transform !== this.appliedContentTransform) {
      this.appliedContentTransform = transform;
      surface.style.transform = transform;
      content.style.transform = transform;
    }

    const cornersChanged = this.updateCornerRadii(position);
    if (
      !cornersChanged &&
      cardHeight === this.appliedCardHeight &&
      clipInset === this.appliedClipInset &&
      left === this.appliedCardLeft &&
      right === this.appliedCardRight
    ) {
      return;
    }
    this.appliedCardHeight = cardHeight;
    this.appliedClipInset = clipInset;
    this.appliedCardLeft = left;
    this.appliedCardRight = right;

    shadow.style.height = `${cardHeight}px`;
    shadow.style.left = `${left}px`;
    shadow.style.right = `${right}px`;
    card.style.clipPath = this.clipsToCard()
      ? `inset(0px ${right}px ${clipInset}px ${left}px round ${this.cardCorners})`
      : '';
  }

  private updateCornerRadii(position: number) {
    if (!this.cornerRadiiDirty && !this.hasDetentCornerRadii) return false;
    const { config, detents, elements } = this.host;
    if (config === null || elements === null) return false;
    this.cornerRadiiDirty = false;
    let lowerRadius = -1;
    let upperRadius = -1;
    let progress = 0;
    if (this.hasDetentCornerRadii && detents.length > 0) {
      const upper = detents.findIndex((detent) => position < detent.position);
      const lower = upper < 0 ? detents.length - 1 : Math.max(upper - 1, 0);
      lowerRadius = this.cornerStyleAt(lower)?.cornerRadius ?? -1;
      if (upper > lower) {
        const span = detents[upper]!.position - detents[lower]!.position;
        const target = this.cornerStyleAt(upper);
        const distance = target?.cornerRadiusTransitionDistance || span;
        upperRadius = target?.cornerRadius ?? -1;
        progress = clamp(
          1 - (detents[upper]!.position - position) / Math.min(distance, span),
          0,
          1
        );
      }
    }
    let changed = false;
    for (let corner = 0; corner < 4; corner++) {
      const baseline = config.cornerRadii[corner] ?? 0;
      const from = lowerRadius < 0 ? baseline : lowerRadius;
      const to = upperRadius < 0 ? baseline : upperRadius;
      const radius = from + (to - from) * progress;
      if (this.appliedCornerRadii[corner] !== radius) {
        this.appliedCornerRadii[corner] = radius;
        changed = true;
      }
    }
    if (changed) {
      this.cardCorners = this.appliedCornerRadii
        .map((radius) => `${radius}px`)
        .join(' ');
      elements.shadow.style.borderRadius = this.cardCorners;
    }
    return changed;
  }

  private cornerStyleAt(index: number) {
    const { detents, config } = this.host;
    const position = detents[index]!.position;
    let first = index;
    while (first > 0 && detents[first - 1]!.position === position) first--;
    for (let member = first; detents[member]?.position === position; member++) {
      const style = config?.detents[member];
      if ((style?.cornerRadius ?? -1) >= 0) return style;
    }
    return config?.detents[first];
  }

  private currentPosition() {
    return Math.max(
      this.host.hostHeight - this.host.translateY,
      firstOpenPosition(this.host.detents)
    );
  }

  /** The height the card has right now, in layout units. */
  currentLayoutHeight() {
    const hostState = this.host;
    const position = this.currentPosition();
    const insets = this.cardInsets(position);
    const height = clamp(
      position - insets.insetBottom,
      0,
      hostState.hostHeight
    );
    const scale = contentScale(hostState, insets);
    return scale > 0 ? height / scale : height;
  }

  /**
   * The card's gaps at a sheet position, blended between the detents around it.
   * Resolved detents ascend by position.
   */
  private cardInsets(position: number) {
    const detents = this.host.detents;
    const upperIndex = detents.findIndex(
      (detent) => position <= detent.position
    );
    if (upperIndex <= 0)
      return detents[upperIndex] ?? detents[detents.length - 1] ?? NO_INSETS;
    const upper = detents[upperIndex]!;
    const lower = detents[upperIndex - 1]!;
    const span = upper.position - lower.position;
    const t = span <= 0 ? 1 : (position - lower.position) / span;
    const blend = (from: number, to: number) => from + (to - from) * t;
    return {
      insetLeft: blend(lower.insetLeft, upper.insetLeft),
      insetRight: blend(lower.insetRight, upper.insetRight),
      insetBottom: blend(lower.insetBottom, upper.insetBottom),
    };
  }

  /** Whether the card is cut out of the surface: rounded, or keeping gaps. */
  private clipsToCard() {
    return (
      this.hasDetentCornerRadii ||
      this.appliedCornerRadii.some((radius) => radius > 0) ||
      this.isNarrowedByMaxWidth() ||
      this.host.rawDetents.some(
        (detent) =>
          detent.insetLeft > 0 ||
          detent.insetRight > 0 ||
          detent.insetBottom > 0
      )
    );
  }

  /**
   * Whether `maxWidth` leaves a gap beside the sheet in this host: whether its
   * column is narrower than the host.
   */
  private isNarrowedByMaxWidth() {
    const width = this.host.elements?.host.clientWidth ?? 0;
    return columnWidth(width, this.host.rawDetents[0]?.maxWidth ?? 0) < width;
  }
}
