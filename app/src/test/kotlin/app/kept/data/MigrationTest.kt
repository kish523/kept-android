package app.kept.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.kept.data.db.KeptDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Every schema change must keep users' receipts. Runs the real migrations against the exported old schema. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KeptDatabase::class.java)

    @Test fun `v1 to v2 keeps receipts and items and adds returnResolved = false`() {
        helper.createDatabase(DB, 1).apply {
            execSQL(
                "INSERT INTO receipts (id, merchantName, purchaseDate, source, status, docType, createdAt, updatedAt) " +
                    "VALUES ('r1', 'Northline', '2026-11-01', 'CAMERA', 'ACTIVE', 'RECEIPT', 0, 0)",
            )
            execSQL(
                "INSERT INTO items (id, receiptId, name, quantity, returnBy, noReturns, lifetimeWarranty, state) " +
                    "VALUES ('i1', 'r1', 'Headphones', 1, '2026-12-01', 0, 0, 'KEPT')",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 2, true)
        db.query("SELECT name, returnBy, returnResolved FROM items WHERE id = 'i1'").use { c ->
            c.moveToFirst()
            assertEquals("Headphones", c.getString(0))
            assertEquals("2026-12-01", c.getString(1))
            assertEquals(0, c.getInt(2))
        }
        db.query("SELECT merchantName FROM receipts WHERE id = 'r1'").use { c ->
            c.moveToFirst()
            assertEquals("Northline", c.getString(0))
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
