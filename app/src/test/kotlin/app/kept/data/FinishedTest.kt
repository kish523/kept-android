package app.kept.data

import app.kept.data.db.ItemEntity
import app.kept.data.db.ItemState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FinishedTest {
    private val today = LocalDate.of(2026, 11, 24)
    private fun item(
        returnBy: LocalDate? = null,
        warranty: LocalDate? = null,
        state: ItemState = ItemState.KEPT,
        resolved: Boolean = false,
        lifetime: Boolean = false,
    ) = ItemEntity(receiptId = "r", name = "x", returnBy = returnBy, warrantyEnds = warranty, state = state, returnResolved = resolved, lifetimeWarranty = lifetime)

    @Test fun `finished when returned or every window has closed`() {
        assertTrue(isFinished(listOf(item(state = ItemState.RETURNED, returnBy = today.plusDays(5))), today))
        assertTrue(isFinished(listOf(item(returnBy = today.minusDays(1), warranty = today.minusDays(1))), today))
        assertTrue(isFinished(listOf(item(returnBy = today.plusDays(5), resolved = true)), today))
    }

    @Test fun `not finished while anything is open`() {
        assertFalse(isFinished(listOf(item(returnBy = today)), today))
        assertFalse(isFinished(listOf(item(warranty = today.plusDays(300))), today))
        assertFalse(isFinished(listOf(item(lifetime = true)), today))
        assertFalse(isFinished(listOf(item(state = ItemState.RETURNED), item(warranty = today.plusDays(9))), today))
        assertFalse(isFinished(emptyList(), today))
    }
}
