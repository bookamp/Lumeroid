package org.getlumeroid.android.core.player

/**
 * Pairs directional moves with button presses, ensuring reliable D-Pad click
 * handling on Android TV / Google TV / Fire TV remotes.
 * Direct port of Lumeroid Apple's RemoteDirectionGate.swift.
 */
class RemoteDirectionGate {
    enum class Direction {
        UP, DOWN, LEFT, RIGHT
    }

    private data class PendingAction(val direction: Direction, val timestamp: Long)

    private var pendingMove: PendingAction? = null
    private var pendingPress: PendingAction? = null

    companion object {
        const val PAIRING_WINDOW_MS = 300L
    }

    fun noteMove(direction: Direction, now: Long = System.currentTimeMillis()): Boolean {
        if (take(pendingPress, direction, now)) {
            pendingPress = null
            return true
        }
        pendingMove = PendingAction(direction, now)
        return false
    }

    fun notePress(direction: Direction, now: Long = System.currentTimeMillis()): Boolean {
        if (take(pendingMove, direction, now)) {
            pendingMove = null
            return true
        }
        pendingPress = PendingAction(direction, now)
        return false
    }

    private fun take(pending: PendingAction?, direction: Direction, now: Long): Boolean {
        if (pending == null) return false
        if (pending.direction == direction && (now - pending.timestamp) < PAIRING_WINDOW_MS) {
            return true
        }
        return false
    }
}
