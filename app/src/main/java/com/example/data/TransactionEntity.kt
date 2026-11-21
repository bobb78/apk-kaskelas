package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val INDONESIAN_LOCALE: Locale = Locale.forLanguageTag("id-ID")

enum class TransactionType(val displayName: String) {
    PEMASUKAN("Pemasukan"),
    PENGELUARAN("Pengeluaran")
}

enum class KasCategory(val displayName: String, val iconName: String, val defaultType: TransactionType) {
    // Pemasukan categories
    IURAN_MINGGUAN("Uang Kas", "payments", TransactionType.PEMASUKAN),
    IURAN_BULANAN("Uang Kas Bulanan", "calendar_month", TransactionType.PEMASUKAN),
    DONASI("Donasi / Sukarela", "volunteer_activism", TransactionType.PEMASUKAN),
    DENDA("Denda / Keterlambatan", "gavel", TransactionType.PEMASUKAN),
    SISA_ACARA("Sisa Dana Acara", "savings", TransactionType.PEMASUKAN),
    PEMASUKAN_LAIN("Pemasukan Lainnya", "add_circle", TransactionType.PEMASUKAN),

    // Pengeluaran categories
    ATK_KELAS("Pembelian Alat Tulis", "edit", TransactionType.PENGELUARAN),
    ACARA_KELAS("Acara Kelas", "celebration", TransactionType.PENGELUARAN),
    KEBERSIHAN("Kebersihan Kelas", "cleaning_services", TransactionType.PENGELUARAN),
    FOTOKOPI("Fotokopi & Materi", "print", TransactionType.PENGELUARAN),
    KONSUMSI("Konsumsi & Snack", "restaurant", TransactionType.PENGELUARAN),
    SOSIAL("Dana Sosial / Jenguk", "favorite", TransactionType.PENGELUARAN),
    DEKORASI("Dekorasi & Mading", "brush", TransactionType.PENGELUARAN),
    PENGELUARAN_LAIN("Pengeluaran Lainnya", "remove_circle", TransactionType.PENGELUARAN);

    companion object {
        fun fromString(name: String): KasCategory {
            val trimmed = name.trim()
            return entries.find { it.name.equals(trimmed, ignoreCase = true) }
                ?: entries.find { it.displayName.equals(trimmed, ignoreCase = true) }
                ?: when {
                    trimmed.equals("Iuran Kas Mingguan", ignoreCase = true) -> IURAN_MINGGUAN
                    trimmed.equals("Iuran Kas Bulanan", ignoreCase = true) -> IURAN_BULANAN
                    trimmed.equals("Sisa Dana Kegiatan", ignoreCase = true) -> SISA_ACARA
                    trimmed.equals("Spidol & Alat Tulis", ignoreCase = true) -> ATK_KELAS
                    trimmed.equals("Sapu & Kebersihan", ignoreCase = true) -> KEBERSIHAN
                    trimmed.equals("Jenguk Teman / Sosial", ignoreCase = true) -> SOSIAL
                    else -> PEMASUKAN_LAIN
                }
        }
    }
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // "PEMASUKAN" or "PENGELUARAN"
    val title: String,
    val amount: Long,
    val category: String,
    val dateMillis: Long = System.currentTimeMillis(),
    val personName: String = "", // Nama Siswa / Pengurus / PIC
    val notes: String = ""
) {
    val isIncome: Boolean
        get() = type.equals(TransactionType.PEMASUKAN.name, ignoreCase = true)

    val formattedAmount: String
        get() = formatRupiah(amount)

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", INDONESIAN_LOCALE)
            return sdf.format(Date(dateMillis))
        }

    val dateOnlyFormatted: String
        get() {
            val sdf = SimpleDateFormat("dd MMMM yyyy", INDONESIAN_LOCALE)
            return sdf.format(Date(dateMillis))
        }
}

fun formatRupiah(amount: Long): String {
    val formatter = NumberFormat.getCurrencyInstance(INDONESIAN_LOCALE)
    formatter.maximumFractionDigits = 0
    return formatter.format(amount).replace("Rp", "Rp ")
}

fun formatRupiahCompact(amount: Long): String {
    val sign = if (amount < 0) "-" else ""
    val abs = kotlin.math.abs(amount)
    return when {
        abs >= 1_000_000_000L -> {
            val value = abs / 1_000_000_000.0
            "${sign}Rp ${String.format(INDONESIAN_LOCALE, "%.1f", value)}M"
        }
        abs >= 1_000_000L -> {
            val value = abs / 1_000_000.0
            val formatted = if (abs % 1_000_000L == 0L) {
                "${abs / 1_000_000L}"
            } else {
                String.format(INDONESIAN_LOCALE, "%.1f", value)
            }
            "${sign}Rp ${formatted}jt"
        }
        abs >= 1_000L -> {
            val value = abs / 1_000.0
            val formatted = if (abs % 1_000L == 0L) {
                "${abs / 1_000L}"
            } else {
                String.format(INDONESIAN_LOCALE, "%.1f", value)
            }
            "${sign}Rp ${formatted}rb"
        }
        else -> "${sign}Rp $abs"
    }
}
