package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// Dismissal is asserted through onIndexChange alone: the closing spring runs on a
// Choreographer that Robolectric stops driving once another view test class has run.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BottomSheetViewScrimDismissTest {
  private var nextTouchDownTime = 1_000L

  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun `scrim tap dismisses an open sheet`() {
    withOpenModalSheet { sheet, listener ->
      tapScrim(sheet)

      assertEquals(listOf(0), listener.indexChanges)
    }
  }

  @Test
  fun `scrim tap with a close request handler only reports the request`() {
    withOpenModalSheet { sheet, listener ->
      sheet.setHasCloseRequestHandler(true)
      tapScrim(sheet)

      assertEquals(1, listener.closeRequestCount)
      assertEquals(emptyList<Int>(), listener.indexChanges)
    }
  }

  @Test
  fun `further scrim taps during the close snap emit no extra index change`() {
    withOpenModalSheet { sheet, listener ->
      tapScrim(sheet)
      tapScrim(sheet)
      tapScrim(sheet)

      assertEquals(listOf(0), listener.indexChanges)
    }
  }

  @Test
  fun `a re-anchor pins the scrim at the fully open opacity`() {
    withOpenModalSheet { sheet, listener ->
      pinScrimWithReanchor(sheet)
      tapScrim(sheet)

      assertEquals(listOf(0), listener.indexChanges)
    }
  }

  @Test
  fun `a close request handler does not carry over to a reused sheet`() {
    withOpenModalSheet { sheet, listener ->
      sheet.setHasCloseRequestHandler(true)

      sheet.destroy()
      sheet.listener = listener
      sheet.setDetents(
        listOf(
          mapOf("value" to 0.0, "scrimOpacity" to 0.0),
          mapOf("value" to 300.0, "scrimOpacity" to 1.0),
        ),
      )
      sheet.setIndex(1)
      sheet.forceLayout()
      layoutView(sheet)
      tapScrim(sheet)

      assertEquals(0, listener.closeRequestCount)
      assertTrue(listener.indexChanges.toString(), 0 in listener.indexChanges)
    }
  }

  @Test
  fun `a scrim pinned at destroy does not carry over to a reused sheet`() {
    withOpenModalSheet { sheet, listener ->
      pinScrimWithReanchor(sheet)

      sheet.destroy()
      sheet.listener = listener
      sheet.setDetents(
        listOf(
          mapOf("value" to 0.0, "scrimOpacity" to 0.0),
          mapOf("value" to 300.0, "scrimOpacity" to 0.0),
          mapOf("value" to 600.0, "scrimOpacity" to 1.0),
        ),
      )
      sheet.setIndex(1)
      sheet.forceLayout()
      layoutView(sheet)
      tapScrim(sheet)

      // This detent's scrim is invisible, so the tap must not dismiss the sheet. Only the dismissal
      // is checked: this tap also emits onIndexChange(1), a separate bug on main. Once that is
      // fixed, this can assert that no index change is emitted at all.
      assertFalse(listener.indexChanges.toString(), 0 in listener.indexChanges)
    }
  }

  @Test
  fun `growing the open detent mid-settle does not dip the scrim opacity`() {
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val activity = controller.get()
    val host = BottomSheetHostView(activity)
    try {
      host.animateIn = false
      host.modal = true
      host.setScrimColor(Color.BLACK)
      fun detents(middleHeight: Double, middleOpacity: Double) =
        listOf(
          mapOf("value" to 0.0, "scrimOpacity" to 0.0),
          mapOf("value" to middleHeight, "scrimOpacity" to middleOpacity),
          mapOf("value" to 900.0, "scrimOpacity" to 1.0),
        )
      host.setDetents(detents(100.0, 0.5))
      host.setIndex(1)
      activity.setContentView(host, ViewGroup.LayoutParams(1080, 1920))
      layoutView(host)
      val beforeAlpha = readScrimAlpha(host)
      assertEquals(127, beforeAlpha)

      // Start a spring, then resize before it advances. At the current 100pt position
      // the new geometry alone would yield 0.12; the last rendered opacity is 0.5.
      host.setIndex(2)
      val grownDetents = detents(500.0, 0.6)
      host.setDetents(grownDetents)
      assertEquals(beforeAlpha, readScrimAlpha(host))

      host.setIndex(0)
      host.setDetents(grownDetents)
      assertTrue(readScrimAlpha(host) < beforeAlpha)
    } finally {
      host.destroy()
      shadowOf(Looper.getMainLooper()).idle()
      controller.close()
    }
  }

  @Test
  fun `shrinking the host height while settled at an open detent keeps a transparent scrim transparent`() {
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val activity = controller.get()
    val host = BottomSheetHostView(activity)
    try {
      host.animateIn = false
      host.modal = true
      host.setScrimColor(Color.BLACK)
      host.setDetents(
        listOf(
          mapOf("value" to 0.0, "kind" to "points", "scrimOpacity" to 0.0),
          mapOf("value" to 0.5, "kind" to "percentage", "scrimOpacity" to 0.0),
          mapOf("value" to 1.0, "kind" to "percentage", "scrimOpacity" to 1.0),
        ),
      )
      host.setIndex(1)
      activity.setContentView(host, ViewGroup.LayoutParams(1080, 1920))
      layoutView(host, height = 1920)

      assertEquals(0, readScrimAlpha(host))
      layoutView(host, height = 1000)
      assertEquals(0, readScrimAlpha(host))
    } finally {
      host.destroy()
      shadowOf(Looper.getMainLooper()).idle()
      controller.close()
    }
  }

  private fun readScrimAlpha(host: BottomSheetHostView): Int {
    val canvas = RecordingScrimCanvas()
    host.draw(canvas)
    return canvas.lastAlpha
  }

  /**
   * Grows the open detent, which re-anchors the sheet and pins the scrim until the spring ends. The
   * sheet is still below the detent's position, where interpolation alone would reduce the opacity.
   */
  private fun pinScrimWithReanchor(sheet: BottomSheetView) {
    sheet.setDetents(
      listOf(
        mapOf("value" to 0.0, "scrimOpacity" to 0.0),
        mapOf("value" to 320.0, "scrimOpacity" to 1.0),
        mapOf("value" to 600.0, "scrimOpacity" to 1.0),
      ),
    )
  }

  private fun withOpenModalSheet(block: (BottomSheetView, RecordingBottomSheetListener) -> Unit) {
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val activity = controller.get()
    val listener = RecordingBottomSheetListener()
    val sheet = openModalSheet(activity, listener)
    try {
      block(sheet, listener)
    } finally {
      sheet.destroy()
      shadowOf(Looper.getMainLooper()).idle()
      controller.close()
    }
  }

  private fun openModalSheet(activity: Activity, listener: RecordingBottomSheetListener) =
    BottomSheetView(activity)
      .apply {
        this.listener = listener
        animateIn = false
        modal = true
        setScrimColor(Color.BLACK)
        setDetents(
          listOf(
            mapOf(
              "value" to 0.0,
              "kind" to "points",
              "programmatic" to false,
              "scrimOpacity" to 0.0,
            ),
            mapOf(
              "value" to 300.0,
              "kind" to "points",
              "programmatic" to false,
              "scrimOpacity" to 1.0,
            ),
          ),
        )
        setIndex(1)
      }
      .also {
        activity.setContentView(it)
        layoutView(it)
      }

  /** Presses and releases above the sheet top, where the scrim is the touch target. */
  private fun tapScrim(sheet: BottomSheetView) {
    val downTime = nextTouchDownTime
    nextTouchDownTime += 100L
    val x = 10f
    val y = 100f
    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
    val up = MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_UP, x, y, 0)
    sheet.dispatchTouchEvent(down)
    sheet.dispatchTouchEvent(up)
    down.recycle()
    up.recycle()
  }

  private fun layoutView(view: View, height: Int = 1920) {
    val width = 1080
    view.measure(
      View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
    )
    view.layout(0, 0, width, height)
    shadowOf(Looper.getMainLooper()).idle()
  }
}

private class RecordingScrimCanvas : Canvas() {
  var lastAlpha = 0

  override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
    lastAlpha = Color.alpha(paint.color)
  }
}

private class RecordingBottomSheetListener : BottomSheetViewListener {
  val indexChanges = mutableListOf<Int>()

  override fun onIndexChange(index: Int) {
    indexChanges.add(index)
  }

  override fun onSettle(index: Int) = Unit

  override fun onPositionChange(position: Double, index: Double) = Unit

  var closeRequestCount = 0

  override fun onCloseRequest() {
    closeRequestCount++
  }
}
