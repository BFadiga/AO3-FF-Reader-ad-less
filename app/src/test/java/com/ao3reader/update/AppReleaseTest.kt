package com.ao3reader.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppReleaseTest {
    @Test
    fun readsBuildNumberAndApk() {
        val json = """{"tag_name":"build-107","name":"Build 107","body":"Fix sign-in",
            "assets":[{"name":"notes.txt","browser_download_url":"x","size":1},
                      {"name":"ao3-reader.apk","browser_download_url":"https://example.org/a.apk","size":2048}]}"""
        val r = AppUpdater.parseRelease(json)!!
        assertEquals(107, r.versionCode)
        assertEquals("https://example.org/a.apk", r.apkUrl)
        assertEquals(2048L, r.sizeBytes)
    }

    @Test
    fun ignoresReleaseWithoutApk() {
        assertNull(AppUpdater.parseRelease("""{"tag_name":"build-5","assets":[]}"""))
    }
}
