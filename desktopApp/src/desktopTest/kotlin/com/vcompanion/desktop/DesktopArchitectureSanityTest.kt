package com.vcompanion.desktop

import com.vcompanion.shared.domain.model.SessionConfig
import com.vcompanion.shared.resources.Res
import com.vcompanion.shared.resources.app_name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DesktopArchitectureSanityTest {

    @Test
    fun shouldConsumeSharedDomainInDesktopApp() {
        val session = SessionConfig(
            sessionId = "desktop-session-99",
            hostIp = "127.0.0.1",
            port = 8080,
            secretToken = "tok_desktop_secret"
        )
        assertNotNull(session)
        assertEquals("desktop-session-99", session.sessionId)
        assertEquals("127.0.0.1", session.hostIp)
        assertEquals(8080, session.port)
    }

    @Test
    fun shouldAccessSharedResourcesInDesktopApp() {
        assertEquals("app_name", Res.string.app_name.key)
    }
}
