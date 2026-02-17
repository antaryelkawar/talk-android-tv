/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.annotation.ColorInt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.RecyclerView

object TvUtils {

    fun isTvMode(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    fun isLargeScreen(context: Context): Boolean {
        val screenLayout = context.resources.configuration.screenLayout
        val screenSize = screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        return screenSize >= Configuration.SCREENLAYOUT_SIZE_LARGE
    }

    fun getTvAspectRatio(): Float {
        return 16f / 9f
    }

    fun applyTvFocusHighlight(view: View, @ColorInt focusColor: Int) {
        view.isFocusable = true
        view.isFocusableInTouchMode = false

        val originalBackground = view.background

        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                val focusDrawable = ColorDrawable(
                    Color.argb(
                        60,
                        Color.red(focusColor),
                        Color.green(focusColor),
                        Color.blue(focusColor)
                    )
                )
                v.background = focusDrawable
                v.scaleX = 1.1f
                v.scaleY = 1.1f
                v.elevation = 8f
            } else {
                v.background = originalBackground
                v.scaleX = 1.0f
                v.scaleY = 1.0f
                v.elevation = 0f
            }
        }
    }

    fun setupDpadNavigation(
        views: List<View>,
        orientation: NavigationOrientation = NavigationOrientation.HORIZONTAL
    ) {
        for (i in views.indices) {
            views[i].isFocusable = true
            views[i].isFocusableInTouchMode = false

            when (orientation) {
                NavigationOrientation.HORIZONTAL -> {
                    if (i > 0) {
                        views[i].nextFocusLeftId = views[i - 1].id
                    }
                    if (i < views.size - 1) {
                        views[i].nextFocusRightId = views[i + 1].id
                    }
                }
                NavigationOrientation.VERTICAL -> {
                    if (i > 0) {
                        views[i].nextFocusUpId = views[i - 1].id
                    }
                    if (i < views.size - 1) {
                        views[i].nextFocusDownId = views[i + 1].id
                    }
                }
            }
        }
    }

    enum class NavigationOrientation {
        HORIZONTAL,
        VERTICAL
    }

    fun requestDefaultFocus(vararg views: View) {
        for (view in views) {
            if (view.isFocusable && view.visibility == View.VISIBLE) {
                view.requestFocus()
                break
            }
        }
    }

    fun calculateTvVideoDimensions(
        screenWidth: Int,
        screenHeight: Int,
        videoWidth: Int,
        videoHeight: Int
    ): Pair<Int, Int> {
        val screenAspect = screenWidth.toFloat() / screenHeight.toFloat()
        val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()

        return if (screenAspect > videoAspect) {
            val width = (screenHeight * videoAspect).toInt()
            Pair(width, screenHeight)
        } else {
            val height = (screenWidth / videoAspect).toInt()
            Pair(screenWidth, height)
        }
    }

    fun getTvRecommendedResolution(): Pair<Int, Int> {
        return Pair(1920, 1080)
    }

    fun makeRecyclerViewItemsFocusable(recyclerView: RecyclerView, @ColorInt focusColor: Int) {
        recyclerView.addOnChildAttachStateChangeListener(
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    view.isFocusable = true
                    view.isFocusableInTouchMode = false
                    val originalBackground = view.background
                    view.setOnFocusChangeListener { v, hasFocus ->
                        if (hasFocus) {
                            val focusDrawable = ColorDrawable(
                                Color.argb(
                                    40,
                                    Color.red(focusColor),
                                    Color.green(focusColor),
                                    Color.blue(focusColor)
                                )
                            )
                            v.background = focusDrawable
                            v.scaleX = 1.02f
                            v.scaleY = 1.02f
                            v.elevation = 4f
                        } else {
                            v.background = originalBackground
                            v.scaleX = 1.0f
                            v.scaleY = 1.0f
                            v.elevation = 0f
                        }
                    }
                }

                override fun onChildViewDetachedFromWindow(view: View) {
                    // no-op
                }
            }
        )
    }

    fun setupScrollViewDpadNavigation(scrollView: ScrollView) {
        scrollView.isFocusable = false
        scrollView.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        scrollView.isSmoothScrollingEnabled = true
    }

    fun makeViewGroupChildrenFocusable(viewGroup: ViewGroup, @ColorInt focusColor: Int) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child.isClickable) {
                applyTvFocusHighlight(child, focusColor)
            } else if (child is ViewGroup) {
                if (child.isClickable) {
                    applyTvFocusHighlight(child, focusColor)
                } else {
                    makeViewGroupChildrenFocusable(child, focusColor)
                }
            }
        }
    }
}

@Composable
fun isTvMode(): Boolean {
    val context = LocalContext.current
    return remember { TvUtils.isTvMode(context) }
}

@Suppress("MagicNumber")
fun Modifier.tvFocusHighlight(): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1.0f,
        label = "tvFocusScale"
    )
    val borderColor = MaterialTheme.colorScheme.primary

    this
        .onFocusChanged { isFocused = it.isFocused }
        .scale(scale)
        .then(
            if (isFocused) {
                Modifier.border(2.dp, borderColor, RoundedCornerShape(8.dp))
            } else {
                Modifier
            }
        )
        .focusable()
}

fun Modifier.tvDpadHandler(
    onSelect: (() -> Unit)? = null
): Modifier = this.onPreviewKeyEvent { event ->
    if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key.nativeKeyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
            onSelect?.invoke()
            onSelect != null
        }
        else -> false
    }
}
