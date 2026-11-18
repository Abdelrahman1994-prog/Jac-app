package com.jacdrive.diagnostics.data

import com.jacdrive.core.database.VehicleProfileEntity
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a self-contained, printable HTML diagnostic report (no external resources).
 * Arabic reports are laid out right-to-left. Text is HTML-escaped.
 */
object DiagnosticReport {

    fun html(
        session: DiagnosticSession,
        previous: DiagnosticSession?,
        vehicle: VehicleProfileEntity?,
        lookup: (String) -> DtcInfo,
        arabic: Boolean,
        appVersion: String,
    ): String {
        val l = if (arabic) Labels.AR else Labels.EN
        val locale = if (arabic) Locale("ar") else Locale.ENGLISH
        val fmt = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT, locale)
        fun e(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val sb = StringBuilder()
        sb.append("<!doctype html><html lang=\"${if (arabic) "ar" else "en"}\" dir=\"${if (arabic) "rtl" else "ltr"}\"><head><meta charset=\"utf-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>${e(l.title)}</title><style>")
        sb.append("body{font-family:system-ui,-apple-system,'Segoe UI',Roboto,'Noto Sans Arabic',sans-serif;margin:24px;color:#111;max-width:860px}")
        sb.append("h1{font-size:22px;margin:0}h2{font-size:16px;margin:24px 0 8px;border-bottom:1px solid #ddd;padding-bottom:4px}")
        sb.append(".muted{color:#666;font-size:13px}table{border-collapse:collapse;width:100%}td,th{padding:6px 8px;border-bottom:1px solid #eee;text-align:start;vertical-align:top;font-size:14px}")
        sb.append(".code{font-family:ui-monospace,monospace;font-weight:700;direction:ltr;unicode-bidi:isolate}.sev{font-weight:600}.CRITICAL{color:#b00020}.HIGH{color:#c2410c}.MEDIUM{color:#a16207}.LOW{color:#1d4ed8}")
        sb.append("ul{margin:4px 0;padding-inline-start:18px}.note{background:#f6f6f6;padding:10px;border-radius:8px;font-size:13px}</style></head><body>")
        sb.append("<h1>JAC DRIVE · ${e(l.title)}</h1><div class=\"muted\">${e(fmt.format(Date(session.timestampMs)))}</div>")

        sb.append("<h2>${e(l.vehicle)}</h2><table>")
        fun row(k: String, v: String?) { sb.append("<tr><th>${e(k)}</th><td>${e(v ?: "—")}</td></tr>") }
        row(l.model, vehicle?.let { "${it.make} ${it.model} ${it.modelYear} ${it.trim}" })
        row("VIN", vehicle?.vin)
        row(l.odometer, session.mileageKm?.let { "$it km" })
        row(l.protocol, session.protocol)
        row(l.adapter, session.adapterName)
        sb.append("</table>")

        sb.append("<h2>${e(l.status)}</h2><table>")
        row(l.mil, when (session.milOn) { true -> l.on; false -> l.off; null -> null })
        row(l.reported, session.reportedDtcCount?.toString())
        if (session.failedReads.isNotEmpty()) row(l.incomplete, session.failedReads.joinToString { it.name })
        sb.append("</table>")

        fun codes(title: String, list: List<String>?) {
            sb.append("<h2>${e(title)}</h2>")
            when {
                list == null -> sb.append("<p class=\"muted\">${e(l.notSupported)}</p>")
                list.isEmpty() -> sb.append("<p class=\"muted\">${e(l.none)}</p>")
                else -> {
                    sb.append("<table><tr><th>${e(l.code)}</th><th>${e(l.description)}</th><th>${e(l.severity)}</th></tr>")
                    for (c in list) {
                        val i = lookup(c)
                        sb.append("<tr><td class=\"code\">${e(c)}</td><td>${e(i.title.get(arabic))}")
                        if (i.causes.isNotEmpty()) sb.append("<div class=\"muted\">${e(l.causes)}:</div><ul>${i.causes.joinToString("") { "<li>${e(it.get(arabic))}</li>" }}</ul>")
                        if (i.steps.isNotEmpty()) sb.append("<div class=\"muted\">${e(l.steps)}:</div><ul>${i.steps.joinToString("") { "<li>${e(it.get(arabic))}</li>" }}</ul>")
                        sb.append("</td><td class=\"sev ${i.severity.name}\">${e(l.severityName(i.severity))}</td></tr>")
                    }
                    sb.append("</table>")
                }
            }
        }
        codes(l.stored, session.stored)
        codes(l.pending, session.pending)
        codes(l.permanent, session.permanent)

        session.readiness?.takeIf { it.isNotEmpty() }?.let { r ->
            sb.append("<h2>${e(l.readiness)}</h2><table>")
            r.forEach { (m, st) -> row(m.name.lowercase().replace('_', ' '), if (st == com.jacdrive.obd.dtc.MonitorState.COMPLETE) l.complete else l.incompleteMon) }
            sb.append("</table>")
        }
        previous?.let { p ->
            val cmp = SessionMapper.compare(session, p)
            sb.append("<h2>${e(l.compare)} ${e(fmt.format(Date(p.timestampMs)))}</h2><table>")
            row(l.new, cmp.newCodes.joinToString().ifEmpty { "—" })
            row(l.resolved, cmp.resolved.joinToString().ifEmpty { "—" })
            row(l.persisting, cmp.persisting.joinToString().ifEmpty { "—" })
            sb.append("</table>")
        }
        sb.append("<h2>${e(l.notes)}</h2><p class=\"note\">${e(l.disclaimer)}</p>")
        sb.append("<p class=\"muted\">JAC DRIVE $appVersion</p></body></html>")
        return sb.toString()
    }

    private data class Labels(
        val title: String, val vehicle: String, val model: String, val odometer: String, val protocol: String, val adapter: String,
        val status: String, val mil: String, val on: String, val off: String, val reported: String, val incomplete: String,
        val stored: String, val pending: String, val permanent: String, val notSupported: String, val none: String,
        val code: String, val description: String, val severity: String, val causes: String, val steps: String,
        val readiness: String, val complete: String, val incompleteMon: String, val compare: String, val new: String,
        val resolved: String, val persisting: String, val notes: String, val disclaimer: String, val sev: List<String>,
    ) {
        fun severityName(s: DtcSeverity) = sev[s.ordinal]

        companion object {
            val EN = Labels(
                "Diagnostic report", "Vehicle", "Model", "Odometer", "Protocol", "Adapter", "Status", "Check-engine lamp (MIL)", "ON", "Off",
                "Confirmed codes reported by ECU", "Lists not read (communication error)", "Stored codes (Mode 03)", "Pending codes (Mode 07)",
                "Permanent codes (Mode 0A)", "Not supported by the ECU.", "No codes.", "Code", "Description", "Severity", "Possible causes",
                "Recommended checks", "Emissions readiness", "Complete", "Incomplete", "Compared with scan of", "New", "No longer present",
                "Still present", "Notes",
                "Generated from standard OBD-II (SAE J1979) data read by JAC DRIVE. Descriptions follow generic SAE J2012 definitions; " +
                    "manufacturer-specific conditions may apply. ABS, airbag and body modules are not covered. This report does not replace a professional inspection.",
                listOf("Low", "Medium", "High", "Critical", "Unknown"),
            )
            val AR = Labels(
                "تقرير الفحص", "السيارة", "الطراز", "عداد المسافة", "البروتوكول", "المحوّل", "الحالة", "لمبة فحص المحرك (MIL)", "مضاءة", "مطفأة",
                "عدد الأعطال المؤكدة حسب وحدة التحكم", "قوائم لم تُقرأ (خطأ اتصال)", "الأعطال المخزنة (Mode 03)", "الأعطال المعلّقة (Mode 07)",
                "الأعطال الدائمة (Mode 0A)", "غير مدعوم من وحدة التحكم.", "لا توجد أعطال.", "الرمز", "الوصف", "الخطورة", "الأسباب المحتملة",
                "الفحوصات المقترحة", "جاهزية الانبعاثات", "مكتمل", "غير مكتمل", "مقارنة بفحص", "جديد", "لم يعد موجوداً", "ما زال موجوداً", "ملاحظات",
                "تم إنشاء التقرير من بيانات OBD-II القياسية (SAE J1979) التي قرأها JAC DRIVE. الأوصاف مبنية على تعريفات SAE J2012 العامة وقد تنطبق شروط خاصة بالشركة المصنعة. " +
                    "لا يشمل التقرير أنظمة ABS والوسائد الهوائية ووحدات الهيكل، ولا يغني عن الفحص لدى مختص.",
                listOf("منخفض", "متوسط", "مرتفع", "حرج", "غير معروف"),
            )
        }
    }
}
