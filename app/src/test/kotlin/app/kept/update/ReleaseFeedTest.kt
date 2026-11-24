package app.kept.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Robolectric only because the parser uses Android's org.json. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReleaseFeedTest {
    private fun release(tag: String, apk: Boolean = true, draft: Boolean = false) = """
        {"tag_name":"$tag","draft":$draft,"body":"notes for $tag",
         "assets":[${if (apk) """{"name":"kept.apk","browser_download_url":"https://example.invalid/$tag.apk","size":123}""" else ""}]}
    """

    @Test fun `picks the newest build above the installed one`() {
        val json = "[" + listOf(release("v0.2.1-build-3"), release("v0.2.2-build-5"), release("v0.2.1-build-4")).joinToString(",") + "]"
        val u = pickUpdate(json, currentBuild = 3)!!
        assertEquals(5, u.build)
        assertEquals("0.2.2", u.versionName)
        assertEquals("https://example.invalid/v0.2.2-build-5.apk", u.apkUrl)
        assertEquals("notes for v0.2.2-build-5", u.notes)
    }

    @Test fun `nothing newer means no update`() {
        assertNull(pickUpdate("[" + release("v0.2.1-build-3") + "]", currentBuild = 3))
        assertNull(pickUpdate("[]", currentBuild = 3))
    }

    @Test fun `ignores drafts, odd tags and releases without an apk`() {
        val json = "[" + listOf(release("v0.3.0-build-9", draft = true), release("nightly"), release("v0.3.0-build-8", apk = false), release("v0.2.2-build-4")).joinToString(",") + "]"
        assertEquals(4, pickUpdate(json, currentBuild = 3)!!.build)
    }
}
