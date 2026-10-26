package com.tqfapp.android

import androidx.test.platform.app.InstrumentationRegistry
import com.tqfapp.android.data.local.preferences.SessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {

    @Test
    fun testSessionFlow() {
        val context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        val session = SessionManager(context)

        session.logout()

        assertFalse(session.isLoggedIn())

        session.saveLogin("user123")

        assertTrue(session.isLoggedIn())
        assertEquals("user123", session.getUserId())

        session.logout()

        assertFalse(session.isLoggedIn())
    }
}