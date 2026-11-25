import Foundation

/// Offset to apply after scaling around the view's laid-out center.
func bottomAnchorTranslation(
  cardBottom: CGFloat, centerY: CGFloat, height: CGFloat, scale: CGFloat
) -> CGFloat {
  cardBottom - (centerY + scale * height / 2)
}
