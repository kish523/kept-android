package app.kept.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChangelogTest {
    private val sample = """
        # Changelog

        ## 0.2.1: reminders

        **Reminders**
        - Kept now reminds you: at 9:00, before a return
          window closes.
        - Answer with **Keeping it**.

        Plain paragraph here.

        ## 0.2.0: the redesign

        - Old stuff
    """.trimIndent()

    @Test fun `reads one version's section into headings, bullets and paragraphs`() {
        val blocks = changelogSection(sample, "0.2.1")
        assertEquals(
            listOf(
                NoteBlock.Heading("Reminders"),
                NoteBlock.Bullet("Kept now reminds you: at 9:00, before a return window closes."),
                NoteBlock.Bullet("Answer with **Keeping it**."),
                NoteBlock.Paragraph("Plain paragraph here."),
            ),
            blocks,
        )
        assertEquals("reminders", changelogTitle(sample, "0.2.1"))
        assertEquals(listOf(NoteBlock.Bullet("Old stuff")), changelogSection(sample, "0.2.0"))
        assertTrue(changelogSection(sample, "9.9.9").isEmpty())
    }

    @Test fun `the real changelog has a section for the app's current version`() {
        val text = File("../CHANGELOG.md").readText()
        val version = Regex("""versionName = "([^"]+)"""").find(File("build.gradle.kts").readText())!!.groupValues[1]
        val blocks = changelogSection(text, version)
        assertTrue("CHANGELOG.md needs a '## $version' section for What's new", blocks.isNotEmpty())
        assertTrue(blocks.any { it is NoteBlock.Heading })
    }
}
