package app.kept

import android.content.Context
import app.kept.capture.ExtractionPipeline
import app.kept.capture.OcrEngine
import app.kept.core.deadlines.MerchantPolicy
import app.kept.core.deadlines.MerchantPolicyTable
import app.kept.core.parser.ParserConfig
import app.kept.core.parser.model.Category
import app.kept.data.ReceiptRepository
import app.kept.data.db.KeptDatabase
import app.kept.data.files.AttachmentStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

/**
 * Hand-written dependency container. Small enough not to need Hilt; swap in Hilt later if it grows.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Work that must finish even if the screen that started it closes (reading a receipt). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: KeptDatabase by lazy { KeptDatabase.create(appContext) }
    val attachments: AttachmentStore by lazy { AttachmentStore(appContext) }
    val repository: ReceiptRepository by lazy { ReceiptRepository(database, attachments) }
    val policies: MerchantPolicyTable by lazy { loadPolicies() }

    val extraction: ExtractionPipeline by lazy {
        ExtractionPipeline(database, attachments, OcrEngine(appContext), repository) {
            ParserConfig.forLocale(Locale.getDefault(), LocalDate.now(), policies.aliasMap())
        }
    }

    private fun loadPolicies(): MerchantPolicyTable {
        val json = appContext.assets.open("merchant_policies.json").bufferedReader().use { it.readText() }
        val array = JSONObject(json).getJSONArray("policies")
        val list = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val aliases = o.getJSONArray("aliases")
            val byCategory = o.optJSONObject("categoryReturnDays")
            MerchantPolicy(
                name = o.getString("name"),
                aliases = (0 until aliases.length()).map { aliases.getString(it) },
                returnDays = if (o.isNull("returnDays")) null else o.getInt("returnDays"),
                categoryReturnDays = byCategory?.keys()?.asSequence()
                    ?.mapNotNull { key -> runCatching { Category.valueOf(key) }.getOrNull()?.let { it to byCategory.getInt(key) } }
                    ?.toMap().orEmpty(),
                asOf = if (o.isNull("asOf")) null else LocalDate.parse(o.getString("asOf")),
                returnsUrl = if (o.isNull("returnsUrl")) null else o.getString("returnsUrl"),
            )
        }
        return MerchantPolicyTable(list)
    }
}
