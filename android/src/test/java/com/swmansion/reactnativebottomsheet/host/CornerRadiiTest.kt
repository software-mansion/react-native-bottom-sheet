package com.swmansion.reactnativebottomsheet.host

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CornerRadiiTest {
  private val surface = floatArrayOf(32f, 24f, 16f, 8f)

  private fun radii(position: Float, lower: Float, distance: Float): FloatArray {
    val detents =
      listOf(lower, 800f).map { DetentSpec(it, programmatic = false, insets = DetentInsets()) }
    val styles = listOf(DetentCornerRadius(null, 0f), DetentCornerRadius(0f, distance))
    return FloatArray(4).also { resolveCornerRadii(position, detents, styles, surface, it) }
  }

  @Test
  fun `the clip keeps its surface radii before the transition starts`() {
    assertArrayEquals(surface, radii(749f, lower = 400f, distance = 50f), 0f)
    assertArrayEquals(surface, radii(750f, lower = 400f, distance = 50f), 0f)
  }

  @Test
  fun `all four radii reach zero at the upper detent`() {
    assertArrayEquals(floatArrayOf(16f, 12f, 8f, 4f), radii(775f, 400f, 50f), 0f)
    assertArrayEquals(FloatArray(4), radii(800f, 400f, 50f), 0f)
  }

  @Test
  fun `coincident detents keep the first explicit corner shape on both sides`() {
    val detents = listOf(400f, 800f, 800f).map { DetentSpec(it, false, DetentInsets()) }
    val inherited = DetentCornerRadius(null, 0f)
    val square = DetentCornerRadius(0f, 50f)
    for (styles in
      listOf(
        listOf(inherited, square, inherited),
        listOf(inherited, inherited, square),
        listOf(inherited, square, DetentCornerRadius(12f, 0f)),
      )) {
      val result = FloatArray(4)
      resolveCornerRadii(775f, detents, styles, surface, result)
      assertArrayEquals(floatArrayOf(16f, 12f, 8f, 4f), result, 0f)
      resolveCornerRadii(800f, detents, styles, surface, result)
      assertArrayEquals(FloatArray(4), result, 0f)
      resolveCornerRadii(825f, detents, styles, surface, result)
      assertArrayEquals(FloatArray(4), result, 0f)
    }
  }

  @Test
  fun `outset shadows taper while the corners approach zero`() {
    assertEquals(0f, spreadCornerRadius(0f, 20f, tapered = true), 0f)
    assertEquals(0.039985f, spreadCornerRadius(0.01f, 20f, tapered = true), 0.0001f)
    assertEquals(27.5f, spreadCornerRadius(10f, 20f, tapered = true), 0f)
    assertEquals(30f, spreadCornerRadius(10f, 20f, tapered = false), 0f)
    assertEquals(0f, spreadCornerRadius(10f, -20f, tapered = true), 0f)
  }

  @Test
  fun `a short interval caps the distance and zero blends over the entire interval`() {
    val halfway = floatArrayOf(16f, 12f, 8f, 4f)
    assertArrayEquals(halfway, radii(785f, 770f, 50f), 0f)
    assertArrayEquals(halfway, radii(600f, 400f, 0f), 0f)
  }
}
