package app.kept.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.kept.core.deadlines.ReminderKind
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ReceiptDao {
    @Insert suspend fun insert(receipt: ReceiptEntity)
    @Update suspend fun update(receipt: ReceiptEntity)

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun get(id: String): ReceiptEntity?

    @Query("SELECT * FROM receipts WHERE id = :id")
    fun observe(id: String): Flow<ReceiptEntity?>

    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun getWithDetails(id: String): ReceiptWithDetails?

    @Query("UPDATE receipts SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: String, status: ReceiptStatus, now: Instant = Instant.now())

    /** Drafts and receipts whose reading needs a human check. Shown at the top of Home. */
    @Query("SELECT * FROM receipts WHERE status IN ('DRAFT', 'NEEDS_REVIEW') ORDER BY createdAt DESC")
    fun observeUnfinished(): Flow<List<ReceiptEntity>>

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ItemDao {
    @Insert suspend fun insertAll(items: List<ItemEntity>)

    @Query("SELECT * FROM items WHERE receiptId = :receiptId")
    suspend fun forReceipt(receiptId: String): List<ItemEntity>

    @Query("DELETE FROM items WHERE receiptId = :receiptId")
    suspend fun deleteForReceipt(receiptId: String)

    @Query("UPDATE items SET state = :state WHERE id = :itemId")
    suspend fun setState(itemId: String, state: ItemState)

    @Query("UPDATE items SET returnResolved = :resolved WHERE id = :itemId")
    suspend fun setReturnResolved(itemId: String, resolved: Boolean)

    @Query("SELECT * FROM items WHERE id = :itemId")
    suspend fun get(itemId: String): ItemEntity?

    /** Active items with any deadline, nearest first. Dates are ISO text, so they sort correctly. */
    @Query(
        """
        SELECT i.id AS itemId, i.receiptId AS receiptId, i.name AS itemName, r.merchantName AS merchantName,
               r.purchaseDate AS purchaseDate, COALESCE(i.category, r.category) AS category,
               i.returnBy AS returnBy, i.warrantyEnds AS warrantyEnds, i.noReturns AS noReturns,
               i.returnResolved AS returnResolved, i.state AS state
        FROM items i JOIN receipts r ON r.id = i.receiptId
        WHERE r.status = 'ACTIVE' AND i.state IN ('KEPT', 'RETURNING')
        ORDER BY COALESCE(i.returnBy, i.warrantyEnds) IS NULL, COALESCE(i.returnBy, i.warrantyEnds)
        """,
    )
    fun observeDeadlines(): Flow<List<DeadlineRow>>
}

@Dao
interface AttachmentDao {
    @Insert suspend fun insertAll(attachments: List<AttachmentEntity>)

    @Query("SELECT * FROM attachments WHERE receiptId = :receiptId ORDER BY pageIndex")
    suspend fun forReceipt(receiptId: String): List<AttachmentEntity>

    @Query("UPDATE attachments SET ocrText = :text WHERE id = :id")
    suspend fun setOcrText(id: String, text: String)
}

@Dao
interface ExtractionDao {
    @Insert suspend fun insert(result: ExtractionResultEntity)
    @Update suspend fun update(result: ExtractionResultEntity)

    @Query("SELECT * FROM extraction_results WHERE receiptId = :receiptId ORDER BY createdAt DESC LIMIT 1")
    fun observeLatest(receiptId: String): Flow<ExtractionResultEntity?>

    @Query("SELECT * FROM extraction_results WHERE receiptId = :receiptId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(receiptId: String): ExtractionResultEntity?
}

@Dao
interface ReminderDao {
    @Insert suspend fun insertAll(reminders: List<ReminderEntity>)

    @Query("DELETE FROM reminders WHERE itemId IN (SELECT id FROM items WHERE receiptId = :receiptId)")
    suspend fun deleteForReceipt(receiptId: String)

    /** Moves an item's reminders of the given kinds from one set of statuses to another (cancel, or undo a cancel). */
    @Query("UPDATE reminders SET status = :to WHERE itemId = :itemId AND kind IN (:kinds) AND status IN (:from)")
    suspend fun moveStatus(itemId: String, kinds: List<ReminderKind>, from: List<ReminderStatus>, to: ReminderStatus)

    /** What the alarm scheduler re-arms after boot, update or a clock change. */
    @Query("SELECT * FROM reminders WHERE status IN ('PENDING', 'HANDED_TO_OS', 'SNOOZED') ORDER BY fireAt")
    suspend fun upcoming(): List<ReminderEntity>
}

@Dao
interface SearchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(row: ReceiptSearchEntity)

    @Query("DELETE FROM receipt_search WHERE receiptId = :receiptId")
    suspend fun deleteForReceipt(receiptId: String)

    /** [query] uses FTS syntax; [app.kept.data.ReceiptRepository.search] builds it from user input. */
    @Query("SELECT DISTINCT receiptId FROM receipt_search WHERE receipt_search MATCH :query")
    suspend fun search(query: String): List<String>
}
