package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val WIDTH_PX = 1080
private const val HEIGHT_PX = 1920
private const val IME_PX = 500

@RunWith(AndroidJUnit4::class)
// A display the size of the sheet, so the keyboard is measured from the sheet's bottom.
@Config(sdk = [35], qualifiers = "w1080dp-h1920dp-mdpi", shadows = [TestArgumentsShadow::class])
class BottomSheetHostViewKeyboardAvoidanceTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun `a short sheet rides on the keyboard and comes back down when it hides`() {
    withSheet(detent = 600.0) { sheet, input, _ ->
      input.requestFocus()

      sheet.applyIme(IME_PX)
      assertEquals(IME_PX, sheet.overlap(IME_PX))
      assertEquals(-IME_PX, sheet.container().top)

      sheet.applyIme(0)
      assertEquals(0, sheet.container().top)
    }
  }

  @Test
  fun `a tall sheet stops below its top inset and pads its scroll view instead`() {
    withSheet(detent = 1800.0) { sheet, input, scrollView ->
      input.requestFocus()

      sheet.applyIme(IME_PX)
      // The card's top can rise only as far as the status bar, which doesn't overlap here.
      assertEquals(-(HEIGHT_PX - 1800), sheet.container().top)
      assertTrue(scrollView.paddingBottom > 0)
      assertTrue(!scrollView.clipToPadding)

      sheet.applyIme(0)
      assertEquals(0, sheet.container().top)
      assertEquals(0, scrollView.paddingBottom)
      assertTrue(scrollView.clipToPadding)
    }
  }

  @Test
  fun `an input outside the sheet doesn't move it`() {
    withSheet(detent = 600.0) { sheet, _, _ ->
      sheet.applyIme(IME_PX)
      assertEquals(0, sheet.container().top)
    }
  }

  @Test
  fun `the sheet stays put when keyboard avoidance is off`() {
    withSheet(detent = 600.0, keyboardAvoidance = false) { sheet, input, _ ->
      input.requestFocus()

      sheet.applyIme(IME_PX)
      assertEquals(0, sheet.container().top)
    }
  }

  private fun BottomSheetHostView.applyIme(bottom: Int) {
    val insets =
      WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, bottom))
        .setVisible(WindowInsetsCompat.Type.ime(), bottom > 0)
        .build()
    dispatchApplyWindowInsets(insets.toWindowInsets())
    shadowOf(Looper.getMainLooper()).idle()
  }

  /** How far an IME this tall covers the sheet, measured as the host does. */
  private fun BottomSheetHostView.overlap(ime: Int): Int {
    val location = IntArray(2)
    getLocationInWindow(location)
    val belowHost = rootView.height - (location[1] + height)
    return (ime - belowHost).coerceIn(0, height)
  }

  private fun BottomSheetHostView.container(): View =
    (0 until childCount).map(::getChildAt).single { it is BottomSheetCardContainer }

  private fun withSheet(
    detent: Double,
    keyboardAvoidance: Boolean = true,
    block: (BottomSheetHostView, EditText, ScrollView) -> Unit,
  ) {
    val controller = Robolectric.buildActivity(Activity::class.java).setup()
    val activity = controller.get()
    val sheet = BottomSheetHostView(activity)
    try {
      sheet.animateIn = false
      sheet.keyboardAvoidance = keyboardAvoidance
      sheet.setDetents(listOf(0.0, detent).map { mapOf("value" to it, "kind" to "points") })
      sheet.setIndex(1)
      val input = EditText(activity).apply { isFocusableInTouchMode = true }
      val scrollView =
        ScrollView(activity).apply {
          addView(
            FrameLayout(activity).apply {
              addView(input, FrameLayout.LayoutParams(WIDTH_PX, 120))
            }
          )
        }
      val content = FrameLayout(activity).apply { addView(scrollView) }
      sheet.addSheetChild(content, 0)
      activity.setContentView(sheet, ViewGroup.LayoutParams(WIDTH_PX, HEIGHT_PX))
      layout(sheet, content)
      block(sheet, input, scrollView)
    } finally {
      sheet.destroy()
      shadowOf(Looper.getMainLooper()).idle()
      controller.close()
    }
  }

  private fun layout(sheet: BottomSheetHostView, content: FrameLayout) {
    content.measure(
      View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY),
    )
    content.layout(0, 0, WIDTH_PX, HEIGHT_PX)
    sheet.measure(
      View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY),
    )
    sheet.layout(0, 0, WIDTH_PX, HEIGHT_PX)
    shadowOf(Looper.getMainLooper()).idle()
  }
}
