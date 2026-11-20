import Foundation

struct DetentCornerRadius: Equatable {
  let radius: CGFloat?
  let distance: CGFloat
}

func resolveDetentCornerRadii(
  position: CGFloat,
  detents: [DetentSpec],
  styles: [DetentCornerRadius],
  surface: [CGFloat]
) -> [CGFloat] {
  guard !detents.isEmpty else { return surface }
  let upper = detents.firstIndex { position < $0.position }
  let lower = upper.map { max(0, $0 - 1) } ?? (detents.count - 1)
  let from = cornerStyle(at: lower, detents: detents, styles: styles)?.radius
  guard let upper, upper > lower else {
    return from.map { Array(repeating: $0, count: 4) } ?? surface
  }
  let target = cornerStyle(at: upper, detents: detents, styles: styles)
  let span = detents[upper].position - detents[lower].position
  let distance = target?.distance ?? 0
  let window = distance > 0 ? min(span, distance) : span
  let progress = min(1, max(0, 1 - (detents[upper].position - position) / window))
  return surface.map { baseline in
    let start = from ?? baseline
    return start + ((target?.radius ?? baseline) - start) * progress
  }
}

private func cornerStyle(
  at index: Int,
  detents: [DetentSpec],
  styles: [DetentCornerRadius]
) -> DetentCornerRadius? {
  let position = detents[index].position
  var first = index
  while first > 0, detents[first - 1].position == position {
    first -= 1
  }
  var member = first
  while member < detents.count, detents[member].position == position {
    if styles.indices.contains(member), styles[member].radius != nil {
      return styles[member]
    }
    member += 1
  }
  return styles.indices.contains(first) ? styles[first] : nil
}

/// Selects the component a CALayer can animate while retaining a fixed corner mask.
func detentCornerLayerIndex(
  surface: [CGFloat],
  styles: [DetentCornerRadius]
) -> Int? {
  if surface.dropFirst().allSatisfy({ $0 == surface[0] }) {
    return 0
  }
  guard styles.allSatisfy({ ($0.radius ?? 0) == 0 }),
        let first = surface.firstIndex(where: { $0 > 0 }),
        surface.allSatisfy({ $0 == 0 || $0 == surface[first] })
  else { return nil }
  return first
}

func spreadCornerRadius(_ radius: CGFloat, by spread: CGFloat, tapered: Bool) -> CGFloat {
  guard radius > 0 else { return 0 }
  if tapered, radius < spread {
    // CSS tapered outset keeps a changing shadow radius continuous at zero.
    let ratio = radius / spread
    return radius * (4 - 3 * ratio + ratio * ratio)
  }
  return max(0, radius + spread)
}
