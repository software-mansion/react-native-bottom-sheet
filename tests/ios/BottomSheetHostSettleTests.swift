import UIKit

private final class AnchoredSlot: UIView {
  @objc var nativeId: String { BottomSheetHostingView.bottomAnchoredContentNativeID }
}

@main
struct BottomSheetHostSettleTests {
  @MainActor static func main() {
    let host = BottomSheetHostingView(frame: CGRect(x: 0, y: 0, width: 400, height: 800))
    host.sheetContainer.frame = host.bounds
    host.detentSpecs = [400, 800].map {
      DetentSpec(position: $0, programmatic: false, insets: DetentInsets())
    }
    let slot = AnchoredSlot(frame: host.bounds)
    let list = UIScrollView(frame: slot.bounds)
    slot.addSubview(list)
    host.mountChildComponentView(slot, atIndex: 0)
    for translation: CGFloat in [0, 400] {
      host.setSheetTranslation(translation)
      precondition(list.bounds.height == 800)
      precondition(slot.convert(slot.bounds, to: host).maxY == 800)
    }

    let state = ActiveScrollViewState(scrollView: list, inverted: false, wasAtStartWhenTouched: true)
    host.fitScrollIndicators(of: [state])
    precondition(list.verticalScrollIndicatorInsets.top == 400 && list.verticalScrollIndicatorInsets.bottom == 0)
    host.refitScrollIndicators(atTranslation: 100)
    precondition(list.verticalScrollIndicatorInsets.top == 100 && list.verticalScrollIndicatorInsets.bottom == 0)
    host.releaseScrollIndicatorFits()
    list.alwaysBounceVertical = true
    host.activeSpring = CriticalSpring(from: 100, target: 400, v0: 0, omega: 1,
                                       startTime: CACurrentMediaTime() + 10, duration: 8)
    let point = CGPoint(x: 120, y: 200)
    precondition(host.hitTest(point, with: nil) === list)
    let chain = host.scrollableAncestorChain(containing: host.convert(point, to: host.sheetContainer))
    precondition(chain.first?.scrollView === list)
    host.activeSpring = nil

    host.bottomAnchorTopInsets = [56, 104]
    host.setSheetTranslation(400)
    precondition(host.hitTest(CGPoint(x: 120, y: 440), with: nil) !== list)
    precondition(host.hitTest(CGPoint(x: 120, y: 480), with: nil) === list)
    host.fitScrollIndicators(of: [state])
    precondition(list.verticalScrollIndicatorInsets.top == 456)
    host.releaseScrollIndicatorFits()

    host.detentSpecs.append(host.detentSpecs[1])
    host.bottomAnchorTopInsets = [56, 12, 104]

    let spring = CAKeyframeAnimation(keyPath: "transform.translation.y")
    let translations: [CGFloat] = [0, 10, 30, 400]
    spring.values = translations
    spring.keyTimes = [0, 0.05, 0.1, 1]
    spring.duration = 1
    spring.beginTime = CACurrentMediaTime()
    host.addCardAnimations(matching: spring)
    guard let animation = slot.layer.animation(forKey: BottomSheetHostingView.cardContentAnimationKey)
      as? CAKeyframeAnimation, let transforms = animation.values as? [NSValue],
      let clip = host.clipContainer.layer.animation(forKey: BottomSheetHostingView.cardBoundsAnimationKey)
      as? CAKeyframeAnimation, let rects = clip.values as? [NSValue],
      let anchorClip = host.bottomAnchorViewport.layer.animation(forKey: BottomSheetHostingView.cardBoundsAnimationKey)
      as? CAKeyframeAnimation, let anchors = anchorClip.values as? [NSValue]
    else { fatalError("Missing slot or clip keyframes") }
    for track in [animation, clip, anchorClip] {
      precondition(track.keyTimes == spring.keyTimes && track.duration == spring.duration)
      precondition(track.beginTime == spring.beginTime)
    }
    precondition(transforms.count == translations.count && rects.count == translations.count)
    for index in translations.indices {
      let transform = transforms[index].caTransform3DValue
      let bottom = slot.center.y + slot.bounds.height / 2 * transform.m22 + transform.m42
      let anchor = anchors[index].cgRectValue
      precondition(bottom == rects[index].cgRectValue.maxY && bottom + translations[index] == 800)
      precondition(anchor.minY == 104 - 48 * translations[index] / 400 && anchor.maxY == bottom)
    }

    host.removeSettleAnimations()
    host.setSheetTranslation(150)
    precondition(slot.layer.animation(forKey: BottomSheetHostingView.cardContentAnimationKey) == nil)
    precondition(slot.convert(slot.bounds, to: host).maxY == 800)
    precondition(host.bottomAnchorViewport.bounds.minY == 86)
    spring.values = [150, 80, 0]
    spring.keyTimes = [0, 0.5, 1]
    host.addCardAnimations(matching: spring)
    host.unmountChildComponentView(slot)
    precondition(slot.layer.animationKeys() == nil && slot.layer.transform.m42 == 0)
    precondition(host.bottomAnchorViewport.superview == nil && !host.clipContainer.clipsToBounds)
    host.mountChildComponentView(slot, atIndex: 0)
    precondition(slot.layer.animation(forKey: BottomSheetHostingView.cardContentAnimationKey) != nil)
    host.resetSheetState()
    precondition(slot.superview == nil && CATransform3DIsIdentity(slot.layer.transform))
    precondition(host.bottomAnchorTopInsets.isEmpty && host.bottomAnchoredContentView == nil)
  }
}
