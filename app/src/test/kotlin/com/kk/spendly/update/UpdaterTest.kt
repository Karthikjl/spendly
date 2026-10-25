package com.kk.spendly.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    private val digest = "a".repeat(64)

    private fun release(
        tag: String = "v1.2.0",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: String = """[{"name":"spendly-1.2.0.apk","size":12345,"digest":"sha256:$digest","browser_download_url":"https://github.com/o/r/releases/download/v1.2.0/spendly-1.2.0.apk"}]""",
    ) = """{"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"body":"## What's new\n- Faster alerts","assets":$assets}"""

    @Test
    fun comparesDottedVersionsNumerically() {
        assertTrue(Updater.isNewer("1.0.1", "1.0.0"))
        assertTrue(Updater.isNewer("v1.10.0", "1.9.2"))
        assertTrue(Updater.isNewer("2.0", "1.9.9"))
        assertFalse(Updater.isNewer("1.0.0", "1.0.0"))
        assertFalse(Updater.isNewer("1.0", "1.0.0"))
        assertFalse(Updater.isNewer("0.9.9", "1.0.0"))
    }

    @Test
    fun ignoresPrereleaseSuffixAndGarbage() {
        assertTrue(Updater.isNewer("1.1.0-beta", "1.0.0"))
        assertFalse(Updater.isNewer("not-a-version", "1.0.0"))
    }

    @Test
    fun parsesLatestRelease() {
        val info = Updater.parseRelease(release())!!
        assertEquals("1.2.0", info.version)
        assertEquals(12345L, info.sizeBytes)
        assertEquals(digest, info.sha256)
        assertTrue(info.apkUrl.startsWith("https://"))
        assertTrue(info.notes.contains("Faster alerts"))
    }

    @Test
    fun skipsDraftsAndPrereleases() {
        assertNull(Updater.parseRelease(release(draft = true)))
        assertNull(Updater.parseRelease(release(prerelease = true)))
    }

    @Test
    fun needsAnApkAsset() {
        assertNull(Updater.parseRelease(release(assets = """[{"name":"notes.txt","size":1,"browser_download_url":"https://x/y"}]""")))
        assertNull(Updater.parseRelease(release(assets = "[]")))
    }

    @Test
    fun rejectsNonHttpsDownloads() {
        val http = """[{"name":"a.apk","size":1,"browser_download_url":"http://example.com/a.apk"}]"""
        assertNull(Updater.parseRelease(release(assets = http)))
    }

    @Test
    fun dropsMalformedDigest() {
        val bad = """[{"name":"a.apk","size":1,"digest":"sha256:zz","browser_download_url":"https://example.com/a.apk"}]"""
        assertNull(Updater.parseRelease(release(assets = bad))!!.sha256)
    }

    @Test
    fun survivesGarbageJson() {
        assertNull(Updater.parseRelease("not json"))
        assertNotNull(Updater.parseRelease(release()))
    }

    @Test
    fun highlightSkipsHeadingsAndBullets() {
        assertEquals("Faster alerts", Updater.parseRelease(release())!!.highlight)
        val bare = ReleaseInfo("1.0.0", "", "https://x", 0, null)
        assertNull(bare.highlight)
    }
}
