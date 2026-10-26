package org.getlumeroid.android

import org.getlumeroid.android.core.player.RemoteDirectionGate
import org.junit.Assert.*
import org.junit.Test

class RemoteDirectionGateTest {

    @Test
    fun testMoveThenPressPairsSuccessfully() {
        val gate = RemoteDirectionGate()
        val now = 1000L

        // Swipe / move first
        val moveResult = gate.noteMove(RemoteDirectionGate.Direction.UP, now)
        assertFalse("Initial move without prior press should not trigger immediately", moveResult)

        // Button press comes within pairing window (100ms later)
        val pressResult = gate.notePress(RemoteDirectionGate.Direction.UP, now + 100)
        assertTrue("Move followed promptly by press should pair and return true", pressResult)
    }

    @Test
    fun testPressThenMovePairsSuccessfully() {
        val gate = RemoteDirectionGate()
        val now = 1000L

        // Press first
        val pressResult = gate.notePress(RemoteDirectionGate.Direction.DOWN, now)
        assertFalse("Initial press without prior move should not trigger immediately", pressResult)

        // Move follows within pairing window
        val moveResult = gate.noteMove(RemoteDirectionGate.Direction.DOWN, now + 150)
        assertTrue("Press followed promptly by move should pair and return true", moveResult)
    }

    @Test
    fun testExpiredActionsDoNotPair() {
        val gate = RemoteDirectionGate()
        val now = 1000L

        // Move happens
        gate.noteMove(RemoteDirectionGate.Direction.LEFT, now)

        // Press happens well after 300ms window (e.g. 500ms later)
        val pressResult = gate.notePress(RemoteDirectionGate.Direction.LEFT, now + 500)
        assertFalse("Actions outside 300ms pairing window should expire and not pair", pressResult)
    }

    @Test
    fun testMismatchedDirectionsDoNotPair() {
        val gate = RemoteDirectionGate()
        val now = 1000L

        gate.noteMove(RemoteDirectionGate.Direction.UP, now)
        val pressResult = gate.notePress(RemoteDirectionGate.Direction.DOWN, now + 50)
        assertFalse("Different directions should never pair", pressResult)
    }
}
