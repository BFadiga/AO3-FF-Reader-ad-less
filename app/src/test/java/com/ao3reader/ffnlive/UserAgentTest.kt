package com.ao3reader.ffnlive

import com.ao3reader.data.remote.web.desktopUserAgent
import org.junit.Assert.assertEquals
import org.junit.Test

class UserAgentTest {
    @Test
    fun keepsChromeVersionAndDropsPhoneMarkers() {
        val ua = desktopUserAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP2A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/129.0.6668.100 Mobile Safari/537.36")
        assertEquals("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.6668.100 Safari/537.36", ua)
    }
}
