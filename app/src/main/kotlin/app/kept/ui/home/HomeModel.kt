package app.kept.ui.home

import app.kept.core.parser.model.Category
import app.kept.data.db.DeadlineRow
import app.kept.data.db.ReceiptEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class HomeFilter { ALL, RETURNS, WARRANTIES }
enum class DeadlineKind { RETURN, WARRANTY }

/** One row on Home, ready to draw. */
data class HomeRow(
    val itemId: String,
    val receiptId: String,
    val name: String,
    val merchant: String?,
    val category: Category?,
    /** "Return by Nov 28", "Warranty until Jan 12", "No open deadlines". */
    val label: String,
    val daysLeft: Long?,
    /** How much of the window has gone, 0..1, for the thin progress bar. Null when unknown. */
    val windowUsed: Float?,
) {
    val soon: Boolean get() = daysLeft != null && daysLeft <= 7
}

/** The "Next up" card: the single most urgent open deadline. */
data class HeroModel(
    val kind: DeadlineKind,
    val itemId: String,
    val receiptId: String,
    val name: String,
    val merchant: String?,
    val daysLeft: Long,
    /** Share of the window still left, 0..1: the ring. */
    val fractionLeft: Float,
    val due: LocalDate,
)

data class HomeSection(val title: String, val rows: List<HomeRow>)

data class HomeContent(
    val hero: HeroModel?,
    val sections: List<HomeSection>,
    val allCount: Int,
    val returnsCount: Int,
    val warrantiesCount: Int,
    val unfinished: List<ReceiptEntity>,
)

private val SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val WITH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

const val RETURNS_WINDOW_DAYS = 30L
const val WARRANTIES_WINDOW_DAYS = 90L

/**
 * Sorts deadline rows into Home's sections. Pure, so it's unit-tested.
 *
 * [matches] is the set of receipt ids that match the search, or null when not searching.
 */
fun buildHome(
    rows: List<DeadlineRow>,
    unfinished: List<ReceiptEntity>,
    today: LocalDate,
    filter: HomeFilter,
    matches: Set<String>?,
): HomeContent {
    fun returnOpen(r: DeadlineRow) = r.returnBy != null && !r.noReturns && !r.returnResolved && !r.returnBy.isBefore(today)
    fun warrantyOpen(r: DeadlineRow) = r.warrantyEnds != null && !r.warrantyEnds.isBefore(today)
    fun days(d: LocalDate) = ChronoUnit.DAYS.between(today, d)
    fun used(start: LocalDate?, end: LocalDate): Float? {
        val span = start?.let { ChronoUnit.DAYS.between(it, end) } ?: return null
        if (span <= 0) return null
        return ((span - days(end)).toFloat() / span).coerceIn(0.04f, 1f)
    }

    val visible = if (matches == null) rows else rows.filter { it.receiptId in matches }
    val returns = visible.filter { returnOpen(it) && days(it.returnBy!!) <= RETURNS_WINDOW_DAYS }.sortedBy { it.returnBy }
    val returnIds = returns.map { it.itemId }.toSet()
    val warranties = visible
        .filter { it.itemId !in returnIds && warrantyOpen(it) && days(it.warrantyEnds!!) <= WARRANTIES_WINDOW_DAYS }
        .sortedBy { it.warrantyEnds }
    val warrantyIds = warranties.map { it.itemId }.toSet()
    val other = visible.filter { it.itemId !in returnIds && it.itemId !in warrantyIds }

    fun returnRow(r: DeadlineRow) = HomeRow(
        r.itemId, r.receiptId, r.itemName, r.merchantName, r.category,
        "Return by ${r.returnBy!!.format(SHORT)}", days(r.returnBy), used(r.purchaseDate, r.returnBy),
    )
    fun warrantyRow(r: DeadlineRow): HomeRow {
        val d = days(r.warrantyEnds!!)
        return HomeRow(
            r.itemId, r.receiptId, r.itemName, r.merchantName, r.category,
            "Warranty until ${r.warrantyEnds.format(if (d > 60) WITH_YEAR else SHORT)}", d, used(r.purchaseDate, r.warrantyEnds),
        )
    }
    fun otherRow(r: DeadlineRow): HomeRow {
        val ret = r.returnBy?.takeIf { returnOpen(r) }
        val war = r.warrantyEnds?.takeIf { warrantyOpen(r) }
        return when {
            ret != null && (war == null || !ret.isAfter(war)) -> returnRow(r)
            war != null -> warrantyRow(r)
            else -> HomeRow(
                r.itemId, r.receiptId, r.itemName, r.merchantName, r.category,
                if (r.returnResolved) "Kept · nothing left to track" else "No open deadlines", null, null,
            )
        }
    }

    val sections = buildList {
        if (filter != HomeFilter.WARRANTIES && returns.isNotEmpty()) add(HomeSection("Returns closing", returns.map(::returnRow)))
        if (filter != HomeFilter.RETURNS && warranties.isNotEmpty()) add(HomeSection("Warranties ending", warranties.map(::warrantyRow)))
        if (filter == HomeFilter.ALL && other.isNotEmpty()) add(HomeSection("Everything else", other.map(::otherRow)))
    }

    // The hero ignores the search box: it's always the most urgent thing overall.
    val hero = rows.flatMap { r ->
        buildList {
            if (returnOpen(r) && days(r.returnBy!!) <= RETURNS_WINDOW_DAYS) add(Triple(r, DeadlineKind.RETURN, r.returnBy))
            if (warrantyOpen(r) && days(r.warrantyEnds!!) <= WARRANTIES_WINDOW_DAYS) add(Triple(r, DeadlineKind.WARRANTY, r.warrantyEnds))
        }
    }.minByOrNull { days(it.third) }?.let { (r, kind, due) ->
        val left = days(due)
        val span = r.purchaseDate?.let { ChronoUnit.DAYS.between(it, due) }?.takeIf { it > 0 }
        HeroModel(
            kind = kind,
            itemId = r.itemId,
            receiptId = r.receiptId,
            name = r.itemName,
            merchant = r.merchantName,
            daysLeft = left,
            fractionLeft = span?.let { (left.toFloat() / it).coerceIn(0.03f, 1f) } ?: 1f,
            due = due,
        )
    }

    return HomeContent(
        hero = hero,
        sections = sections,
        allCount = visible.size,
        returnsCount = returns.size,
        warrantiesCount = warranties.size,
        unfinished = if (matches == null) unfinished else unfinished.filter { it.id in matches },
    )
}

/** "4 days", "Today", "11 mo". */
fun daysLabel(days: Long): String = when {
    days <= 0L -> "Today"
    days == 1L -> "1 day"
    days <= 60L -> "$days days"
    else -> "${Math.round(days / 30.4)} mo"
}
