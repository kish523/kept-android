package app.kept.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

@Database(
    entities = [
        ReceiptEntity::class,
        ItemEntity::class,
        AttachmentEntity::class,
        ExtractionResultEntity::class,
        ReminderEntity::class,
        ItemGroupEntity::class,
        TagEntity::class,
        ReceiptTagEntity::class,
        ReceiptSearchEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KeptDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao
    abstract fun itemDao(): ItemDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun extractionDao(): ExtractionDao
    abstract fun reminderDao(): ReminderDao
    abstract fun searchDao(): SearchDao

    companion object {
        const val NAME = "kept.db"

        fun create(context: Context): KeptDatabase =
            Room.databaseBuilder(context, KeptDatabase::class.java, NAME)
                // WAL is Room's default on most devices; set explicitly because crash-safety matters here.
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                // Never add fallbackToDestructiveMigration(): it deletes users' receipts.
                .build()
    }
}

/** Dates as ISO text (sortable, readable in exports); instants as epoch milliseconds. */
class Converters {
    @TypeConverter fun localDateToText(d: LocalDate?): String? = d?.toString()
    @TypeConverter fun textToLocalDate(s: String?): LocalDate? = s?.let(LocalDate::parse)
    @TypeConverter fun localDateTimeToText(d: LocalDateTime?): String? = d?.toString()
    @TypeConverter fun textToLocalDateTime(s: String?): LocalDateTime? = s?.let(LocalDateTime::parse)
    @TypeConverter fun instantToMillis(i: Instant?): Long? = i?.toEpochMilli()
    @TypeConverter fun millisToInstant(ms: Long?): Instant? = ms?.let(Instant::ofEpochMilli)
}
