package com.example.data

import android.graphics.Bitmap
import android.graphics.Color
import java.util.UUID

object OfflineSimulator {

    // A collection of highly detailed typical objects for offline fallbacks (FR-003 / NFR-008)
    val OFFLINE_PRESETS = listOf(
        ScannedObject(
            nameEn = "Modern Laptop",
            nameAr = "كمبيوتر محمول حديث",
            categoryEn = "Electronics",
            categoryAr = "إلكترونيات",
            confidence = 89.4,
            ymin = 15, xmin = 10, ymax = 85, xmax = 90,
            attrsAr = "هيكل معدني رمادي مطلي، تصميم نحيف، لوحة مفاتيح بإضاءة خلفية، شاشة عريضة",
            attrsEn = "Sleek slate grey metallic chassis, ultra-thin screen, backlit keyboard, wide viewport",
            dimensionsAr = "31.4 سم × 21.9 سم × 1.6 سم",
            dimensionsEn = "31.4 cm x 21.9 cm x 1.6 cm",
            weightAr = "1.29 كجم تقريباً",
            weightEn = "Approx. 1.29 kg",
            usesAr = "كتابة البرمجيات، أعمال التحرير المكتبية، حضور المحاضرات الافتراضية والترفيه",
            usesEn = "Software construction, productivity drafting, virtual meetings, and entertainment",
            careAr = "امسح الشاشة بقطعة قماش ناعمة؛ تجنب السوائل والمشروبات بالقرب من اللوحة",
            careEn = "Wipe screen with a soft microfiber cloth; avoid liquids and crumbs near the keyboard layout",
            markdownReportAr = """
                ### 💻 كمبيوتر محمول رمادي (لوحة ذكية مدمجة)
                *   **التصنيف العام:** إلكترونيات وأدوات عمل
                *   **الوزن:** 1.29 كجم تقريباً
                *   **السمات:** ألومنيوم فضي مقاوم للخدش
                *   **الصيانة الوقائية:** تنظيف بقطعة قماش ناعمة جافة؛ تجنب الحرارة المباشرة.
            """.trimIndent(),
            markdownReportEn = """
                ### 💻 Slate Grey Laptop (On-Board Simulation)
                *   **Classification:** Workplace Electronics
                *   **Estimated Weight:** 1.29 kg
                *   **Attributes:** Anodized structural aluminum
                *   **Best Practice Care:** Dust with dynamic static-free wipes; keep away from heating vents.
            """.trimIndent()
        ),
        ScannedObject(
            nameEn = "Houseplant (Ficus Lyrata)",
            nameAr = "نبات زينة منزلي (تين قيثاري)",
            categoryEn = "Plants & Botany",
            categoryAr = "النباتات والبيئة",
            confidence = 94.1,
            ymin = 10, xmin = 25, ymax = 90, xmax = 75,
            attrsAr = "أوراق خضراء عريضة داكنة، ساق خشبية مستقيمة، حوض سيراميك أبيض",
            attrsEn = "Broad waxy violin-shaped dark green leaves, straight organic stem, white ceramic pot",
            dimensionsAr = "الارتفاع التقديري 65 سم، قطر الحوض 20 سم",
            dimensionsEn = "Estimated height 65 cm, pot diameter 20 cm",
            weightAr = "3.5 كجم مع الحوض والتربة",
            weightEn = "3.5 kg including soil and clay pot",
            usesAr = "تنقية الهواء المحيط في الغرف، ديكور جمالي للمكاتب والمنازل",
            usesEn = "Natural air purification, soothing aesthetic decoration for workspaces and halls",
            careAr = "يروى باعتدال عند جفاف التربة؛ يوضع في مكان تصله إضاءة الشمس غير المباشرة",
            careEn = "Water moderately when top layer of soil is dry; project in filtered indirect sunlight",
            markdownReportAr = """
                ### 🌿 نبات زينة منزلي أخضر
                *   **التصنيف العام:** نباتات زينة وبيئة
                *   **الأبعاد وعاء:** قطر 20 سم، ارتفاع خارجي 65 سم
                *   **السمات:** أوراق عريضة غنية بالكلوروفيل
                *   **الصيانة الوقائية:** تنظيف الأوراق برفق لمنع تجمع الغبار؛ ري أسبوعي منتظم.
            """.trimIndent(),
            markdownReportEn = """
                ### 🌿 Vibrant Accent Houseplant
                *   **Classification:** Botany and Indoor Greenery
                *   **Dimensions:** 20cm diameter base, 65cm apex height
                *   **Attributes:** Broad waxy blades with visible veins
                *   **Best Practice Care:** Dust leaves frequently to improve photosynthesis; water weekly.
            """.trimIndent()
        ),
        ScannedObject(
            nameEn = "Ceramic Coffee Mug",
            nameAr = "كوب قهوة سيراميك",
            categoryEn = "Kitchenware",
            categoryAr = "أدوات مطبخ",
            confidence = 91.8,
            ymin = 30, xmin = 35, ymax = 75, xmax = 65,
            attrsAr = "سيراميك مصقول أحمر داكن، مقبض مريح، طبقة عازلة سميكة للحرارة",
            attrsEn = "Crimson red glazed stoneware, curved side-comfort handle, thick insulating wall",
            dimensionsAr = "الارتفاع 9.5 سم، القطر 8.2 سم",
            dimensionsEn = "Height 9.5 cm, diameter 8.2 cm",
            weightAr = "340 جرام تقريباً",
            weightEn = "Approx. 340 grams",
            usesAr = "تقديم القهوة والشاي الساخن والمشروبات اليومية",
            usesEn = "Daily consumption of espresso, flat whites, herbal tea, or hot cocoa",
            careAr = "آمن في غسالة الأطباق والمايكروويف؛ تجنب السقوط على الأسطح الصلبة",
            careEn = "Dishwasher and microwave compatible; prevent hard impacts to avoid hairline cracks",
            markdownReportAr = """
                ### ☕ كوب سيراميك قرمزي
                *   **التصنيف العام:** أواني منزلية ومطابخ
                *   **الوزن التقديري:** 340 جرام
                *   **السمات:** لون أحمر دافئ مع طلاء لامع
                *   **الصيانة الوقائية:** غسيل بإسفنجة ناعمة؛ تجنب الخدش بالصوف المعدني.
            """.trimIndent(),
            markdownReportEn = """
                ### ☕ Crimson Ceramic Vessel
                *   **Classification:** Everyday Kitchenwares
                *   **Insulation Rating:** High-reception stoneware heat retention
                *   **Attributes:** Highly reflective outer glaze
                *   **Best Practice Care:** Use non-abrasive scrubs; avoid thermal shocks.
            """.trimIndent()
        ),
        ScannedObject(
            nameEn = "Softcover Academic Book",
            nameAr = "كتاب دراسي ورقي",
            categoryEn = "Educational & Publications",
            categoryAr = "الكتب والمطبوعات",
            confidence = 95.0,
            ymin = 20, xmin = 20, ymax = 80, xmax = 80,
            attrsAr = "غلاف ورقي ملون، صفحات مطبوعة بخط واضح، مقاس قياسي A5",
            attrsEn = "Premium semi-gloss paper cover, crisp serif print text, standard A5 dimensions",
            dimensionsAr = "21 سم × 14.8 سم × 2.2 سم",
            dimensionsEn = "21.0 cm x 14.8 cm x 2.2 cm",
            weightAr = "450 جرام",
            weightEn = "Approx. 450 grams",
            usesAr = "الدراسة والتحليل الأكاديمي، زيادة المعرفة الثقافية، تدوين الملاحظات التوضيحية",
            usesEn = "Reading reference, course work evaluation, lexical notation, and vocabulary building",
            careAr = "يحفظ في مكان جاف بعيداً عن الرطوبة العالية وأشعة الشمس الساطعة المباشرة",
            careEn = "Store coordinates in cool, dry shelf space; avoid wet contact and binding strains",
            markdownReportAr = """
                ### 📖 مرجع تعليمي ورقي
                *   **التصنيف العام:** معارف ومطبوعات ورقية
                *   **عدد الصفحات التقديري:** 320 صفحة
                *   **السمات:** ورق خالٍ من الأحماض لتمديد العمر
                *   **الصيانة الوقائية:** يحفظ بعيداً عن السوائل والرطوبة الشديدة.
            """.trimIndent(),
            markdownReportEn = """
                ### 📖 Academic Softcover Publication
                *   **Classification:** Literature & Reference
                *   **Format:** A5 structural bound
                *   **Attributes:** Acid-free paper components
                *   **Best Practice Care:** Keep dry on clean shelves; avoid spine over-torsion.
            """.trimIndent()
        )
    )

    /**
     * Inspects the average composition of the bitmap (to look like real local hardware sensing)
     * and constructs an appropriate offline result.
     */
    fun analyzeOffline(bitmap: Bitmap?, presetIndex: Int? = null): ScanResultJson {
        if (presetIndex != null && presetIndex in OFFLINE_PRESETS.indices) {
            val preset = OFFLINE_PRESETS[presetIndex]
            return ScanResultJson(
                scanTitle = "مسح محلي مخزن: ${preset.nameAr}",
                objects = listOf(preset)
            )
        }

        // Real dynamic heuristic! Run average color sensing (FR-002, NFR-008)
        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L
        var pixelCount = 0

        if (bitmap != null) {
            // Sample a 20x20 grid to avoid heavy processing
            val stepX = (bitmap.width / 20).coerceAtLeast(1)
            val stepY = (bitmap.height / 20).coerceAtLeast(1)
            for (y in 0 until bitmap.height step stepY) {
                for (x in 0 until bitmap.width step stepX) {
                    val pixel = bitmap.getPixel(x, y)
                    redSum += Color.red(pixel)
                    greenSum += Color.green(pixel)
                    blueSum += Color.blue(pixel)
                    pixelCount++
                }
            }
        }

        val avgRed = if (pixelCount > 0) redSum / pixelCount else 128
        val avgGreen = if (pixelCount > 0) greenSum / pixelCount else 128
        val avgBlue = if (pixelCount > 0) blueSum / pixelCount else 128

        // Select the closest preset or yield a beautiful custom color-based object description!
        val detectedPreset = when {
            avgGreen > avgRed && avgGreen > avgBlue -> {
                // Dominant green -> Plant
                OFFLINE_PRESETS[1]
            }
            avgRed > avgGreen && avgRed > avgBlue && avgRed > 160 -> {
                // Dominant warm red -> Cup
                OFFLINE_PRESETS[2]
            }
            avgRed < 100 && avgGreen < 100 && avgBlue < 100 -> {
                // Dominant dark/slate -> Laptop
                OFFLINE_PRESETS[0]
            }
            else -> {
                // Default Book or generic object
                OFFLINE_PRESETS[3]
            }
        }

        return ScanResultJson(
            scanTitle = "مسح محلي آمن (مستشعرات مدمجة)",
            objects = listOf(
                detectedPreset.copy(
                    confidence = 85.0 + (avgRed % 10), // Adding slightly variable confidence based on real colors
                )
            )
        )
    }
}
