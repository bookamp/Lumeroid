package org.getlumeroid.android.core.designsystem

import android.util.Log
import androidx.compose.ui.focus.FocusRequester
import java.lang.ref.WeakReference

/**
 * Universal TV Focus Rescue System.
 * Ensures that there is NEVER a situation in the application where no element has focus.
 *
 * If a screen transition, dialog dismissal, background data reload, or scroll causes
 * Compose to drop focus, TvFocusRescue rescues focus on the active screen's fallback requester.
 */
object TvFocusRescue {
    private const val TAG = "TvFocusRescue"
    private val requesters = mutableListOf<WeakReference<FocusRequester>>()
    var fallbackAction: (() -> Boolean)? = null

    fun register(requester: FocusRequester) {
        synchronized(requesters) {
            requesters.removeAll { it.get() == null || it.get() === requester }
            requesters.add(WeakReference(requester))
            Log.d(TAG, "Registered requester. Total active: " + requesters.size)
        }
    }

    fun unregister(requester: FocusRequester) {
        synchronized(requesters) {
            requesters.removeAll { it.get() == null || it.get() === requester }
            Log.d(TAG, "Unregistered requester. Total remaining: " + requesters.size)
        }
    }

    fun rescueFocus(): Boolean {
        synchronized(requesters) {
            for (i in requesters.indices.reversed()) {
                val req = requesters[i].get()
                if (req != null) {
                    try {
                        val granted = req.requestFocus()
                        if (granted) {
                            Log.d(TAG, "Successfully rescued focus on requester " + i)
                            return true
                        } else {
                            Log.d(TAG, "Requester " + i + " was not granted focus (unmounted or offscreen)")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to rescue focus on requester " + i + ": " + e.message)
                    }
                }
            }
        }
        val fallback = fallbackAction?.invoke() ?: false
        if (fallback) {
            Log.d(TAG, "Successfully rescued focus using fallbackAction")
            return true
        }
        Log.w(TAG, "No viable focus requester found to rescue focus.")
        return false
    }
}
