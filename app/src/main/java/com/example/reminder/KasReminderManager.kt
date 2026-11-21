package com.example.reminder

import android.content.Context
import com.example.data.TransactionEntity
import com.example.data.formatRupiah
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ReminderSettings(
    val enabled: Boolean = false,
    val dayOfMonth: Int = 1,
    val hour: Int = 8,
    val minute: Int = 0,
    val monthlyTargetAmount: Long = 10_000L,
    val customNote: String = "Mohon segera melunasi iuran kas kelas bulan ini."
)

data class UnpaidSummary(
    val monthLabel: String = "",
    val totalRosterCount: Int = 0,
    val paidMembers: List<String> = emptyList(),
    val unpaidMembers: List<String> = emptyList()
)

object KasReminderManager {

    private const val ROSTER_PREFS = "kas_student_roster"
    private const val KEY_SAVED_STUDENTS = "saved_students"

    private const val REMINDER_PREFS = "kas_reminder_prefs"
    private const val KEY_ENABLED = "reminder_enabled"
    private const val KEY_DAY = "reminder_day"
    private const val KEY_HOUR = "reminder_hour"
    private const val KEY_MINUTE = "reminder_minute"
    private const val KEY_TARGET_AMOUNT = "reminder_target_amount"
    private const val KEY_CUSTOM_NOTE = "reminder_custom_note"

    private val idLocale: Locale = Locale.forLanguageTag("id-ID")

    // --- Student Roster Persistence ---

    fun getStudentRoster(context: Context): List<String> {
        val prefs = context.getSharedPreferences(ROSTER_PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SAVED_STUDENTS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("|||")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(idLocale) }
    }

    fun saveStudentRoster(context: Context, students: List<String>) {
        val cleaned = students
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(idLocale) }
        context.getSharedPreferences(ROSTER_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVED_STUDENTS, cleaned.joinToString("|||"))
            .apply()
    }

    data class CsvParseResult(
        val names: List<String>,
        val detectedColumnName: String? = null,
        val rowCount: Int = 0
    )

    private val LEADING_BULLET_OR_NUMBER_REGEX =
        Regex("""^\s*(?:\[[ xX]?\]\s*)?(?:\d+[.)\-\]]\s*|[-•*▪▫◦✓✔☑☐]+\s*|\d+\s+(?=\p{L}))""")
    private val MULTI_SPACE_REGEX = Regex("""\s+""")
    private val CSV_HEADER_TOKENS = setOf(
        "no", "nomor", "nama", "name", "nama siswa", "nama lengkap", "nama anggota",
        "daftar siswa", "anggota", "siswa", "murid", "peserta", "student", "student name",
        "full name", "kelas", "class", "nis", "nisn", "status", "keterangan", "ket",
        "jenis kelamin", "gender", "l/p", "jk"
    )
    private val NAME_HEADER_KEYWORDS = listOf(
        "nama lengkap", "nama siswa", "nama anggota", "student name", "full name",
        "nama", "name", "siswa", "murid", "anggota", "peserta", "student"
    )
    private val NON_NAME_SINGLE_CODES = setOf("l", "p", "m", "f", "v", "x", "ok", "-")

    fun parseCsvStudentNames(csvContent: String): CsvParseResult {
        val cleanedContent = csvContent.removePrefix("\uFEFF")
        if (cleanedContent.isBlank()) return CsvParseResult(emptyList())

        val nonEmptyLines = cleanedContent.lines().filter { it.isNotBlank() }
        if (nonEmptyLines.isEmpty()) return CsvParseResult(emptyList())

        // Single-line comma/semicolon list like "Andi, Budi, Siti"
        if (nonEmptyLines.size == 1) {
            val simpleNames = parseSimpleTokens(nonEmptyLines.first())
            return CsvParseResult(names = simpleNames, rowCount = 1)
        }

        val delimiter = detectCsvDelimiter(nonEmptyLines)
        val rows = parseCsvRows(cleanedContent, delimiter).filter { row ->
            row.any { it.isNotBlank() }
        }
        if (rows.isEmpty()) return CsvParseResult(emptyList())

        val maxCols = rows.maxOfOrNull { it.size } ?: 1
        if (maxCols <= 1) {
            val singleColNames = rows.flatMap { row -> parseSimpleTokens(row.firstOrNull().orEmpty()) }
                .distinctBy { it.lowercase(idLocale) }
            return CsvParseResult(names = singleColNames, rowCount = rows.size)
        }

        // Multi-column CSV: check if first row is a header
        val firstRow = rows.first()
        val headerNameColIndex = findNameColumnInHeader(firstRow)
        val firstRowLooksLikeHeader = headerNameColIndex != null || firstRow.any { cell ->
            cell.trim().lowercase(idLocale) in CSV_HEADER_TOKENS
        }

        if (headerNameColIndex != null) {
            val detectedHeader = firstRow[headerNameColIndex].trim()
            val names = rows.drop(1)
                .mapNotNull { row ->
                    row.getOrNull(headerNameColIndex)?.let { cleanSingleNameToken(it, stripOuterQuotes = false) }
                }
                .distinctBy { it.lowercase(idLocale) }
            return CsvParseResult(
                names = names,
                detectedColumnName = detectedHeader,
                rowCount = (rows.size - 1).coerceAtLeast(0)
            )
        }

        val dataRows = if (firstRowLooksLikeHeader) rows.drop(1) else rows
        if (dataRows.isEmpty()) return CsvParseResult(emptyList())

        // Infer best name column when no explicit "Nama" header exists
        val bestColIndex = (0 until maxCols).maxByOrNull { colIdx ->
            scoreColumnAsStudentName(dataRows, colIdx)
        } ?: 0

        val bestScore = scoreColumnAsStudentName(dataRows, bestColIndex)
        if (bestScore > 0) {
            val names = dataRows
                .mapNotNull { row ->
                    row.getOrNull(bestColIndex)?.let { cleanSingleNameToken(it, stripOuterQuotes = false) }
                }
                .distinctBy { it.lowercase(idLocale) }
            return CsvParseResult(
                names = names,
                detectedColumnName = if (firstRowLooksLikeHeader) firstRow.getOrNull(bestColIndex)?.trim() else null,
                rowCount = dataRows.size
            )
        }

        // Fallback: extract valid name tokens across rows
        val fallback = dataRows
            .flatMap { row -> row.mapNotNull { cleanSingleNameToken(it, stripOuterQuotes = false) } }
            .distinctBy { it.lowercase(idLocale) }
        return CsvParseResult(names = fallback, rowCount = dataRows.size)
    }

    private fun detectCsvDelimiter(lines: List<String>): Char {
        val sample = lines.take(5)
        val candidates = listOf(';', ',', '\t')
        return candidates.maxByOrNull { delim ->
            sample.sumOf { countUnquotedOccurrences(it, delim) }
        }?.takeIf { delim ->
            sample.sumOf { countUnquotedOccurrences(it, delim) } > 0
        } ?: ','
    }

    private fun countUnquotedOccurrences(line: String, target: Char): Int {
        var inQuotes = false
        var count = 0
        for (ch in line) {
            if (ch == '"') inQuotes = !inQuotes
            else if (ch == target && !inQuotes) count++
        }
        return count
    }

    private fun parseCsvRows(content: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val currentCell = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < content.length) {
            val ch = content[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < content.length && content[i + 1] == '"') {
                        currentCell.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == delimiter && !inQuotes -> {
                    currentRow.add(currentCell.toString().trim())
                    currentCell.clear()
                }
                (ch == '\n' || ch == '\r') && !inQuotes -> {
                    if (ch == '\r' && i + 1 < content.length && content[i + 1] == '\n') {
                        i++
                    }
                    currentRow.add(currentCell.toString().trim())
                    currentCell.clear()
                    rows.add(currentRow.toList())
                    currentRow.clear()
                }
                else -> currentCell.append(ch)
            }
            i++
        }
        if (currentCell.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentCell.toString().trim())
            rows.add(currentRow.toList())
        }
        return rows
    }

    private fun findNameColumnInHeader(headerRow: List<String>): Int? {
        for (keyword in NAME_HEADER_KEYWORDS) {
            val exactIdx = headerRow.indexOfFirst { it.trim().lowercase(idLocale) == keyword }
            if (exactIdx >= 0) return exactIdx
        }
        for (keyword in NAME_HEADER_KEYWORDS) {
            val partialIdx = headerRow.indexOfFirst { it.trim().lowercase(idLocale).contains(keyword) }
            if (partialIdx >= 0) return partialIdx
        }
        return null
    }

    private fun scoreColumnAsStudentName(rows: List<List<String>>, colIdx: Int): Int {
        val values = rows.mapNotNull { it.getOrNull(colIdx)?.trim()?.takeIf { s -> s.isNotEmpty() } }
        if (values.isEmpty()) return -100
        var score = 0
        for (v in values) {
            val lower = v.lowercase(idLocale)
            when {
                v.all { it.isDigit() || it == '.' || it == '-' } -> score -= 3
                lower in NON_NAME_SINGLE_CODES -> score -= 4
                lower in CSV_HEADER_TOKENS -> score -= 2
                v.any { it.isDigit() } && !v.contains(' ') -> score -= 1 // e.g. class codes like "XI-A" or NIS
                v.count { it.isLetter() } >= 3 -> {
                    score += 3
                    if (v.contains(' ')) score += 2 // Full names ("Andi Pratama") score higher
                }
            }
        }
        // Columns with many distinct values are much more likely to be names than status/class columns
        val distinctRatio = values.map { it.lowercase(idLocale) }.distinct().size
        score += distinctRatio * 2
        return score
    }

    private fun cleanSingleNameToken(raw: String, stripOuterQuotes: Boolean = true): String? {
        val baseTrimmed = raw.trim().let { if (stripOuterQuotes) it.trim('"', '\'') else it }
        val cleaned = baseTrimmed
            .replace(LEADING_BULLET_OR_NUMBER_REGEX, "")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
        val lower = cleaned.lowercase(idLocale)
        return if (
            cleaned.isNotBlank() &&
            cleaned.count { it.isLetter() } >= 2 &&
            lower !in CSV_HEADER_TOKENS &&
            lower !in NON_NAME_SINGLE_CODES
        ) {
            cleaned
        } else {
            null
        }
    }

    private fun parseSimpleTokens(rawNames: String): List<String> {
        return rawNames
            .replace("\r\n", "\n")
            .split(",", "\n", ";", "\t")
            .asSequence()
            .mapNotNull { cleanSingleNameToken(it) }
            .distinctBy { it.lowercase(idLocale) }
            .toList()
    }

    fun parseStudentNames(rawNames: String): List<String> {
        if (rawNames.isBlank()) return emptyList()
        val lines = rawNames.lines().filter { it.isNotBlank() }
        // If multi-line and structured like CSV (multiple rows with consistent commas/semicolons/tabs), use CSV parser
        val looksLikeCsvTable = lines.size >= 2 && lines.count {
            it.contains(',') || it.contains(';') || it.contains('\t')
        } >= (lines.size / 2).coerceAtLeast(1)

        return if (looksLikeCsvTable) {
            parseCsvStudentNames(rawNames).names
        } else {
            parseSimpleTokens(rawNames)
        }
    }

    // --- Reminder Settings Persistence ---

    fun getSettings(context: Context): ReminderSettings {
        val prefs = context.getSharedPreferences(REMINDER_PREFS, Context.MODE_PRIVATE)
        return ReminderSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            dayOfMonth = prefs.getInt(KEY_DAY, 1).coerceIn(1, 28),
            hour = prefs.getInt(KEY_HOUR, 8).coerceIn(0, 23),
            minute = prefs.getInt(KEY_MINUTE, 0).coerceIn(0, 59),
            monthlyTargetAmount = prefs.getLong(KEY_TARGET_AMOUNT, 10_000L).coerceAtLeast(1000L),
            customNote = prefs.getString(KEY_CUSTOM_NOTE, null)
                ?.takeIf { it.isNotBlank() }
                ?: "Mohon segera melunasi iuran kas kelas bulan ini."
        )
    }

    fun saveSettings(context: Context, settings: ReminderSettings) {
        context.getSharedPreferences(REMINDER_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putInt(KEY_DAY, settings.dayOfMonth.coerceIn(1, 28))
            .putInt(KEY_HOUR, settings.hour.coerceIn(0, 23))
            .putInt(KEY_MINUTE, settings.minute.coerceIn(0, 59))
            .putLong(KEY_TARGET_AMOUNT, settings.monthlyTargetAmount.coerceAtLeast(1000L))
            .putString(KEY_CUSTOM_NOTE, settings.customNote.trim())
            .apply()
    }

    // --- Unpaid Computation & Formatting ---

    fun computeUnpaidMembersForMonth(
        roster: List<String>,
        transactions: List<TransactionEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): UnpaidSummary {
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val targetYear = nowCal.get(Calendar.YEAR)
        val targetMonth = nowCal.get(Calendar.MONTH)
        val monthLabel = SimpleDateFormat("MMMM yyyy", idLocale).format(Date(nowMillis))

        val txCal = Calendar.getInstance()
        val paidLowerSet = transactions
            .asSequence()
            .filter { tx ->
                if (!tx.isIncome || tx.personName.isBlank()) return@filter false
                txCal.timeInMillis = tx.dateMillis
                txCal.get(Calendar.YEAR) == targetYear && txCal.get(Calendar.MONTH) == targetMonth
            }
            .map { it.personName.trim().lowercase(idLocale) }
            .toSet()

        val cleanedRoster = roster
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(idLocale) }

        val paidMembers = cleanedRoster.filter { it.lowercase(idLocale) in paidLowerSet }
        val unpaidMembers = cleanedRoster.filter { it.lowercase(idLocale) !in paidLowerSet }

        return UnpaidSummary(
            monthLabel = monthLabel,
            totalRosterCount = cleanedRoster.size,
            paidMembers = paidMembers,
            unpaidMembers = unpaidMembers
        )
    }

    fun formatReminderShareMessage(
        unpaidSummary: UnpaidSummary,
        targetAmount: Long,
        customNote: String
    ): String {
        val sb = StringBuilder()
        sb.append("🔔 *TAGIHAN IURAN KAS KELAS*\n")
        sb.append("📅 Periode: ${unpaidSummary.monthLabel}\n")
        sb.append("💰 Nominal Iuran: ${formatRupiah(targetAmount)}\n")
        sb.append("═══════════════════════\n")
        if (unpaidSummary.totalRosterCount == 0) {
            sb.append("Daftar anggota kelas belum ditambahkan.\n")
        } else if (unpaidSummary.unpaidMembers.isEmpty()) {
            sb.append("🎉 Alhamdulillah, seluruh ${unpaidSummary.totalRosterCount} anggota kelas sudah membayar kas bulan ini!\n")
        } else {
            sb.append("📋 *Anggota yang belum bayar (${unpaidSummary.unpaidMembers.size} dari ${unpaidSummary.totalRosterCount}):*\n")
            unpaidSummary.unpaidMembers.forEachIndexed { idx, name ->
                sb.append("${idx + 1}. $name\n")
            }
            sb.append("\n")
        }
        if (customNote.isNotBlank()) {
            sb.append("💬 ${customNote.trim()}\n")
        }
        sb.append("Terima kasih atas kerja samanya! 🙏")
        return sb.toString()
    }
}
