package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.R
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.facebook.react.uimanager.PointerEvents
import com.facebook.react.uimanager.TouchTargetHelper
import com.facebook.react.views.view.ReactViewGroup
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [TestArgumentsShadow::class])
class BottomSheetHostViewAnchorTest {
  @Test
  fun `the anchored slot keeps its height and clips touches below the header`() {
    ReactNativeFeatureFlagsForTests.setUp()
    Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
      val activity = controller.get()
      val host = BottomSheetHostView(activity)
      val root = FrameLayout(activity).apply { id = 101 }
      root.addView(host)
      activity.setContentView(root, ViewGroup.LayoutParams(320, 400))
      val density = activity.resources.displayMetrics.density
      host.animateIn = false
      val detents =
        listOf(
          mapOf("value" to 0.0),
          mapOf("value" to 200.0 / density, "contentClipTopInset" to 40.0 / density),
          mapOf("value" to 400.0 / density, "contentClipTopInset" to 12.0 / density),
          mapOf("value" to 400.0 / density, "contentClipTopInset" to 80.0 / density),
        )
      host.setDetents(detents)
      val wrapper = ReactViewGroup(activity)
      val delegate = BottomSheetContentPointerEventsDelegate(BottomSheetContentWrapperViewManager())
      delegate.setProperty(wrapper, "pointerEvents", "box-none")
      assertEquals(PointerEvents.BOX_NONE, wrapper.pointerEvents)
      delegate.setProperty(wrapper, "pointerEvents", null)
      assertEquals(PointerEvents.AUTO, wrapper.pointerEvents)

      delegate.setProperty(wrapper, "pointerEvents", "box-none")
      val content = View(activity)
      content.id = 100
      val header = View(activity).apply { id = 102 }
      content.setTag(R.id.view_tag_native_id, BottomSheetHostView.BOTTOM_ANCHORED_CONTENT_NATIVE_ID)
      host.addSheetChild(content, 0)
      wrapper.addView(header)
      host.addSheetChild(wrapper, 1)
      wrapper.measure(exactly(320), exactly(400))
      header.layout(0, 0, 320, 40)
      host.setIndex(1)
      content.measure(exactly(320), exactly(400))
      header.measure(exactly(320), exactly(40))
      root.measure(exactly(320), exactly(400))
      root.layout(0, 0, 320, 400)
      host.measure(exactly(320), exactly(400))
      host.layout(0, 0, 320, 400)

      val viewport = content.parent as ViewGroup
      assertSame(content, host.getSheetChildAt(0))
      assertEquals(40, viewport.top)
      assertEquals(200, viewport.bottom)
      assertEquals(400, content.height)
      assertEquals(200f, viewport.top + content.y + content.height, 0.01f)
      assertEquals(content.id, TouchTargetHelper.findTargetTagForTouch(100f, 300f, root))
      assertEquals(header.id, TouchTargetHelper.findTargetTagForTouch(100f, 220f, root))

      host.setIndex(2)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
      assertEquals(80, viewport.top)
      assertEquals(400, viewport.bottom)
      assertEquals(400, content.height)
      assertEquals(400f, viewport.top + content.y + content.height, 0.01f)

      host.setDetents(
        detents.dropLast(1) + (detents.last() + ("contentClipTopInset" to 64.0 / density))
      )
      assertEquals(64, viewport.top)
      assertEquals(400, viewport.bottom)
      host.removeSheetChildAt(0)
      assertNull(content.parent)
      assertEquals(0f, content.translationY, 0.01f)
      assertEquals(1, host.sheetChildCount)
      assertSame(wrapper, host.getSheetChildAt(0))
    }
  }

  private fun exactly(size: Int): Int =
    View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
}
