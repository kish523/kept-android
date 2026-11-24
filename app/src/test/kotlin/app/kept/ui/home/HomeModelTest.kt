package app.kept.ui.home

import app.kept.core.parser.model.Category
import app.kept.data.db.DeadlineRow
import app.kept.data.db.ItemState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HomeModelTest {
    private val today = LocalDate.of(2026, 11, 24)

    private fun row(
        id: String,
        bought: LocalDate? = today.minusDays(10),
        returnBy: LocalDate? = null,
        warranty: LocalDate? = null,
        resolved: Boolean = false,
        noReturns: Boolean = false,
    ) = DeadlineRow(id, "r-$id", "Item $id", "Store", bought, Category.ELECTRONICS, returnBy, warranty, noReturns, resolved, ItemState.KEPT)

    @Test fun `returns closing are sorted soonest first and the hero is the most urgent`() {
        val rows = listOf(
            row("a", returnBy = today.plusDays(15)),
            row("b", returnBy = today.plusDays(4)),
            row("c", warranty = today.plusDays(2)),
        )
        val home = buildHome(rows, emptyList(), today, HomeFilter.ALL, null)
        assertEquals(listOf("b", "a"), home.sections.first { it.title == "Returns closing" }.rows.map { it.itemId })
        assertEquals("c", home.hero!!.itemId)
        assertEquals(DeadlineKind.WARRANTY, home.hero!!.kind)
    }

    @Test fun `keeping it takes the item off returns closing and out of the hero`() {
        val rows = listOf(row("a", returnBy = today.plusDays(3), resolved = true))
        val home = buildHome(rows, emptyList(), today, HomeFilter.ALL, null)
        assertNull(home.hero)
        assertEquals(0, home.returnsCount)
        val other = home.sections.single { it.title == "Everything else" }.rows.single()
        assertEquals("Kept · nothing left to track", other.label)
    }

    @Test fun `a kept item with a warranty still shows its warranty`() {
        val rows = listOf(row("a", returnBy = today.plusDays(3), warranty = today.plusDays(40), resolved = true))
        val home = buildHome(rows, emptyList(), today, HomeFilter.ALL, null)
        assertEquals("a", home.sections.single { it.title == "Warranties ending" }.rows.single().itemId)
        assertEquals(DeadlineKind.WARRANTY, home.hero!!.kind)
    }

    @Test fun `filters and search narrow the sections but not the hero`() {
        val rows = listOf(row("a", returnBy = today.plusDays(5)), row("b", warranty = today.plusDays(30)))
        val returnsOnly = buildHome(rows, emptyList(), today, HomeFilter.RETURNS, null)
        assertEquals(listOf("Returns closing"), returnsOnly.sections.map { it.title })
        val searched = buildHome(rows, emptyList(), today, HomeFilter.ALL, setOf("r-b"))
        assertEquals(listOf("b"), searched.sections.flatMap { it.rows }.map { it.itemId })
        assertEquals("a", searched.hero!!.itemId)
    }

    @Test fun `window bar and ring reflect how much time is used and left`() {
        val rows = listOf(row("a", bought = today.minusDays(26), returnBy = today.plusDays(4)))
        val home = buildHome(rows, emptyList(), today, HomeFilter.ALL, null)
        val r = home.sections.first().rows.single()
        assertEquals(26f / 30f, r.windowUsed!!, 0.001f)
        assertEquals(4f / 30f, home.hero!!.fractionLeft, 0.001f)
        assertTrue(r.soon)
    }

    @Test fun `past deadlines and no-returns items have no open deadline`() {
        val rows = listOf(row("a", returnBy = today.minusDays(1)), row("b", returnBy = today.plusDays(9), noReturns = true))
        val home = buildHome(rows, emptyList(), today, HomeFilter.ALL, null)
        assertNull(home.hero)
        assertEquals(listOf("No open deadlines", "No open deadlines"), home.sections.single().rows.map { it.label })
    }

    @Test fun `days labels`() {
        assertEquals("Today", daysLabel(0))
        assertEquals("1 day", daysLabel(1))
        assertEquals("45 days", daysLabel(45))
        assertEquals("11 mo", daysLabel(339))
    }
}
