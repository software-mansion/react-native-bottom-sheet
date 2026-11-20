package com.swmansion.reactnativebottomsheet.host

import kotlin.math.min

/** Distances in physical pixels, like the resolved detent positions. */
internal data class DetentCornerRadius(val radius: Float?, val distance: Float)

/** Blends the corners without allocating or re-laying out content during a gesture. */
internal fun resolveCornerRadii(
  position: Float,
  detents: List<DetentSpec>,
  cornerStyles: List<DetentCornerRadius>,
  surface: FloatArray,
  result: FloatArray,
) {
  if (detents.isEmpty()) {
    surface.copyInto(result)
    return
  }
  val upper = detents.indexOfFirst { position < it.position }
  val lower = if (upper < 0) detents.lastIndex else (upper - 1).coerceAtLeast(0)
  val from = cornerStyleAt(lower, detents, cornerStyles)?.radius
  val target = cornerStyleAt(upper, detents, cornerStyles)
  val to = target?.radius
  val progress =
    if (upper > lower) {
      val span = detents[upper].position - detents[lower].position
      val distance = target?.distance?.takeIf { it > 0f } ?: span
      (1f - (detents[upper].position - position) / min(span, distance)).coerceIn(0f, 1f)
    } else {
      0f
    }
  for (i in result.indices) {
    val start = from ?: surface[i]
    val end = to ?: surface[i]
    result[i] = start + (end - start) * progress
  }
}

private fun cornerStyleAt(
  index: Int,
  detents: List<DetentSpec>,
  styles: List<DetentCornerRadius>,
): DetentCornerRadius? {
  val position = detents.getOrNull(index)?.position ?: return null
  var first = index
  while (first > 0 && detents[first - 1].position == position) first--
  var member = first
  while (member < detents.size && detents[member].position == position) {
    val style = styles.getOrNull(member)
    if (style?.radius != null) return style
    member++
  }
  return styles.getOrNull(first)
}

/** CSS tapered outset keeps a changing shadow radius continuous at zero. */
internal fun spreadCornerRadius(radius: Float, spread: Float, tapered: Boolean): Float {
  if (radius <= 0f) return 0f
  if (tapered && radius < spread) {
    val ratio = radius / spread
    return radius * (4f - 3f * ratio + ratio * ratio)
  }
  return (radius + spread).coerceAtLeast(0f)
}
