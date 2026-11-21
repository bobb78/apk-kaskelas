package com.example.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.example.ui.KasSummary
import com.example.ui.KasViewModel.PaidPayerSummary
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object KasPdfExporter {

    private const val PAGE_WIDTH = 595 // A4 width in points
    private const val PAGE_HEIGHT = 842 // A4 height in points
    private const val MARGIN_H = 36f
    private const val MARGIN_BOTTOM = 44f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_H * 2)

    private val idLocale: Locale = Locale.forLanguageTag("id-ID")

    fun defaultPdfFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
        return "Laporan_Kas_Kelas_${sdf.format(Date())}.pdf"
    }

    fun exportToCacheFile(
        context: Context,
        summary: KasSummary,
        transactions: List<TransactionEntity>,
        paidPayers: List<PaidPayerSummary>,
        unpaidMembers: List<String> = emptyList(),
        periodLabel: String = "Semua Transaksi"
    ): File {
        val exportDir = File(context.cacheDir, "exports").apply {
            if (!exists()) mkdirs()
        }
        // Clean up older cached PDFs to keep cache tidy
        exportDir.listFiles()?.forEach { oldFile ->
            if (oldFile.isFile && oldFile.name.endsWith(".pdf")) {
                oldFile.delete()
            }
        }
        val outFile = File(exportDir, defaultPdfFileName())
        FileOutputStream(outFile).use { fos ->
            writePdfToOutputStream(
                outputStream = fos,
                summary = summary,
                transactions = transactions,
                paidPayers = paidPayers,
                unpaidMembers = unpaidMembers,
                periodLabel = periodLabel
            )
        }
        return outFile
    }

    fun printPdfReport(
        context: Context,
        summary: KasSummary,
        transactions: List<TransactionEntity>,
        paidPayers: List<PaidPayerSummary>,
        unpaidMembers: List<String> = emptyList(),
        periodLabel: String = "Semua Transaksi"
    ): Boolean {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: return false

        val jobName = "Laporan_Kas_Kelas_${SimpleDateFormat("ddMMyyyy", Locale.US).format(Date())}"
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(defaultPdfFileName())
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                    return
                }
                val pfd = destination ?: run {
                    callback?.onWriteFailed("Tujuan cetak tidak tersedia")
                    return
                }
                try {
                    FileOutputStream(pfd.fileDescriptor).use { fos ->
                        writePdfToOutputStream(
                            outputStream = fos,
                            summary = summary,
                            transactions = transactions,
                            paidPayers = paidPayers,
                            unpaidMembers = unpaidMembers,
                            periodLabel = periodLabel
                        )
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message ?: "Gagal menulis dokumen PDF")
                }
            }
        }

        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        printManager.print(jobName, adapter, attributes)
        return true
    }

    fun writePdfToOutputStream(
        outputStream: OutputStream,
        summary: KasSummary,
        transactions: List<TransactionEntity>,
        paidPayers: List<PaidPayerSummary>,
        unpaidMembers: List<String> = emptyList(),
        periodLabel: String = "Semua Transaksi"
    ) {
        val pdfDocument = PdfDocument()
        try {
            val printedAt = SimpleDateFormat("dd MMMM yyyy, HH:mm", idLocale).format(Date())

            // Paints
            val navyColor = Color.parseColor("#001D36")
            val brandBlue = Color.parseColor("#0061A4")
            val softBlue = Color.parseColor("#E8F1FC")
            val borderBlue = Color.parseColor("#CBD5E1")
            val greenColor = Color.parseColor("#15803D")
            val redColor = Color.parseColor("#B91C1C")
            val amberColor = Color.parseColor("#B45309")
            val amberBg = Color.parseColor("#FFFBEB")
            val textPrimary = Color.parseColor("#191C1E")
            val textMuted = Color.parseColor("#475569")
            val zebraBg = Color.parseColor("#F8FAFC")

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#DBEAFE")
                textSize = 10.5f
            }
            val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = navyColor
                textSize = 12.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textMuted
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textPrimary
                textSize = 9.6f
            }
            val bodyBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textPrimary
                textSize = 9.6f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val mutedSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textMuted
                textSize = 8.4f
            }
            val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textMuted
                textSize = 7.8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
                color = borderBlue
            }

            var pageNumber = 1
            var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            var canvas = page.canvas
            var y = 32f

            fun drawFooter(c: Canvas, pageNum: Int) {
                val footerY = PAGE_HEIGHT - 22f
                c.drawLine(MARGIN_H, footerY - 12f, PAGE_WIDTH - MARGIN_H, footerY - 12f, strokePaint)
                c.drawText(
                    "Laporan Kas Kelas ($periodLabel) • Dicetak pada $printedAt",
                    MARGIN_H,
                    footerY,
                    mutedSmallPaint
                )
                val pageText = "Halaman $pageNum"
                val pageTextWidth = mutedSmallPaint.measureText(pageText)
                c.drawText(pageText, PAGE_WIDTH - MARGIN_H - pageTextWidth, footerY, mutedSmallPaint)
            }

            fun startNewPage(drawContinuationHeader: Boolean = true) {
                drawFooter(canvas, pageNumber)
                pdfDocument.finishPage(page)
                pageNumber++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = 36f
                if (drawContinuationHeader) {
                    canvas.drawText("LAPORAN KEUANGAN KAS KELAS (Lanjutan)", MARGIN_H, y, sectionTitlePaint)
                    y += 16f
                }
            }

            fun ensureSpace(requiredHeight: Float, onNewPage: () -> Unit = {}) {
                if (y + requiredHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                    startNewPage(drawContinuationHeader = true)
                    onNewPage()
                }
            }

            // 1. Blue Gradient Hero Banner
            val bannerRect = RectF(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + 72f)
            val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    bannerRect.left,
                    bannerRect.top,
                    bannerRect.right,
                    bannerRect.bottom,
                    Color.parseColor("#003366"),
                    brandBlue,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(bannerRect, 14f, 14f, bannerPaint)
            canvas.drawText("LAPORAN KEUANGAN KAS KELAS", MARGIN_H + 18f, y + 29f, titlePaint)
            canvas.drawText("Cakupan: $periodLabel • Dicetak $printedAt", MARGIN_H + 18f, y + 51f, subtitlePaint)
            y += 88f

            // 2. Financial Summary Cards (3 Columns)
            val cardGap = 10f
            val cardW = (CONTENT_WIDTH - cardGap * 2) / 3f
            val cardH = 58f

            fun drawSummaryBox(x: Float, title: String, value: String, valueColor: Int, bgColor: Int) {
                val rect = RectF(x, y, x + cardW, y + cardH)
                fillPaint.color = bgColor
                canvas.drawRoundRect(rect, 10f, 10f, fillPaint)
                canvas.drawRoundRect(rect, 10f, 10f, strokePaint)
                canvas.drawText(title, x + 10f, y + 20f, labelPaint)
                val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = valueColor
                    textSize = 13f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText(truncateText(value, valPaint, cardW - 20f), x + 10f, y + 42f, valPaint)
            }

            drawSummaryBox(
                x = MARGIN_H,
                title = "SALDO BERSIH",
                value = formatRupiah(summary.balance),
                valueColor = brandBlue,
                bgColor = softBlue
            )
            drawSummaryBox(
                x = MARGIN_H + cardW + cardGap,
                title = "TOTAL PEMASUKAN",
                value = "+${formatRupiah(summary.totalIncome)}",
                valueColor = greenColor,
                bgColor = Color.parseColor("#F0FDF4")
            )
            drawSummaryBox(
                x = MARGIN_H + (cardW + cardGap) * 2,
                title = "TOTAL PENGELUARAN",
                value = "-${formatRupiah(summary.totalExpense)}",
                valueColor = redColor,
                bgColor = Color.parseColor("#FEF2F2")
            )
            y += cardH + 20f

            // 3. Daftar Yang Telah Bayar Section
            ensureSpace(50f)
            canvas.drawText("Daftar Yang Telah Bayar (${paidPayers.size} Orang)", MARGIN_H, y, sectionTitlePaint)
            y += 14f

            if (paidPayers.isEmpty()) {
                canvas.drawText("Belum ada nama pembayar yang tercatat pada cakupan ini.", MARGIN_H, y + 4f, mutedSmallPaint)
                y += 22f
            } else {
                val colPayerW = CONTENT_WIDTH / 2f
                val rowH = 18f
                var colIndex = 0
                for ((idx, payer) in paidPayers.withIndex()) {
                    if (colIndex == 0) {
                        ensureSpace(rowH + 6f)
                    }
                    val xPos = MARGIN_H + (colIndex * colPayerW)
                    val freq = if (payer.paymentCount > 1) " (${payer.paymentCount}x)" else ""
                    val text = "${idx + 1}. ${payer.name} — ${formatRupiah(payer.totalPaid)}$freq"
                    canvas.drawText(truncateText(text, bodyPaint, colPayerW - 10f), xPos, y + 12f, bodyPaint)
                    colIndex++
                    if (colIndex == 2) {
                        colIndex = 0
                        y += rowH
                    }
                }
                if (colIndex == 1) {
                    y += rowH
                }
                y += 14f
            }

            // 3b. Anggota Belum Bayar Bulan Ini (jika ada daftar siswa)
            if (unpaidMembers.isNotEmpty()) {
                ensureSpace(50f)
                val unpaidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = amberColor
                    textSize = 11.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText(
                    "Belum Bayar Bulan Ini (${unpaidMembers.size} Anggota)",
                    MARGIN_H,
                    y,
                    unpaidPaint
                )
                y += 8f

                val colW = CONTENT_WIDTH / 3f
                val rowH = 17f
                val boxRows = (unpaidMembers.size + 2) / 3
                val boxH = (boxRows * rowH) + 12f
                ensureSpace(boxH + 10f)
                val unpaidBox = RectF(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + boxH)
                fillPaint.color = amberBg
                canvas.drawRoundRect(unpaidBox, 8f, 8f, fillPaint)
                canvas.drawRoundRect(unpaidBox, 8f, 8f, strokePaint)

                var curY = y + 18f
                var colIdx = 0
                unpaidMembers.forEachIndexed { idx, name ->
                    val xPos = MARGIN_H + 10f + (colIdx * colW)
                    canvas.drawText(
                        truncateText("${idx + 1}. $name", bodyPaint, colW - 14f),
                        xPos,
                        curY,
                        bodyPaint
                    )
                    colIdx++
                    if (colIdx == 3) {
                        colIdx = 0
                        curY += rowH
                    }
                }
                y += boxH + 16f
            }

            // 4. Full Transaction History Table
            val colNoX = MARGIN_H + 6f
            val colDateX = MARGIN_H + 34f
            val colTitleX = MARGIN_H + 126f
            val colCatX = MARGIN_H + 318f
            val colAmtRightX = PAGE_WIDTH - MARGIN_H - 8f

            fun drawTableHeader() {
                val headerRect = RectF(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + 24f)
                fillPaint.color = navyColor
                canvas.drawRoundRect(headerRect, 6f, 6f, fillPaint)
                val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 9.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val textY = y + 15.5f
                canvas.drawText("No", colNoX, textY, headerTextPaint)
                canvas.drawText("Tanggal", colDateX, textY, headerTextPaint)
                canvas.drawText("Keterangan & Pembayar", colTitleX, textY, headerTextPaint)
                canvas.drawText("Kategori", colCatX, textY, headerTextPaint)
                val amtHdr = "Nominal"
                canvas.drawText(amtHdr, colAmtRightX - headerTextPaint.measureText(amtHdr), textY, headerTextPaint)
                y += 26f
            }

            ensureSpace(64f)
            canvas.drawText("Riwayat Transaksi (${transactions.size} Catatan)", MARGIN_H, y, sectionTitlePaint)
            y += 10f
            drawTableHeader()

            if (transactions.isEmpty()) {
                fillPaint.color = zebraBg
                val emptyRect = RectF(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + 34f)
                canvas.drawRoundRect(emptyRect, 6f, 6f, fillPaint)
                canvas.drawText("Belum ada riwayat transaksi kas yang tercatat.", MARGIN_H + 12f, y + 21f, mutedSmallPaint)
                y += 38f
            } else {
                val dateFmt = SimpleDateFormat("dd/MM/yy HH:mm", idLocale)
                transactions.forEachIndexed { index, tx ->
                    val hasNotes = tx.notes.isNotBlank()
                    val rowHeight = if (hasNotes) 35f else 26f
                    ensureSpace(rowHeight + 4f) {
                        drawTableHeader()
                    }
                    if (index % 2 == 1) {
                        fillPaint.color = zebraBg
                        canvas.drawRect(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + rowHeight, fillPaint)
                    }

                    val baseLineY = y + 16f
                    canvas.drawText("${index + 1}", colNoX, baseLineY, bodyPaint)
                    canvas.drawText(dateFmt.format(Date(tx.dateMillis)), colDateX, baseLineY, mutedSmallPaint)

                    val titleWithPerson = if (tx.personName.isNotBlank()) {
                        "${tx.title} (${tx.personName})"
                    } else {
                        tx.title
                    }
                    canvas.drawText(
                        truncateText(titleWithPerson, bodyBoldPaint, colCatX - colTitleX - 10f),
                        colTitleX,
                        baseLineY,
                        bodyBoldPaint
                    )
                    if (hasNotes) {
                        canvas.drawText(
                            truncateText("Catatan: ${tx.notes}", notePaint, colCatX - colTitleX - 10f),
                            colTitleX,
                            baseLineY + 12f,
                            notePaint
                        )
                    }

                    val catDisplay = KasCategory.fromString(tx.category).displayName
                    canvas.drawText(
                        truncateText(catDisplay, mutedSmallPaint, (colAmtRightX - 95f) - colCatX),
                        colCatX,
                        baseLineY,
                        mutedSmallPaint
                    )

                    val amountStr = (if (tx.isIncome) "+ " else "- ") + tx.formattedAmount
                    val amtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (tx.isIncome) greenColor else redColor
                        textSize = 9.6f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    canvas.drawText(amountStr, colAmtRightX - amtPaint.measureText(amountStr), baseLineY, amtPaint)

                    canvas.drawLine(MARGIN_H, y + rowHeight, PAGE_WIDTH - MARGIN_H, y + rowHeight, strokePaint)
                    y += rowHeight
                }
            }

            // 5. Signature / Footer Total Strip
            ensureSpace(46f)
            y += 14f
            val summaryStrip = RectF(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + 28f)
            fillPaint.color = softBlue
            canvas.drawRoundRect(summaryStrip, 8f, 8f, fillPaint)
            canvas.drawRoundRect(summaryStrip, 8f, 8f, strokePaint)
            val totalLine = "Total: ${transactions.size} transaksi  |  Masuk: +${formatRupiah(summary.totalIncome)}  |  Keluar: -${formatRupiah(summary.totalExpense)}  |  Saldo: ${formatRupiah(summary.balance)}"
            canvas.drawText(
                truncateText(totalLine, bodyBoldPaint, CONTENT_WIDTH - 20f),
                MARGIN_H + 10f,
                y + 17.5f,
                bodyBoldPaint
            )

            drawFooter(canvas, pageNumber)
            pdfDocument.finishPage(page)
            pdfDocument.writeTo(outputStream)
        } finally {
            pdfDocument.close()
        }
    }

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "..."
        val ellipsisWidth = paint.measureText(ellipsis)
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + ellipsisWidth > maxWidth) {
            end--
        }
        return if (end <= 0) ellipsis else text.substring(0, end) + ellipsis
    }
}
