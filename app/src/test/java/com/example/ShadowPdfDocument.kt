package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import java.io.OutputStream
import java.nio.charset.StandardCharsets

@Implements(PdfDocument::class)
open class ShadowPdfDocument {
    private val finishedPages = mutableListOf<PdfDocument.PageInfo>()
    private var currentPage: PdfDocument.Page? = null
    private var closed = false

    @Implementation
    protected open fun __constructor__() {
        closed = false
    }

    @Implementation
    protected open fun startPage(pageInfo: PdfDocument.PageInfo?): PdfDocument.Page {
        check(!closed) { "document is closed!" }
        check(currentPage == null) { "previous page not finished!" }
        requireNotNull(pageInfo) { "pageInfo cannot be null" }

        val bitmap = Bitmap.createBitmap(
            pageInfo.pageWidth.coerceAtLeast(1),
            pageInfo.pageHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val page = Shadow.newInstanceOf(PdfDocument.Page::class.java)
        val shadowPage = Shadow.extract<ShadowPdfDocumentPage>(page)
        shadowPage.init(canvas, pageInfo)
        currentPage = page
        return page
    }

    @Implementation
    protected open fun finishPage(page: PdfDocument.Page?) {
        check(!closed) { "document is closed!" }
        check(page != null && page === currentPage) { "invalid page" }
        finishedPages.add(page.info)
        currentPage = null
    }

    @Implementation
    protected open fun writeTo(out: OutputStream?) {
        check(!closed) { "document is closed!" }
        check(currentPage == null) { "current page not finished!" }
        requireNotNull(out) { "output stream cannot be null" }

        val pdfHeader = buildString {
            append("%PDF-1.4\n")
            append("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n")
            append("2 0 obj << /Type /Pages /Count ${finishedPages.size} >> endobj\n")
            append("%%EOF\n")
        }
        out.write(pdfHeader.toByteArray(StandardCharsets.US_ASCII))
        out.flush()
    }

    @Implementation
    protected open fun getPages(): MutableList<PdfDocument.PageInfo> {
        return finishedPages.toMutableList()
    }

    @Implementation
    protected open fun close() {
        closed = true
    }
}

@Implements(PdfDocument.Page::class)
open class ShadowPdfDocumentPage {
    private var pageCanvas: Canvas? = null
    private var pageInfo: PdfDocument.PageInfo? = null

    fun init(canvas: Canvas, info: PdfDocument.PageInfo) {
        this.pageCanvas = canvas
        this.pageInfo = info
    }

    @Implementation
    protected open fun getCanvas(): Canvas {
        return requireNotNull(pageCanvas)
    }

    @Implementation
    protected open fun getInfo(): PdfDocument.PageInfo {
        return requireNotNull(pageInfo)
    }
}
