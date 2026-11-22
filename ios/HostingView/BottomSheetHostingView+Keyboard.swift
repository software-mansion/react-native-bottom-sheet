import UIKit

/// Keyboard avoidance, opt-in through `keyboardAvoidance`.
///
/// The keyboard is treated as the new bottom of the screen: when a text input
/// inside the sheet is focused, the sheet container is lifted with the
/// keyboard's own animation (same duration and curve, in the same UIKit
/// animation the keyboard runs in), so the card rides on top of it. The lift is
/// applied to the container's `center`, independent of the translation that
/// drags and settles drive, so neither has to know about the other.
///
/// The card's top never rises above its detent's top inset (the status bar by
/// default). Whatever the capped lift leaves hidden behind the keyboard is
/// added to the bottom content inset of the scroll view that holds the focused
/// input, and the input is scrolled above the keyboard in the same animation.
extension BottomSheetHostingView {
  /// Space kept between a revealed input's bottom and the keyboard's top.
  static let keyboardRevealGap: CGFloat = 16

  func startObservingKeyboard() {
    let center = NotificationCenter.default
    center.addObserver(
      self, selector: #selector(keyboardWillChangeFrame(_:)),
      name: UIResponder.keyboardWillChangeFrameNotification, object: nil
    )
    center.addObserver(
      self, selector: #selector(keyboardWillHide(_:)),
      name: UIResponder.keyboardWillHideNotification, object: nil
    )
    center.addObserver(
      self, selector: #selector(textInputDidBeginEditing(_:)),
      name: UITextField.textDidBeginEditingNotification, object: nil
    )
    center.addObserver(
      self, selector: #selector(textInputDidBeginEditing(_:)),
      name: UITextView.textDidBeginEditingNotification, object: nil
    )
  }

  func stopObservingKeyboard() {
    let center = NotificationCenter.default
    center.removeObserver(self, name: UIResponder.keyboardWillChangeFrameNotification, object: nil)
    center.removeObserver(self, name: UIResponder.keyboardWillHideNotification, object: nil)
    center.removeObserver(self, name: UITextField.textDidBeginEditingNotification, object: nil)
    center.removeObserver(self, name: UITextView.textDidBeginEditingNotification, object: nil)
    keyboardScreenFrame = nil
    applyKeyboardAvoidance(animation: nil)
  }

  // MARK: Notifications

  @objc func keyboardWillChangeFrame(_ notification: Notification) {
    guard let info = notification.userInfo,
          let frame = (info[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue)?.cgRectValue
    else { return }
    keyboardScreenFrame = frame
    applyKeyboardAvoidance(animation: Self.keyboardAnimation(from: info))
  }

  @objc func keyboardWillHide(_ notification: Notification) {
    keyboardScreenFrame = nil
    applyKeyboardAvoidance(animation: notification.userInfo.map(Self.keyboardAnimation(from:)))
  }

  /// Moving focus between inputs while the keyboard stays up posts no keyboard
  /// notification (or one with an unchanged frame), so reveal the new input
  /// here, with a short ease like the keyboard's own. On the first focus the
  /// keyboard is not up yet and its notification handles it.
  @objc func textInputDidBeginEditing(_ notification: Notification) {
    guard let input = notification.object as? UIView, input.isDescendant(of: clipContainer),
          keyboardOverlap > 0
    else { return }
    applyKeyboardAvoidance(animation: Self.focusChangeAnimation)
  }

  /// Re-fits the lift after the sheet's target height changed (a new index or
  /// a resized `content` detent) while the keyboard is up, eased like a settle.
  func keyboardTargetDidChange() {
    guard keyboardAvoidance, keyboardLift > 0 || keyboardInsetAmount > 0 else { return }
    // Coalesced: detents and the index often change in the same update.
    guard !isKeyboardRefitScheduled else { return }
    isKeyboardRefitScheduled = true
    DispatchQueue.main.async { [weak self] in
      guard let self else { return }
      self.isKeyboardRefitScheduled = false
      let duration = self.settleDuration > 0 ? self.settleDuration : Self.defaultSettleDuration
      self.applyKeyboardAvoidance(
        animation: KeyboardAnimation(duration: duration, options: [], usesSpring: true),
        revealsFocusedInput: false
      )
    }
  }

  // MARK: Geometry

  /// The reveal when focus moves between inputs: the keyboard's own duration
  /// and curve.
  static let focusChangeAnimation = KeyboardAnimation(
    duration: 0.25,
    options: UIView.AnimationOptions(rawValue: UInt(7) << 16)
  )

  struct KeyboardAnimation {
    var duration: TimeInterval
    var options: UIView.AnimationOptions
    var usesSpring = false
  }

  private static func keyboardAnimation(from info: [AnyHashable: Any]) -> KeyboardAnimation {
    let duration = (info[UIResponder.keyboardAnimationDurationUserInfoKey] as? NSNumber)?
      .doubleValue ?? 0.25
    let curve = (info[UIResponder.keyboardAnimationCurveUserInfoKey] as? NSNumber)?
      .uintValue ?? UInt(UIView.AnimationCurve.easeInOut.rawValue)
    return KeyboardAnimation(
      duration: duration, options: UIView.AnimationOptions(rawValue: curve << 16)
    )
  }

  /// How far the keyboard covers this view from its bottom, in points.
  var keyboardOverlap: CGFloat {
    guard let frame = keyboardScreenFrame, !frame.isEmpty, let window else { return 0 }
    let inWindow = window.convert(frame, from: window.screen.coordinateSpace)
    let inHost = convert(inWindow, from: window)
    guard inHost.minX < bounds.maxX, inHost.maxX > bounds.minX else { return 0 }
    return min(max(0, bounds.maxY - inHost.minY), bounds.height)
  }

  /// The first responder inside the sheet's content, if it is a text input.
  private func focusedTextInput() -> UIView? {
    func search(_ view: UIView) -> UIView? {
      if view.isFirstResponder, view is UITextField || view is UITextView {
        return view
      }
      for subview in view.subviews {
        if let found = search(subview) {
          return found
        }
      }
      return nil
    }
    return search(clipContainer)
  }

  /// The scroll view that scrolls `input`: its nearest scroll view ancestor
  /// inside the sheet. A text view is itself a scroll view, so it is skipped.
  private func enclosingScrollView(of input: UIView) -> UIScrollView? {
    var current = input.superview
    while let view = current, view !== clipContainer {
      if let scrollView = view as? UIScrollView, !(view is UITextView) {
        return scrollView
      }
      current = view.superview
    }
    return nil
  }

  /// Points of this view per point of `view`'s own coordinates: the content is
  /// scaled into narrower cards.
  private func scale(of view: UIView) -> CGFloat {
    let height = view.convert(CGRect(x: 0, y: 0, width: 0, height: 100), to: self).height
    return height > 0 ? height / 100 : 1
  }

  /// Lifts the sheet above the keyboard, insets the focused input's scroll view
  /// by what stays covered, and scrolls the input into view; or undoes all of
  /// it once no input in the sheet is focused or the keyboard is gone.
  func applyKeyboardAvoidance(animation: KeyboardAnimation?, revealsFocusedInput: Bool = true) {
    guard hasLaidOut || keyboardLift > 0 || keyboardInsetAmount > 0 else { return }
    let input = keyboardAvoidance ? focusedTextInput() : nil
    let overlap = input != nil ? keyboardOverlap : 0

    // The card keeps its bottom inset above the keyboard, as it does above the
    // screen's bottom, shifted by `keyboardOffset` (negative tucks it behind).
    let wanted = overlap > 0 ? max(0, overlap + keyboardOffset) : 0
    var room = wanted
    if wanted > 0, detentSpecs.indices.contains(targetIndex) {
      let minTop = rawDetentSpecs.indices.contains(targetIndex)
        ? (rawDetentSpecs[targetIndex].insets.top ?? statusBarOverlap)
        : statusBarOverlap
      room = max(0, sheetContainerHeight - detent(at: targetIndex).position - minTop)
    }
    let lift = min(wanted, room)

    // Scroll view inset: what of it stays below the line the card's bottom
    // would have reached uncapped.
    let scrollView = input.flatMap(enclosingScrollView(of:))
    var inset: CGFloat = 0
    if let scrollView, let superview = scrollView.superview, overlap > 0 {
      let frame = superview.convert(scrollView.frame, to: self)
      // Positions after the lift: this view's coordinates follow the model
      // layers, which still hold the current lift.
      let bottomAfterLift = frame.maxY - (lift - keyboardLift)
      let cardBottomTarget = bounds.height - overlap - keyboardOffset
        - (rawDetentSpecs.indices.contains(targetIndex)
          ? rawDetentSpecs[targetIndex].insets.bottom : 0)
      let covered = max(0, bottomAfterLift - cardBottomTarget)
      inset = covered / scale(of: scrollView)
    }

    let previousScrollView = keyboardInsetScrollView
    let changesInset = previousScrollView !== scrollView || abs(inset - keyboardInsetAmount) > 0.5
    guard abs(lift - keyboardLift) > 0.5 || changesInset || (revealsFocusedInput && input != nil)
    else { return }

    // Insets first, outside the animation: the reveal below scrolls within them.
    if changesInset {
      if let previousScrollView {
        setKeyboardInset(0, on: previousScrollView, removing: keyboardInsetAmount)
      }
      if let scrollView {
        setKeyboardInset(inset, on: scrollView, removing: previousScrollView === scrollView ? keyboardInsetAmount : 0)
      }
      keyboardInsetScrollView = scrollView
      keyboardInsetAmount = scrollView != nil ? inset : 0
    }

    let liftDelta = lift - keyboardLift
    var targetOffset: CGPoint?
    if let scrollView, let input, revealsFocusedInput {
      targetOffset = revealedOffset(of: input, in: scrollView, liftDelta: liftDelta, overlap: overlap)
    }
    // Scrolled past the end once the inset shrank (the keyboard left): clamp.
    var clampedOffsets: [(UIScrollView, CGPoint)] = []
    for candidate in [previousScrollView, scrollView].compactMap({ $0 }) {
      let maxY = max(
        -candidate.adjustedContentInset.top,
        candidate.contentSize.height + candidate.adjustedContentInset.bottom - candidate.bounds.height
      )
      if candidate.contentOffset.y > maxY + 0.5, candidate !== scrollView || targetOffset == nil {
        clampedOffsets.append((candidate, CGPoint(x: candidate.contentOffset.x, y: maxY)))
      }
    }

    keyboardLift = lift
    let changes = {
      self.sheetContainer.center = CGPoint(
        x: self.bounds.width / 2,
        y: self.bounds.height - self.sheetContainerHeight / 2 - lift
      )
      if let scrollView, let targetOffset {
        scrollView.contentOffset = targetOffset
      }
      for (candidate, offset) in clampedOffsets {
        candidate.contentOffset = offset
      }
    }
    guard let animation, animation.duration > 0, window != nil else {
      UIView.performWithoutAnimation(changes)
      return
    }
    if animation.usesSpring {
      UIView.animate(
        withDuration: animation.duration, delay: 0, usingSpringWithDamping: 1,
        initialSpringVelocity: 0, options: [.beginFromCurrentState, .allowUserInteraction],
        animations: changes
      )
    } else {
      UIView.animate(
        withDuration: animation.duration, delay: 0,
        options: animation.options.union([.beginFromCurrentState, .allowUserInteraction]),
        animations: changes
      )
    }
  }

  private func setKeyboardInset(_ inset: CGFloat, on scrollView: UIScrollView, removing previous: CGFloat) {
    var content = scrollView.contentInset
    content.bottom = max(0, content.bottom - previous + inset)
    scrollView.contentInset = content
    var indicators = scrollView.verticalScrollIndicatorInsets
    indicators.bottom = max(0, indicators.bottom - previous + inset)
    scrollView.verticalScrollIndicatorInsets = indicators
  }

  /// The content offset that brings `input` above the keyboard once the sheet
  /// has moved by `liftDelta`, or nil when it is already visible.
  private func revealedOffset(
    of input: UIView, in scrollView: UIScrollView, liftDelta: CGFloat, overlap: CGFloat
  ) -> CGPoint? {
    let frame = input.convert(input.bounds, to: self)
    let limit = bounds.height - overlap - Self.keyboardRevealGap
    let overshoot = frame.maxY - liftDelta - limit
    guard overshoot > 0.5 else { return nil }
    let maxY = max(
      -scrollView.adjustedContentInset.top,
      scrollView.contentSize.height + scrollView.adjustedContentInset.bottom
        - scrollView.bounds.height
    )
    let y = min(scrollView.contentOffset.y + overshoot / scale(of: scrollView), maxY)
    guard y > scrollView.contentOffset.y + 0.5 else { return nil }
    return CGPoint(x: scrollView.contentOffset.x, y: y)
  }
}
