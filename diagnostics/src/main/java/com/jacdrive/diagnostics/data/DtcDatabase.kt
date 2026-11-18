package com.jacdrive.diagnostics.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class LocalizedText(val en: String, val ar: String) {
    fun get(arabic: Boolean) = if (arabic) ar else en
}

enum class DtcSeverity { LOW, MEDIUM, HIGH, CRITICAL, UNKNOWN }

enum class DtcSystem { FUEL_AIR, IGNITION, EMISSIONS, COOLING, SPEED_IDLE, ECU, TRANSMISSION, NETWORK, ELECTRICAL, ENGINE, CHASSIS, BODY, UNKNOWN }

data class DtcInfo(
    val code: String,
    /** False when the code is not in the database and only its structure was interpreted. */
    val known: Boolean,
    val manufacturerSpecific: Boolean,
    val system: DtcSystem,
    val severity: DtcSeverity,
    val title: LocalizedText,
    val causes: List<LocalizedText>,
    val steps: List<LocalizedText>,
)

/**
 * Offline bilingual fault-code reference loaded from `assets/dtc_database.json`.
 * To add codes, edit `tools/gen_dtc_database.py` (or the JSON directly) — no code changes needed.
 */
class DtcDatabase(json: String) {

    private val causes: Map<String, LocalizedText>
    private val steps: Map<String, LocalizedText>
    private val codes: Map<String, DtcInfo>

    init {
        val root = JSONObject(json)
        causes = root.getJSONObject("causes").textMap()
        steps = root.getJSONObject("steps").textMap()
        val arr = root.getJSONArray("codes")
        codes = (0 until arr.length()).associate { i ->
            val o = arr.getJSONObject(i)
            val code = o.getString("code").uppercase()
            code to DtcInfo(
                code = code,
                known = true,
                manufacturerSpecific = DtcStructure.isManufacturerSpecific(code),
                system = runCatching { DtcSystem.valueOf(o.getString("system")) }.getOrDefault(DtcSystem.UNKNOWN),
                severity = runCatching { DtcSeverity.valueOf(o.getString("severity")) }.getOrDefault(DtcSeverity.UNKNOWN),
                title = LocalizedText(o.getString("en"), o.getString("ar")),
                causes = o.getJSONArray("causes").let { a -> (0 until a.length()).mapNotNull { causes[a.getString(it)] } },
                steps = o.getJSONArray("steps").let { a -> (0 until a.length()).mapNotNull { steps[a.getString(it)] } },
            )
        }
    }

    val size: Int get() = codes.size
    val allCodes: Collection<DtcInfo> get() = codes.values

    fun lookup(code: String): DtcInfo {
        val c = code.trim().uppercase()
        return codes[c] ?: DtcStructure.describe(c)
    }

    private fun JSONObject.textMap(): Map<String, LocalizedText> = keys().asSequence().associateWith {
        val t = getJSONObject(it)
        LocalizedText(t.getString("en"), t.getString("ar"))
    }
}

/** Interprets a code from its structure (SAE J2012) when it is not in the database. */
object DtcStructure {
    fun isManufacturerSpecific(code: String): Boolean {
        val d1 = code.getOrNull(1) ?: return false
        return when (code.firstOrNull()) {
            'P' -> d1 == '1' || d1 == '3'
            'C', 'B', 'U' -> d1 == '1' || d1 == '2'
            else -> false
        }
    }

    fun describe(code: String): DtcInfo {
        val manufacturer = isManufacturerSpecific(code)
        val (system, area) = when (code.firstOrNull()) {
            'P' -> when (code.getOrNull(2)) {
                '0', '1', '2' -> DtcSystem.FUEL_AIR to LocalizedText("Fuel and air metering", "قياس الوقود والهواء")
                '3' -> DtcSystem.IGNITION to LocalizedText("Ignition system or misfire", "نظام الإشعال أو خلل الاحتراق")
                '4' -> DtcSystem.EMISSIONS to LocalizedText("Auxiliary emission controls", "أنظمة التحكم بالانبعاثات")
                '5' -> DtcSystem.SPEED_IDLE to LocalizedText("Vehicle speed, idle control and auxiliary inputs", "سرعة السيارة والتحكم بالسرعة البطيئة")
                '6' -> DtcSystem.ECU to LocalizedText("Control module and output circuits", "وحدة التحكم ودوائر الخرج")
                '7', '8', '9' -> DtcSystem.TRANSMISSION to LocalizedText("Transmission", "ناقل الحركة")
                'A', 'B', 'C' -> DtcSystem.ELECTRICAL to LocalizedText("Hybrid / electric propulsion", "نظام الدفع الهجين أو الكهربائي")
                else -> DtcSystem.UNKNOWN to LocalizedText("Powertrain", "مجموعة نقل الحركة")
            }
            'C' -> DtcSystem.CHASSIS to LocalizedText("Chassis (brakes, steering, suspension)", "الشاسيه (الفرامل، التوجيه، التعليق)")
            'B' -> DtcSystem.BODY to LocalizedText("Body (airbags, comfort, lighting)", "الهيكل (الوسائد الهوائية، الراحة، الإضاءة)")
            'U' -> DtcSystem.NETWORK to LocalizedText("Vehicle network communication", "شبكة الاتصال في السيارة")
            else -> DtcSystem.UNKNOWN to LocalizedText("Unknown system", "نظام غير معروف")
        }
        val title = if (manufacturer) {
            LocalizedText("Manufacturer-specific code · ${area.en}", "رمز خاص بالشركة المصنعة · ${area.ar}")
        } else {
            LocalizedText("Generic code · ${area.en}", "رمز عام · ${area.ar}")
        }
        return DtcInfo(
            code = code, known = false, manufacturerSpecific = manufacturer, system = system,
            severity = DtcSeverity.UNKNOWN, title = title, causes = emptyList(),
            steps = listOf(
                LocalizedText(
                    "This code is not in the offline database. Look up the exact definition for JAC vehicles or ask a JAC service centre.",
                    "هذا الرمز غير موجود في قاعدة البيانات. ابحث عن تعريفه الدقيق لسيارات JAC أو اسأل مركز خدمة JAC.",
                ),
            ),
        )
    }
}

@Singleton
class DtcDatabaseProvider @Inject constructor(@ApplicationContext private val context: Context) {
    val database: DtcDatabase by lazy {
        DtcDatabase(context.assets.open("dtc_database.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
    }
}
