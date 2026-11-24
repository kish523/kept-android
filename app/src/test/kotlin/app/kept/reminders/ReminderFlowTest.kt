package app.kept.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kept.KeptApplication
import app.kept.core.deadlines.ReminderKind
import app.kept.data.ReviewedItem
import app.kept.data.ReviewedReceipt
import app.kept.data.db.CaptureSource
import app.kept.data.db.ReceiptEntity
import app.kept.data.db.ReceiptStatus
import app.kept.data.db.ReminderEntity
import app.kept.data.db.ReminderStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The whole reminder path against the real database, AlarmManager and NotificationManager (Robolectric). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReminderFlowTest {
    private val app get() = ApplicationProvider.getApplicationContext<KeptApplication>()
    private val c get() = app.container
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val notes get() = shadowOf(app.getSystemService(NotificationManager::class.java))
    private val today = LocalDate.now()

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** A saved receipt: return in 10 days (reminders 7, 3, 1 before), warranty in 200 days (30, 7 before). */
    private fun saved(): String = runBlocking {
        val r = ReceiptEntity(source = CaptureSource.CAMERA, status = ReceiptStatus.NEEDS_REVIEW)
        c.database.receiptDao().insert(r)
        c.repository.saveReviewed(
            r.id,
            ReviewedReceipt(
                merchantName = "Northline", purchaseDate = today.minusDays(2), totalMinor = 34900, currency = "USD", category = null,
                returnBy = today.plusDays(10), returnNote = "Printed on the receipt", noReturns = false,
                warrantyEnds = today.plusDays(200), warrantyNote = null, lifetimeWarranty = false,
                items = listOf(ReviewedItem("Headphones", 1, 34900, null)), serialNumber = null, orderNumber = null, paymentLabel = null, notes = null,
            ),
            now = LocalDateTime.now(),
        )
        r.id
    }

    private fun reminders(id: String) = runBlocking { c.database.reminderDao().forReceipt(id) }
    private fun handle(action: String, reminderId: String) = runBlocking { handleReminderIntent(c, ReminderReceiver.intent(app, action, reminderId)) }

    @Test fun `saving arms one alarm per reminder`() {
        val id = saved()
        val rs = reminders(id)
        assertEquals(5, rs.size)
        assertTrue(rs.all { it.status == ReminderStatus.HANDED_TO_OS })
        assertEquals(5, alarms.scheduledAlarms.size)
    }

    @Test fun `an alarm shows a notification with the three buttons, and Keeping it stops return reminders`() {
        val id = saved()
        val ret = reminders(id).first { it.kind == ReminderKind.RETURN }
        handle(ReminderReceiver.ACTION_FIRE, ret.id)
        val n = notes.allNotifications.single()
        assertTrue(shadowOf(n).contentTitle.toString().startsWith("Return window closes in"))
        assertEquals(listOf("Keeping it", "Returned it", "Remind me tomorrow"), n.actions.map { it.title.toString() })

        handle(ReminderReceiver.ACTION_KEEP, ret.id)
        assertTrue(notes.allNotifications.isEmpty())
        val after = reminders(id)
        assertTrue(after.filter { it.kind == ReminderKind.RETURN }.all { it.status in setOf(ReminderStatus.CANCELLED, ReminderStatus.DELIVERED) })
        assertTrue(after.filter { it.kind == ReminderKind.WARRANTY }.all { it.status == ReminderStatus.HANDED_TO_OS })
        assertEquals(2, alarms.scheduledAlarms.size) // only the warranty ones remain
        assertTrue(runBlocking { c.database.itemDao().forReceipt(id).single().returnResolved })
    }

    @Test fun `Remind me tomorrow moves the reminder to 9am tomorrow`() {
        val id = saved()
        val w = reminders(id).first { it.kind == ReminderKind.WARRANTY }
        handle(ReminderReceiver.ACTION_SNOOZE, w.id)
        val moved = reminders(id).first { it.id == w.id }
        assertEquals(today.plusDays(1).atTime(LocalTime.of(9, 0)), moved.fireAt)
        assertEquals(ReminderStatus.HANDED_TO_OS, moved.status)
    }

    @Test fun `deleting stops alarms, undo re-arms them, purge erases the receipt`() {
        val id = saved()
        val before = runBlocking { c.repository.trash(id) }
        assertEquals(ReceiptStatus.ACTIVE, before)
        assertTrue(alarms.scheduledAlarms.isEmpty())
        runBlocking { c.repository.restore(id, before!!) }
        assertEquals(5, alarms.scheduledAlarms.size)
        runBlocking { c.repository.trash(id); c.repository.purge(listOf(id)) }
        assertNull(runBlocking { c.database.receiptDao().get(id) })
        assertTrue(reminders(id).isEmpty())
    }

    @Test fun `a reminder missed while the phone was off is shown on the next sync`() {
        val id = saved()
        val item = runBlocking { c.database.itemDao().forReceipt(id).single() }
        val missed = ReminderEntity(itemId = item.id, kind = ReminderKind.RETURN, dueDate = today.plusDays(10), daysBefore = 10, fireAt = LocalDateTime.now().minusHours(2))
        runBlocking { c.database.reminderDao().insertAll(listOf(missed)); c.scheduler.sync() }
        assertEquals(1, notes.allNotifications.size)
        assertEquals(ReminderStatus.DELIVERED, runBlocking { c.database.reminderDao().get(missed.id)!!.status })
    }

    @Test fun `alarms for a returned item are ignored when they fire`() {
        val id = saved()
        val ret = reminders(id).first { it.kind == ReminderKind.RETURN }
        runBlocking { c.repository.markReturned(ret.itemId) }
        handle(ReminderReceiver.ACTION_FIRE, ret.id)
        assertTrue(notes.allNotifications.isEmpty())
    }
}
