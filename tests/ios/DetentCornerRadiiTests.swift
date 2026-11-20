import Foundation

struct DetentCornerRadiiTests {
  private let inherited = DetentCornerRadius(radius: nil, distance: 0)
  private let square = DetentCornerRadius(radius: 0, distance: 50)

  func testCoincidentDetentsUseOneShapeThroughoutTheTransition() {
    let surface: [CGFloat] = [32, 24, 16, 8]
    let detents = [400, 800, 800].map { position in
      DetentSpec(position: CGFloat(position), programmatic: false, insets: DetentInsets())
    }
    for styles in [[inherited, square, inherited], [inherited, inherited, square]] {
      let samples: [(CGFloat, [CGFloat])] = [
        (749, surface),
        (775, [16, 12, 8, 4]),
        (800, [0, 0, 0, 0]),
        (825, [0, 0, 0, 0]),
      ]
      for (position, expected) in samples {
        expectEqual(
          resolveDetentCornerRadii(
            position: position, detents: detents, styles: styles, surface: surface
          ),
          expected
        )
      }
    }
  }

  func testTopOnlyCornersCanUseTheLayerWhenTheOverrideIsZero() {
    let topCorners: [CGFloat] = [32, 32, 0, 0]
    let rounded = DetentCornerRadius(radius: 18, distance: 50)
    expectEqual(detentCornerLayerIndex(surface: topCorners, styles: [inherited, square]), 0)
    expectEqual(detentCornerLayerIndex(surface: [0, 32, 32, 0], styles: [square]), 1)
    expectEqual(detentCornerLayerIndex(surface: [0, 0, 0, 0], styles: [rounded]), 0)
    expectEqual(detentCornerLayerIndex(surface: topCorners, styles: [rounded]), nil)
    expectEqual(detentCornerLayerIndex(surface: [32, 24, 0, 0], styles: [square]), nil)
  }

  func testOutsetShadowApproachesZeroContinuously() {
    expectEqual(spreadCornerRadius(0, by: 20, tapered: true), 0)
    precondition(abs(spreadCornerRadius(0.01, by: 20, tapered: true) - 0.039985) < 0.0001)
    expectEqual(spreadCornerRadius(10, by: 20, tapered: true), 27.5)
    expectEqual(spreadCornerRadius(10, by: 20, tapered: false), 30)
    expectEqual(spreadCornerRadius(10, by: -20, tapered: true), 0)
  }
}

private func expectEqual<T: Equatable>(
  _ actual: T, _ expected: T, file: StaticString = #fileID, line: UInt = #line
) {
  precondition(actual == expected, "Expected \(expected), got \(actual)", file: file, line: line)
}

@main
struct DetentCornerRadiiTestRunner {
  static func main() {
    let tests = DetentCornerRadiiTests()
    tests.testCoincidentDetentsUseOneShapeThroughoutTheTransition()
    tests.testTopOnlyCornersCanUseTheLayerWhenTheOverrideIsZero()
    tests.testOutsetShadowApproachesZeroContinuously()
    print("3 Swift corner tests passed")
  }
}
