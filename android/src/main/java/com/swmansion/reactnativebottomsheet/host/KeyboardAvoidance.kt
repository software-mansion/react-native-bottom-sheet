package com.swmansion.reactnativebottomsheet.host

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.EditText
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Keyboard avoidance, opt-in through `keyboardAvoidance`. Mirrors the iOS host.
 *
 * While a text input inside the sheet is focused, the keyboard is treated as the new bottom of the
 * screen: the sheet container is lifted, frame by frame, by the IME's own inset animation
 * (`WindowInsetsAnimationCompat`), so the card rides on top of the keyboard. The lift moves the
 * container's layout, independent of the `translationY` that drags and settles drive.
 *
 * The card's top never rises above its detent's top inset. Whatever the capped lift leaves behind
 * the keyboard is added to the bottom padding of the scroll view that holds the focused input, and
 * the input is scrolled above the keyboard in the same animation.
 */
internal class KeyboardAvoidance(
  private val state: SheetState,
  private val content: ViewGroup,
  /** Moves the sheet container to a new lift, in px. */
  private val applyLift: (Int) -> Unit,
) {
  var enabled = false
    set(value) {
      if (field == value) return
      field = value
      if (value) attach() else detach()
    }

  /** Extra px between the card's bottom and the keyboard; negative tucks the card behind it. */
  var offsetPx = 0f

  /** The lift currently applied, in px. */
  var lift = 0
    private set

  private val revealGapPx = 16f * state.density
  private val host: View
    get() = state.hostView

  private var isImeAnimating = false
  private var overlap = 0f
  private var isActive = false
  private var refitAnimator: ValueAnimator? = null
  private var focusAnimator: ValueAnimator? = null

  // The transition the running IME animation drives.
  private var fromLift = 0
  private var toLift = 0
  private var scrollView: ViewGroup? = null
  private var fromScroll = 0
  private var toScroll = 0

  // The scroll view given extra bottom padding, and how much.
  private var paddedView: ViewGroup? = null
  private var paddedAmount = 0
  private var paddedClipToPadding = true

  private val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
    if (!enabled || isImeAnimating || overlap <= 0f) return@OnGlobalFocusChangeListener
    if (newFocus is EditText && isInContent(newFocus)) {
      // Moving between inputs with the keyboard up: no keyboard animation to follow, so reveal
      // with a short ease like the keyboard's own.
      val end = overlap
      prepare(end, revealsInput = true)
      if (toScroll == fromScroll && toLift == fromLift) {
        finish(end)
        return@OnGlobalFocusChangeListener
      }
      focusAnimator =
        ValueAnimator.ofFloat(0f, 1f).apply {
          duration = 250
          interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
          addUpdateListener { step(it.animatedValue as Float) }
          addListener(
            object : android.animation.AnimatorListenerAdapter() {
              override fun onAnimationEnd(animation: android.animation.Animator) {
                if (focusAnimator === animation) {
                  focusAnimator = null
                  finish(end)
                }
              }
            }
          )
          start()
        }
    }
  }

  private val animationCallback =
    object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
      override fun onPrepare(animation: WindowInsetsAnimationCompat) {
        if (isIme(animation)) isImeAnimating = true
      }

      override fun onStart(
        animation: WindowInsetsAnimationCompat,
        bounds: WindowInsetsAnimationCompat.BoundsCompat,
      ): WindowInsetsAnimationCompat.BoundsCompat {
        if (isIme(animation)) {
          // The root insets already hold the animation's end state here.
          val end = ViewCompat.getRootWindowInsets(host)?.let(::overlapOf) ?: 0f
          prepare(end, revealsInput = true)
        }
        return bounds
      }

      override fun onProgress(
        insets: WindowInsetsCompat,
        runningAnimations: MutableList<WindowInsetsAnimationCompat>,
      ): WindowInsetsCompat {
        val animation = runningAnimations.firstOrNull(::isIme) ?: return insets
        if (isActive) step(animation.interpolatedFraction)
        return insets
      }

      override fun onEnd(animation: WindowInsetsAnimationCompat) {
        if (!isIme(animation)) return
        isImeAnimating = false
        val end = ViewCompat.getRootWindowInsets(host)?.let(::overlapOf) ?: 0f
        finish(end)
      }
    }

  private fun attach() {
    ViewCompat.setWindowInsetsAnimationCallback(host, animationCallback)
    if (host.isAttachedToWindow) host.viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)
  }

  private fun detach() {
    ViewCompat.setWindowInsetsAnimationCallback(host, null)
    host.viewTreeObserver.removeOnGlobalFocusChangeListener(focusListener)
    prepare(0f, revealsInput = false)
    finish(0f)
  }

  fun onAttachedToWindow() {
    if (enabled) host.viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)
  }

  fun onDetachedFromWindow() {
    host.viewTreeObserver.removeOnGlobalFocusChangeListener(focusListener)
  }

  /** Insets changed without an animation (or the platform doesn't animate them). */
  fun onApplyWindowInsets(insets: WindowInsetsCompat) {
    if (!enabled || isImeAnimating) return
    val end = overlapOf(insets)
    if (abs(end - overlap) < 0.5f) return
    prepare(end, revealsInput = true)
    finish(end)
  }

  /** Re-fits the lift after the sheet's target height changed while the keyboard is up. */
  fun refit() {
    if (!enabled || isImeAnimating || !isActive || overlap <= 0f) return
    val target = liftFor(overlap)
    if (target == lift || refitAnimator?.isRunning == true) return
    val start = lift
    refitAnimator =
      ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 300
        interpolator = DecelerateInterpolator()
        addUpdateListener {
          val fraction = it.animatedValue as Float
          setLift((start + (liftFor(overlap) - start) * fraction).roundToInt())
        }
        start()
      }
  }

  // MARK: - Transitions

  private fun prepare(endOverlap: Float, revealsInput: Boolean) {
    refitAnimator?.cancel()
    // A newer transition takes over from where a running reveal is now.
    focusAnimator?.let {
      focusAnimator = null
      it.cancel()
    }
    val input = if (enabled) focusedInput() else null
    // Showing for an input outside the sheet: nothing to do. Hiding: follow the keyboard down
    // even though the input may already have lost focus.
    isActive = if (endOverlap > 0f) input != null else isActive || lift != 0
    fromLift = lift
    toLift = if (isActive && endOverlap > 0f) liftFor(endOverlap) else 0

    val enclosing = input?.let(::enclosingScrollView)
    if (endOverlap > 0f && paddedView != null && paddedView !== enclosing) setPadding(null, 0)
    if (enclosing != null && endOverlap > 0f) {
      setPadding(enclosing, coveredPx(enclosing, endOverlap).roundToInt())
    }

    scrollView = enclosing
    fromScroll = enclosing?.scrollY ?: 0
    toScroll = fromScroll
    if (enclosing != null && input != null && revealsInput && endOverlap > 0f) {
      toScroll = revealedScroll(input, enclosing, endOverlap)
    }
    // Hiding: the padding goes at the end, so scroll back within the range left without it, along
    // with the keyboard.
    val padded = paddedView
    if (endOverlap <= 0f && padded != null) {
      val child = padded.getChildAt(0)
      val maxScroll =
        if (child == null) 0
        else
          max(
            0,
            child.height - (padded.height - padded.paddingTop - padded.paddingBottom + paddedAmount),
          )
      scrollView = padded
      fromScroll = padded.scrollY
      toScroll = min(fromScroll, maxScroll)
    }
  }

  private fun step(fraction: Float) {
    setLift((fromLift + (toLift - fromLift) * fraction).roundToInt())
    scrollView?.let { view ->
      if (toScroll != fromScroll) {
        view.scrollTo(view.scrollX, (fromScroll + (toScroll - fromScroll) * fraction).roundToInt())
      }
    }
  }

  private fun finish(endOverlap: Float) {
    step(1f)
    overlap = endOverlap
    if (endOverlap <= 0f) {
      isActive = false
      // Removed once the keyboard is gone, so the content doesn't jump mid-animation.
      setPadding(null, 0)
    }
  }

  private fun setLift(value: Int) {
    if (value == lift) return
    lift = value
    applyLift(value)
  }

  // MARK: - Geometry

  private fun isIme(animation: WindowInsetsAnimationCompat): Boolean =
    animation.typeMask and WindowInsetsCompat.Type.ime() != 0

  /** How far the IME covers the host from its bottom, in px. */
  private fun overlapOf(insets: WindowInsetsCompat): Float {
    val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
    if (ime <= 0) return 0f
    val location = IntArray(2)
    host.getLocationInWindow(location)
    val belowHost = host.rootView.height - (location[1] + host.height)
    return (ime - belowHost).toFloat().coerceIn(0f, state.height.toFloat())
  }

  private fun liftFor(overlap: Float): Int {
    val wanted = max(0f, overlap + offsetPx)
    val index = state.targetIndex
    val position = state.detentSpecs.getOrNull(index)?.position ?: return wanted.roundToInt()
    val minTop = state.rawDetentSpecs.getOrNull(index)?.insets?.top ?: state.statusBarOverlapPx
    val room = max(0f, state.height - position - minTop)
    return min(wanted, room).roundToInt()
  }

  /** The px of `view` that stay below the line the card's bottom would reach uncapped. */
  private fun coveredPx(view: ViewGroup, endOverlap: Float): Float {
    val endLift = liftFor(endOverlap)
    val bottomAfterLift = bottomInHost(view) - (endLift - lift)
    val insetBottom = state.rawDetentSpecs.getOrNull(state.targetIndex)?.insets?.bottom ?: 0f
    val cardBottomTarget = state.height - endOverlap - offsetPx - insetBottom
    return max(0f, bottomAfterLift - cardBottomTarget) / scaleOf(view)
  }

  private fun revealedScroll(input: View, view: ViewGroup, endOverlap: Float): Int {
    val endLift = liftFor(endOverlap)
    val inputBottom = bottomInHost(input) - (endLift - lift)
    val limit = state.height - endOverlap - revealGapPx
    val overshoot = inputBottom - limit
    if (overshoot <= 0.5f) return view.scrollY
    val child = view.getChildAt(0) ?: return view.scrollY
    val maxScroll = max(0, child.height - (view.height - view.paddingTop - view.paddingBottom))
    return min(view.scrollY + (overshoot / scaleOf(view)).roundToInt(), maxScroll)
  }

  /** The bottom of `view` on screen, in the host's coordinates. */
  private fun bottomInHost(view: View): Float {
    val hostLocation = IntArray(2)
    val viewLocation = IntArray(2)
    host.getLocationInWindow(hostLocation)
    view.getLocationInWindow(viewLocation)
    return (viewLocation[1] - hostLocation[1]) + view.height * scaleOf(view)
  }

  /** Host px per px of `view`: the content is scaled into narrower cards. */
  private fun scaleOf(view: View): Float {
    var scale = 1f
    var current: View? = view
    while (current != null && current !== host) {
      scale *= current.scaleY
      current = current.parent as? View
    }
    return if (scale > 0f) scale else 1f
  }

  private fun focusedInput(): EditText? = content.findFocus() as? EditText

  private fun isInContent(view: View): Boolean {
    var current = view.parent
    while (current != null) {
      if (current === content) return true
      current = current.parent
    }
    return false
  }

  /** The nearest vertical scroll view around `input` inside the sheet. */
  private fun enclosingScrollView(input: View): ViewGroup? {
    var current = input.parent
    while (current != null && current !== content) {
      if (current is ScrollView) return current
      current = current.parent
    }
    return null
  }

  /** Gives `view` (or none) `amount` px of extra bottom padding, restoring the previous one. */
  private fun setPadding(view: ViewGroup?, amount: Int) {
    paddedView?.let { previous ->
      if (previous !== view || amount != paddedAmount) {
        previous.setPadding(
          previous.paddingLeft,
          previous.paddingTop,
          previous.paddingRight,
          max(0, previous.paddingBottom - paddedAmount),
        )
        if (previous !== view) previous.clipToPadding = paddedClipToPadding
      }
    }
    if (view == null || amount <= 0) {
      if (view == null || view !== paddedView) {
        paddedView = null
        paddedAmount = 0
      } else {
        paddedView = null
        paddedAmount = 0
        view.clipToPadding = paddedClipToPadding
      }
      return
    }
    if (view !== paddedView) {
      paddedClipToPadding = view.clipToPadding
    } else if (amount == paddedAmount) {
      return
    }
    view.clipToPadding = false
    view.setPadding(
      view.paddingLeft,
      view.paddingTop,
      view.paddingRight,
      view.paddingBottom + amount,
    )
    paddedView = view
    paddedAmount = amount
  }
}
