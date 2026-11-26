package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
    val listener = RecordingBottomSheetListener()
    val host = BottomSheetHostView(activity)
    try {
      host.animateIn = false
      host.modal = true
      host.setScrimColor(Color.BLACK)
      host.setDetents(
        listOf(
          mapOf("value" to 0.0, "scrimOpacity" to 0.0),
          mapOf("value" to 300.0, "scrimOpacity" to 1.0),
        ),
      )
      host.setIndex(1)
      val content = FrameLayout(activity)
      host.addSheetChild(content, 0)
      activity.setContentView(host, ViewGroup.LayoutParams(1080, 1920))
      content.measure(
        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
      )
      layoutView(host)
      host.listener = listener

      // Drag gently partway down and hold briefly before releasing so velocity is ~0 and the
      // sheet snaps back to the open detent (index 1) rather than flicking closed.
      val density = host.resources.displayMetrics.density
      val downTime = nextTouchDownTime
      nextTouchDownTime += 700L
      dragHost(host, downTime, 90f * density to 240L, 0f to 300L)
      assertEquals(listOf(1), listener.indexChanges)

      val beforeAlpha = readScrimAlpha(host)
      assertTrue(
        "Expected scrim to remain visible after short drag, got $beforeAlpha",
        beforeAlpha > 100,
      )

      // Grow the open detent while the snap-back spring is still active.
      val grownDetents =
        listOf(
          mapOf("value" to 0.0, "scrimOpacity" to 0.0),
          mapOf("value" to 900.0, "scrimOpacity" to 1.0),
        )
      host.setDetents(grownDetents)
      val afterAlpha = readScrimAlpha(host)
      assertTrue(
        "Expected scrim alpha ($afterAlpha) not to dip below $beforeAlpha after detent growth",
        afterAlpha >= beforeAlpha,
      )

      // Switching to the closed detent clears the floor; trigger a synchronous scrim recompute
      // without relying on Robolectric's Choreographer advancing the closing spring.
      host.setIndex(0)
      host.setDetents(grownDetents)
      val closingAlpha = readScrimAlpha(host)
      assertTrue(
        "Expected closing to clear the floor ($closingAlpha < $beforeAlpha)",
        closingAlpha < beforeAlpha,
      )
    } finally {
      host.destroy()
      shadowOf(Looper.getMainLooper()).idle()
      controller.close()
    }
  }

  @Test
  fun `shrinking the host height while settled at an open detent keeps the scrim pinned`() {
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

      // Shrink the host so newHeight - oldTranslationY falls below the old open detent;
      // using previousMaxHeight keeps the sheet recognized as open and pins the scrim.
      layoutView(host, height = 1000)

      assertEquals(255, readScrimAlpha(host))
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

  private fun dragHost(
    host: BottomSheetHostView,
    downTime: Long,
    vararg segments: Pair<Float, Long>,
  ) {
    val x = 540f
    var y = 1920f - 100f * host.resources.displayMetrics.density
    var time = downTime
    dispatchTouch(host, downTime, time, MotionEvent.ACTION_DOWN, x, y)
    for ((distance, duration) in segments) {
      val steps = (duration / 16L).toInt()
      repeat(steps) {
        y += distance / steps
        time += 16L
        dispatchTouch(host, downTime, time, MotionEvent.ACTION_MOVE, x, y)
      }
    }
    dispatchTouch(host, downTime, time + 16L, MotionEvent.ACTION_UP, x, y)
  }

  private fun dispatchTouch(
    view: View,
    downTime: Long,
    eventTime: Long,
    action: Int,
    x: Float,
    y: Float,
  ) {
    val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
    try {
      view.dispatchTouchEvent(event)
    } finally {
      event.recycle()
    }
  }

  /**
   * Grows the open detent, which re-anchors the sheet and pins the scrim until the spring ends. The
   * sheet is still below the detent's position, where the scrim is invisible unless it is pinned.
   */
  private fun pinScrimWithReanchor(sheet: BottomSheetView) {
    sheet.setDetents(
      listOf(
        mapOf("value" to 0.0, "scrimOpacity" to 0.0),
        mapOf("value" to 320.0, "scrimOpacity" to 0.0),
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
    lastAlpha = paint.alpha
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
