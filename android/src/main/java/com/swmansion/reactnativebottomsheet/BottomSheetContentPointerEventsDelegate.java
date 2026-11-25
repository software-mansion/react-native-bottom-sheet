package com.swmansion.reactnativebottomsheet;

import androidx.annotation.Nullable;
import com.facebook.react.uimanager.PointerEvents;
import com.facebook.react.viewmanagers.BottomSheetContentWrapperViewManagerDelegate;
import com.facebook.react.views.view.ReactViewGroup;

// Match Fabric's generated Java delegate, including RN's Java compatibility bridge.
final class BottomSheetContentPointerEventsDelegate
    extends BottomSheetContentWrapperViewManagerDelegate<
        ReactViewGroup, BottomSheetContentWrapperViewManager> {
  BottomSheetContentPointerEventsDelegate(BottomSheetContentWrapperViewManager manager) {
    super(manager);
  }

  @Override
  public void setProperty(ReactViewGroup view, String propName, @Nullable Object value) {
    if ("pointerEvents".equals(propName)) {
      view.setPointerEvents(PointerEvents.parsePointerEvents((String) value));
    } else {
      super.setProperty(view, propName, value);
    }
  }
}
