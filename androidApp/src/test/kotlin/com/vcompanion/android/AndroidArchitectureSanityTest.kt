package com.vcompanion.android

import com.vcompanion.shared.domain.model.SessionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AndroidArchitectureSanityTest {
    @Test
    fun shouldAccessSharedDomainFromAndroidModule() {
        val session = SessionConfig(
            sessionId = "android-session-01",
            hostIp = "192.168.1.50",
            port = 9000,
            secretToken = "tok_test_abc"
        )
        assertNotNull(session)
        assertEquals("android-session-01", session.sessionId)
        assertEquals(9000, session.port)
    }
}
