package com.swmansion.reactnativebottomsheet

import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import com.facebook.react.uimanager.PointerEvents
import com.facebook.react.uimanager.ReactPointerEventsView

/** Native viewport for the anchored React child. Its bounds gate drawing and hit testing. */
internal class BottomSheetAnchorClipLayout(val content: View) :
  FrameLayout(content.context), ReactPointerEventsView {
  override val pointerEvents = PointerEvents.BOX_NONE
  private val viewport = Rect()

  init {
    addView(content)
  }

  // Card lays the child out at its Fabric-measured size, independently of the viewport.
  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) = Unit

  fun place(width: Int, top: Int, bottom: Int) {
    if (this.width == width && this.top == top && this.bottom == bottom) return
    layout(0, top, width, bottom)
    if (viewport.right != width || viewport.bottom != bottom - top) {
      viewport.set(0, 0, width, bottom - top)
      clipBounds = viewport
    }
  }
}
