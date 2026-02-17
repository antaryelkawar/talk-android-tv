/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Helper object for TV-specific navigation and D-pad handling
 */
object TvNavigationHelper {

    const val VOICE_INPUT_REQUEST_CODE = 1001

    /**
     * Enhanced D-pad handler for RecyclerView with smooth scrolling and focus management
     * @param isReverseLayout Set to true for RecyclerViews with reverse layout (like chat messages)
     */
    fun handleRecyclerViewDpadNavigation(
        recyclerView: RecyclerView,
        keyCode: Int,
        enableHorizontalScroll: Boolean = false,
        isReverseLayout: Boolean = false
    ): Boolean {
        val layoutManager = recyclerView.layoutManager ?: return false
        val currentFocus = recyclerView.focusedChild
        val currentPosition = if (currentFocus != null) {
            recyclerView.getChildAdapterPosition(currentFocus)
        } else {
            -1
        }
        val itemCount = recyclerView.adapter?.itemCount ?: 0

        val upKey = if (isReverseLayout) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
        val downKey = if (isReverseLayout) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN

        return when (keyCode) {
            upKey -> {
                if (currentPosition > 0) {
                    recyclerView.smoothScrollToPosition(currentPosition - 1)
                    recyclerView.post {
                        layoutManager.findViewByPosition(currentPosition - 1)?.requestFocus()
                    }
                    true
                } else {
                    false
                }
            }
            downKey -> {
                if (currentPosition in 0 until itemCount - 1) {
                    recyclerView.smoothScrollToPosition(currentPosition + 1)
                    recyclerView.post {
                        layoutManager.findViewByPosition(currentPosition + 1)?.requestFocus()
                    }
                    true
                } else {
                    false
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (enableHorizontalScroll && currentPosition > 0) {
                    recyclerView.smoothScrollToPosition(currentPosition - 1)
                    recyclerView.post {
                        layoutManager.findViewByPosition(currentPosition - 1)?.requestFocus()
                    }
                    true
                } else {
                    false
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (enableHorizontalScroll && currentPosition in 0 until itemCount - 1) {
                    recyclerView.smoothScrollToPosition(currentPosition + 1)
                    recyclerView.post {
                        layoutManager.findViewByPosition(currentPosition + 1)?.requestFocus()
                    }
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    /**
     * Request focus on first visible item in RecyclerView
     */
    fun requestFocusOnFirstVisibleItem(recyclerView: RecyclerView) {
        recyclerView.post {
            val layoutManager = recyclerView.layoutManager
            val firstVisiblePosition = when (layoutManager) {
                is LinearLayoutManager -> layoutManager.findFirstVisibleItemPosition()
                else -> 0
            }
            
            if (firstVisiblePosition >= 0) {
                val view = layoutManager?.findViewByPosition(firstVisiblePosition)
                view?.requestFocus()
            } else if (recyclerView.childCount > 0) {
                recyclerView.getChildAt(0)?.requestFocus()
            }
        }
    }

    /**
     * Request focus on last visible item in RecyclerView
     */
    fun requestFocusOnLastVisibleItem(recyclerView: RecyclerView) {
        recyclerView.post {
            val layoutManager = recyclerView.layoutManager
            val lastVisiblePosition = when (layoutManager) {
                is LinearLayoutManager -> layoutManager.findLastVisibleItemPosition()
                else -> recyclerView.adapter?.itemCount?.minus(1) ?: 0
            }
            
            if (lastVisiblePosition >= 0) {
                val view = layoutManager?.findViewByPosition(lastVisiblePosition)
                view?.requestFocus()
            } else if (recyclerView.childCount > 0) {
                recyclerView.getChildAt(recyclerView.childCount - 1)?.requestFocus()
            }
        }
    }

    /**
     * Setup message input field for TV with D-pad support
     */
    fun setupMessageInputForTv(
        editText: EditText,
        sendButton: View,
        onSend: () -> Unit,
        onNavigateToMessages: (() -> Unit)? = null
    ) {
        editText.isFocusable = true
        editText.isFocusableInTouchMode = false
        sendButton.isFocusable = true
        sendButton.isFocusableInTouchMode = false
        
        editText.nextFocusRightId = sendButton.id
        editText.nextFocusDownId = sendButton.id
        sendButton.nextFocusLeftId = editText.id
        
        editText.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (editText.text.isNotEmpty()) {
                            onSend()
                            true
                        } else {
                            false
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (editText.text.isEmpty() || 
                            editText.selectionStart == editText.text.length) {
                            sendButton.requestFocus()
                            true
                        } else {
                            false
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        onNavigateToMessages?.invoke()
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
        
        sendButton.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        editText.requestFocus()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        onNavigateToMessages?.invoke()
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
    }

    /**
     * Handle D-pad back navigation with proper focus restoration
     */
    fun handleDpadBack(activity: Activity, currentFocus: View?): Boolean {
        return when {
            currentFocus is EditText -> {
                // If in edit text, clear focus and move to parent
                currentFocus.clearFocus()
                val parent = currentFocus.parent
                if (parent is ViewGroup) {
                    for (i in 0 until parent.childCount) {
                        val child = parent.getChildAt(i)
                        if (child.isFocusable && child != currentFocus) {
                            child.requestFocus()
                            return true
                        }
                    }
                }
                false
            }
            else -> {
                false
            }
        }
    }

    /**
     * Setup button group for TV navigation with circular focus
     */
    fun setupButtonGroupForTv(
        buttons: List<View>,
        orientation: TvUtils.NavigationOrientation = TvUtils.NavigationOrientation.HORIZONTAL,
        circular: Boolean = true
    ) {
        if (buttons.isEmpty()) return
        
        for (i in buttons.indices) {
            buttons[i].isFocusable = true
            buttons[i].isFocusableInTouchMode = false
            
            when (orientation) {
                TvUtils.NavigationOrientation.HORIZONTAL -> {
                    if (i > 0) {
                        buttons[i].nextFocusLeftId = buttons[i - 1].id
                    } else if (circular) {
                        buttons[i].nextFocusLeftId = buttons.last().id
                    }
                    
                    if (i < buttons.size - 1) {
                        buttons[i].nextFocusRightId = buttons[i + 1].id
                    } else if (circular) {
                        buttons[i].nextFocusRightId = buttons.first().id
                    }
                }
                TvUtils.NavigationOrientation.VERTICAL -> {
                    if (i > 0) {
                        buttons[i].nextFocusUpId = buttons[i - 1].id
                    } else if (circular) {
                        buttons[i].nextFocusUpId = buttons.last().id
                    }
                    
                    if (i < buttons.size - 1) {
                        buttons[i].nextFocusDownId = buttons[i + 1].id
                    } else if (circular) {
                        buttons[i].nextFocusDownId = buttons.first().id
                    }
                }
            }
        }
    }

    /**
     * Enable voice input for TV when available
     */
    fun enableVoiceInputForTv(
        activity: Activity,
        voiceInputButton: View?,
        onResult: ((String) -> Unit)
    ) {
        voiceInputButton?.let { button ->
            button.isFocusable = true
            button.isFocusableInTouchMode = false
            
            button.setOnClickListener {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                try {
                    activity.startActivityForResult(intent, VOICE_INPUT_REQUEST_CODE)
                } catch (e: Exception) {
                    // Voice input not available
                }
            }
        }
    }

    /**
     * Setup toolbar/actionbar buttons for TV with proper focus handling
     * Makes toolbar children focusable but not the toolbar itself
     */
    fun setupToolbarForTv(toolbar: ViewGroup) {
        toolbar.isFocusable = false
        // Allow focus to reach toolbar children but not the toolbar itself
        toolbar.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        
        // Make all clickable children in toolbar focusable for TV
        for (i in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(i)
            if (child.isClickable || child is android.widget.ImageButton) {
                child.isFocusable = true
                child.isFocusableInTouchMode = false
            }
        }
    }

    /**
     * Handle navigation between RecyclerView and input controls
     */
    fun linkRecyclerViewAndInput(
        recyclerView: RecyclerView,
        inputView: View,
        additionalControls: List<View> = emptyList()
    ) {
        // Set up focus relationships
        recyclerView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                val lastVisiblePosition = layoutManager?.findLastVisibleItemPosition() ?: -1
                val lastItemPosition = (recyclerView.adapter?.itemCount ?: 1) - 1
                
                // If at the bottom of the list, move to input
                if (lastVisiblePosition >= lastItemPosition - 1) {
                    inputView.requestFocus()
                    return@setOnKeyListener true
                }
            }
            false
        }
    }

    /**
     * Setup quick action buttons (like emoji, attachment) for TV
     */
    fun setupQuickActionButtons(
        buttons: List<View>,
        inputField: EditText,
        messageRecyclerView: RecyclerView
    ) {
        buttons.forEach { button ->
            button.isFocusable = true
            button.isFocusableInTouchMode = false
            
            button.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            requestFocusOnLastVisibleItem(messageRecyclerView)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            inputField.requestFocus()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
        }
        
        setupButtonGroupForTv(buttons, TvUtils.NavigationOrientation.HORIZONTAL, circular = false)
    }

    /**
     * Scroll to bottom of RecyclerView and focus last item
     */
    fun scrollToBottomAndFocus(recyclerView: RecyclerView) {
        val itemCount = recyclerView.adapter?.itemCount ?: 0
        if (itemCount > 0) {
            recyclerView.smoothScrollToPosition(itemCount - 1)
            recyclerView.post {
                recyclerView.layoutManager?.findViewByPosition(itemCount - 1)?.requestFocus()
            }
        }
    }

    /**
     * Scroll to top of RecyclerView and focus first item
     */
    fun scrollToTopAndFocus(recyclerView: RecyclerView) {
        recyclerView.smoothScrollToPosition(0)
        recyclerView.post {
            recyclerView.layoutManager?.findViewByPosition(0)?.requestFocus()
        }
    }
}
