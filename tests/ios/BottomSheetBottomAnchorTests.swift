import CoreGraphics
import Foundation

@main
struct BottomSheetBottomAnchorTests {
  static func main() {
    assertAnchor(cardBottom: 400, frame: CGRect(x: 0, y: 0, width: 400, height: 800), scale: 1, expected: -400)
    assertAnchor(cardBottom: 800, frame: CGRect(x: 0, y: 0, width: 400, height: 800), scale: 1, expected: 0)
    assertAnchor(cardBottom: 400, frame: CGRect(x: 8, y: 20, width: 400, height: 800), scale: 0.95, expected: -400)
    assertAnchor(cardBottom: 700, frame: CGRect(x: 8, y: 0, width: 400, height: 800), scale: 0.9, expected: -60)
  }

  private static func assertAnchor(
    cardBottom: CGFloat, frame: CGRect, scale: CGFloat, expected: CGFloat
  ) {
    let actual = bottomAnchorTranslation(
      cardBottom: cardBottom, centerY: frame.midY, height: frame.height, scale: scale
    )
    precondition(abs(actual - expected) < 0.001, "Unexpected translation: \(actual)")
  }
}
