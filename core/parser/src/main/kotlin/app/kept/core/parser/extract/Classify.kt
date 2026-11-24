package app.kept.core.parser.extract

import app.kept.core.parser.internal.Row
import app.kept.core.parser.internal.wordRegex
import app.kept.core.parser.model.Category
import app.kept.core.parser.model.DocType
import app.kept.core.parser.model.Field
import app.kept.core.parser.model.FieldSource

internal object DocTypeClassifier {
    private val I = RegexOption.IGNORE_CASE
    private val ORDER = Regex(
        """order\s+confirm\w*|thank\s+you\s+for\s+your\s+order|your\s+order|order\s+summary|estimated\s+delivery|""" +
            """ship(ping)?\s+to\b|order\s+total""",
        I,
    )
    private val WARRANTY = Regex("""warranty\s+(card|certificate)|guarantee\s+card|certificate\s+of\s+warranty""", I)
    private val INVOICE = Regex("""\btax\s+invoice\b|\binvoice\s*(no\b|number|#|date)|^\s*invoice\s*$""", I)

    fun classify(rows: List<Row>): Field<DocType> {
        val orderHits = rows.count { ORDER.containsMatchIn(it.text) }
        return when {
            rows.any { WARRANTY.containsMatchIn(it.text) } -> Field(DocType.WARRANTY_CARD, 0.85f)
            orderHits >= 2 -> Field(DocType.ORDER_CONFIRMATION, 0.85f)
            orderHits == 1 -> Field(DocType.ORDER_CONFIRMATION, 0.6f)
            rows.any { INVOICE.containsMatchIn(it.text) } -> Field(DocType.INVOICE, 0.75f)
            else -> Field(DocType.RECEIPT, 0.6f)
        }
    }
}

/** A first guess at the category from item and store words. The user can always change it. */
internal object CategorySuggester {
    private val KEYWORDS: Map<Category, Regex> = mapOf(
        Category.ELECTRONICS to listOf(
            "tv", "television", "laptop", "notebook", "phone", "smartphone", "iphone", "tablet", "ipad",
            "headphones", "headphone", "earbuds", "earphones", "speaker", "charger", "camera", "monitor",
            "keyboard", "mouse", "console", "playstation", "xbox", "router", "smartwatch", "hdmi", "usb",
            "ssd", "hard drive", "printer", "power bank", "electronics", "mobile", "mobiles",
            "tempered glass", "screen protector",
        ),
        Category.APPLIANCES to listOf(
            "refrigerator", "fridge", "freezer", "washing machine", "washer", "dryer", "dishwasher",
            "microwave", "oven", "air fryer", "fryer", "blender", "kettle", "toaster", "vacuum",
            "air conditioner", "fan", "iron", "rice cooker", "coffee machine", "mixer", "water heater",
        ),
        Category.FURNITURE to listOf(
            "sofa", "couch", "chair", "table", "desk", "bed", "mattress", "wardrobe", "cabinet", "shelf",
            "bookcase", "dresser", "furniture",
        ),
        Category.CLOTHING to listOf(
            "shirt", "t-shirt", "tee", "jeans", "trousers", "pants", "dress", "skirt", "jacket", "coat",
            "shoe", "shoes", "sneakers", "boots", "socks", "saree", "sari", "kurta", "hoodie", "sweater",
        ),
        Category.TOOLS to listOf(
            "drill", "saw", "hammer", "screwdriver", "wrench", "spanner", "pliers", "sander", "grinder",
            "toolbox", "tool kit", "hardware",
        ),
        Category.GROCERIES to listOf(
            "milk", "bread", "loaf", "eggs", "banana", "bananas", "rice", "sugar", "flour", "butter",
            "cheese", "olive oil", "vegetables", "fruit", "chicken", "yogurt", "grocers", "grocery",
            "supermarket",
        ),
        Category.HOME to listOf(
            "towel", "bedsheet", "pillow", "curtain", "lamp", "bulb", "cookware", "plate", "mug",
            "detergent", "rug", "candle",
        ),
    ).mapValues { (_, words) -> wordRegex(words) }

    fun suggest(text: String): Field<Category>? {
        val scores = KEYWORDS.mapValues { (_, re) -> re.findAll(text).count() }.filterValues { it > 0 }
        val best = scores.maxByOrNull { it.value } ?: return null
        return Field(best.key, (0.5f + 0.1f * best.value).coerceAtMost(0.85f), source = FieldSource.DERIVED)
    }
}
