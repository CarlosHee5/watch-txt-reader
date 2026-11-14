package com.carloshee.watchtxt.wear

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import kotlinx.coroutines.launch

/**
 * Scroll [state] with the rotating (touch) bezel / crown. Galaxy Watch6's touch bezel
 * delivers standard rotary scroll events, so this works for both.
 */
fun Modifier.rotaryScroll(state: ScrollableState, speed: Float = 1f): Modifier = composed {
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    this
        .onRotaryScrollEvent { event ->
            scope.launch { state.scrollBy(event.verticalScrollPixels * speed) }
            true
        }
        .focusRequester(focusRequester)
        .focusable()
}
