package app.kept.screenshots

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kept.KeptApplication
import app.kept.core.parser.ParserConfig
import app.kept.core.parser.ReceiptParser
import app.kept.core.parser.model.Category
import app.kept.data.ExtractionJson
import app.kept.data.db.CaptureSource
import app.kept.data.db.ExtractionResultEntity
import app.kept.data.db.ExtractionStatus
import app.kept.data.db.ExtractionTier
import app.kept.data.db.ItemEntity
import app.kept.data.db.ReceiptEntity
import app.kept.data.db.ReceiptStatus
import app.kept.ui.home.HomeScreen
import app.kept.ui.home.SettingsContent
import app.kept.ui.home.WhatsNewContent
import app.kept.data.settings.ThemeMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import app.kept.ui.home.HomeViewModel
import app.kept.ui.review.ReviewScreen
import app.kept.ui.review.ReviewViewModel
import app.kept.ui.theme.Kept
import app.kept.ui.theme.KeptTheme
import app.kept.ui.theme.KeptEffects
import app.kept.ui.theme.LocalKeptEffects
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.util.Locale

/**
 * Renders Home and the review card in light and dark to build/screenshots/ for a visual check.
 * Not a pixel-diff test. Run with: gradle :app:testDebugUnitTest --tests '*ScreenshotTest*' -Pscreenshots
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h850dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<KeptApplication>()
    private val outDir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }

    private fun enabled() = assumeTrue("Pass -Pscreenshots to render", System.getProperty("screenshots.dir") != null)

    private fun seedHome() = runBlocking {
        val db = app.container.database
        val today = LocalDate.now()
        fun receipt(id: String, merchant: String, bought: LocalDate, cat: Category) =
            ReceiptEntity(id = id, merchantName = merchant, purchaseDate = bought, category = cat, currency = "USD", source = CaptureSource.CAMERA, status = ReceiptStatus.ACTIVE)
        val data = listOf(
            receipt("hp", "Northline Electronics", today.minusDays(26), Category.ELECTRONICS) to
                ItemEntity(receiptId = "hp", name = "Noise-cancelling headphones", returnBy = today.plusDays(4), warrantyEnds = today.plusMonths(11)),
            receipt("jk", "Fieldwork Outfitters", today.minusDays(23), Category.CLOTHING) to
                ItemEntity(receiptId = "jk", name = "Waxed field jacket", returnBy = today.plusDays(7)),
            receipt("do", "Harbor & Home", today.minusDays(15), Category.HOME) to
                ItemEntity(receiptId = "do", name = "Cast-iron Dutch oven", returnBy = today.plusDays(15)),
            receipt("vc", "Kestrel Appliances", today.minusMonths(22).minusDays(11), Category.APPLIANCES) to
                ItemEntity(receiptId = "vc", name = "Cordless stick vacuum", noReturns = true, warrantyEnds = today.plusDays(49)),
            receipt("ch", "Oak & Pine", today.minusMonths(20), Category.FURNITURE) to
                ItemEntity(receiptId = "ch", name = "Lounge chair", returnBy = today.minusMonths(8)),
        )
        data.forEach { (r, i) ->
            db.receiptDao().insert(r)
            db.itemDao().insertAll(listOf(i.copy(category = r.category)))
        }
    }

    private fun seedReview(sample: String = "us_electronics", locale: Locale = Locale.US): String = runBlocking {
        val db = app.container.database
        val text = File("../core/parser/src/test/resources/golden/$sample.txt").readText()
        val parsed = ReceiptParser(ParserConfig.forLocale(locale, LocalDate.now(), app.container.policies.aliasMap())).parseText(text)
        val r = ReceiptEntity(source = CaptureSource.CAMERA, status = ReceiptStatus.DRAFT)
        db.receiptDao().insert(r)
        app.container.repository.applyExtraction(r.id, parsed)
        db.extractionDao().insert(
            ExtractionResultEntity(receiptId = r.id, tier = ExtractionTier.RULES, status = ExtractionStatus.DONE, fieldsJson = ExtractionJson.encode(parsed)),
        )
        r.id
    }

    /** Lets real background threads (Room) deliver, while stepping the frame clock past entrance animations. */
    private fun settle(ms: Long = 3000) {
        // Compose tests pause infinite animations (glow, shimmer), so idling here terminates.
        compose.mainClock.autoAdvance = true
        repeat(30) {
            Thread.sleep(50)
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    private fun save(name: String) {
        // Software draw of the whole window. Backdrop blur needs the GPU, so bars look flat here.
        val root = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { root.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun home_light() = home(false)
    @Test fun home_lite() = home(false, KeptEffects.Lite, "home_lite")
    @Test fun home_dark() = home(true)
    @Test fun review_light() = review(false)
    @Test fun review_dark() = review(true)
    @Config(qualifiers = "w392dp-h1300dp-xxhdpi") // tall enough for the whole sheet
    @Test fun settings() = sheet("settings") { vm ->
        SettingsContent(vm, ThemeMode.SYSTEM, dark = false, onDismiss = {})
    }

    @Test fun whats_new() = sheet("whats_new") { vm ->
        WhatsNewContent(vm.loadWhatsNew()!!, onDismiss = {})
    }

    /** A sheet's content on the sheet's background (the sheet itself is a separate window). */
    private fun sheet(name: String, content: @androidx.compose.runtime.Composable (HomeViewModel) -> Unit) {
        enabled()
        seedHome()
        val vm = HomeViewModel(app.container)
        compose.setContent {
            KeptTheme(dark = false) {
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxSize().background(Kept.colors.background).padding(top = 24.dp),
                ) { content(vm) }
            }
        }
        settle()
        save(name)
    }

    @Test fun review_needs_check() = review(false, "faded_hardware", Locale("en", "LK"), "review_needs_check")

    private fun home(dark: Boolean, effects: KeptEffects = KeptEffects.Full, name: String? = null) {
        enabled()
        seedHome()
        val vm = HomeViewModel(app.container)
        compose.setContent {
            KeptTheme(dark = dark) {
                CompositionLocalProvider(LocalKeptEffects provides effects) { HomeScreen(vm, onOpenReceipt = {}, savedMessage = null, onMessageShown = {}) }
            }
        }
        settle()
        save(name ?: if (dark) "home_dark" else "home_light")
    }

    private fun review(dark: Boolean, sample: String = "us_electronics", locale: Locale = Locale.US, name: String? = null) {
        enabled()
        val id = seedReview(sample, locale)
        val vm = ReviewViewModel(app.container, id)
        compose.setContent {
            KeptTheme(dark = dark) {
                ReviewScreen(vm, onSaved = {}, onBack = {})
            }
        }
        settle()
        save(name ?: if (dark) "review_dark" else "review_light")
    }
}
