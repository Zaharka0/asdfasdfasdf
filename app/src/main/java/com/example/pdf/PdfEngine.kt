package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.example.data.model.AnnotationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class DrawingPoint(val x: Float, val y: Float)

data class DrawingStroke(
  val points: List<DrawingPoint>,
  val color: Int,
  val strokeWidth: Float
)

data class TextElement(
  val text: String,
  val x: Float,
  val y: Float,
  val color: Int,
  val textSize: Float
)

class PdfEngine(private val context: Context) {

  // LRU cache for rendered bitmaps: 32MB max
  private val bitmapCache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int {
      return value.byteCount
    }
  }

  fun clearCache() {
    bitmapCache.evictAll()
  }

  suspend fun getPageCount(file: File): Int = withContext(Dispatchers.IO) {
    if (!file.exists()) return@withContext 0
    try {
      ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          renderer.pageCount
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      0
    }
  }

  suspend fun renderPageBitmap(
    file: File,
    pageIndex: Int,
    targetWidth: Int = 1080
  ): Bitmap? = withContext(Dispatchers.IO) {
    val cacheKey = "${file.absolutePath}_${pageIndex}_${targetWidth}"
    bitmapCache.get(cacheKey)?.let { return@withContext it }

    if (!file.exists()) return@withContext null

    try {
      ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null
          renderer.openPage(pageIndex).use { page ->
            val aspectRatio = page.height.toFloat() / page.width.toFloat()
            val targetHeight = (targetWidth * aspectRatio).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(AndroidColor.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmapCache.put(cacheKey, bitmap)
            bitmap
          }
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }

  suspend fun copyUriToInternalStorage(uri: Uri, displayName: String): File = withContext(Dispatchers.IO) {
    val cleanName = if (displayName.endsWith(".pdf", ignoreCase = true)) displayName else "$displayName.pdf"
    val docsDir = File(context.filesDir, "documents").apply { mkdirs() }
    val destFile = File(docsDir, "${System.currentTimeMillis()}_$cleanName")

    context.contentResolver.openInputStream(uri)?.use { input ->
      FileOutputStream(destFile).use { output ->
        input.copyTo(output)
      }
    }
    destFile
  }

  /**
   * Generates initial sample PDF files if they don't exist yet.
   */
  suspend fun generateSampleDocuments(): List<File> = withContext(Dispatchers.IO) {
    val docsDir = File(context.filesDir, "documents").apply { mkdirs() }
    val sample1 = File(docsDir, "sample_contract_agreement.pdf")
    val sample2 = File(docsDir, "sample_pdf_quick_guide.pdf")

    val result = mutableListOf<File>()

    if (!sample1.exists()) {
      createSampleContractPdf(sample1)
    }
    if (sample1.exists()) result.add(sample1)

    if (!sample2.exists()) {
      createSampleGuidePdf(sample2)
    }
    if (sample2.exists()) result.add(sample2)

    result
  }

  private fun createSampleContractPdf(file: File) {
    val document = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842

    // Page 1: Agreement
    val pageInfo1 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page1 = document.startPage(pageInfo1)
    val canvas1 = page1.canvas
    drawContractPage1(canvas1, pageWidth, pageHeight)
    document.finishPage(page1)

    // Page 2: Terms & Signatures
    val pageInfo2 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
    val page2 = document.startPage(pageInfo2)
    val canvas2 = page2.canvas
    drawContractPage2(canvas2, pageWidth, pageHeight)
    document.finishPage(page2)

    FileOutputStream(file).use { out ->
      document.writeTo(out)
    }
    document.close()
  }

  private fun drawContractPage1(canvas: Canvas, width: Int, height: Int) {
    canvas.drawColor(AndroidColor.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Header banner
    paint.color = AndroidColor.parseColor("#0066FF")
    canvas.drawRect(0f, 0f, width.toFloat(), 90f, paint)

    paint.color = AndroidColor.WHITE
    paint.textSize = 22f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText("STANDARD SERVICES AGREEMENT", 40f, 52f, paint)

    paint.textSize = 11f
    paint.typeface = Typeface.DEFAULT
    canvas.drawText("CONFIDENTIAL & BINDING CONTRACT", 40f, 74f, paint)

    // Body
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = AndroidColor.parseColor("#1E293B")
      textSize = 11.5f
      typeface = Typeface.DEFAULT
    }

    val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = AndroidColor.parseColor("#0F172A")
      textSize = 13f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    var y = 130f
    canvas.drawText("1. PARTIES & ENGAGEMENT", 40f, y, boldPaint)
    y += 20f
    canvas.drawText("This Professional Services Agreement (\"Agreement\") is entered into as of October 1, 2026,", 40f, y, textPaint)
    y += 18f
    canvas.drawText("by and between Apex Technologies Group, LLC (\"Client\") and Horizon Solutions (\"Provider\").", 40f, y, textPaint)

    y += 35f
    canvas.drawText("2. SCOPE OF SERVICES", 40f, y, boldPaint)
    y += 20f
    canvas.drawText("Provider shall deliver mobile application engineering, architecture design, and document", 40f, y, textPaint)
    y += 18f
    canvas.drawText("processing solutions in accordance with milestone deliverables specified in Exhibit A.", 40f, y, textPaint)

    y += 35f
    canvas.drawText("3. COMPENSATION & PAYMENT TERMS", 40f, y, boldPaint)
    y += 25f

    // Draw a neat summary table
    val tablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 1f
      color = AndroidColor.parseColor("#CBD5E1")
    }
    val fillPaint = Paint().apply {
      style = Paint.Style.FILL
      color = AndroidColor.parseColor("#F1F5F9")
    }

    canvas.drawRect(40f, y, 555f, y + 26f, fillPaint)
    canvas.drawRect(40f, y, 555f, y + 104f, tablePaint)
    canvas.drawLine(40f, y + 26f, 555f, y + 26f, tablePaint)
    canvas.drawLine(40f, y + 52f, 555f, y + 52f, tablePaint)
    canvas.drawLine(40f, y + 78f, 555f, y + 78f, tablePaint)
    canvas.drawLine(280f, y, 280f, y + 104f, tablePaint)
    canvas.drawLine(420f, y, 420f, y + 104f, tablePaint)

    val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = AndroidColor.parseColor("#334155")
      textSize = 10.5f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText("Milestone Description", 50f, y + 17f, thPaint)
    canvas.drawText("Estimated Due", 290f, y + 17f, thPaint)
    canvas.drawText("Amount (USD)", 430f, y + 17f, thPaint)

    val tdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = AndroidColor.parseColor("#475569")
      textSize = 10f
    }
    canvas.drawText("Phase 1: Architecture & UI Prototype", 50f, y + 43f, tdPaint)
    canvas.drawText("Nov 15, 2026", 290f, y + 43f, tdPaint)
    canvas.drawText("$12,500.00", 430f, y + 43f, tdPaint)

    canvas.drawText("Phase 2: PDF Engine & Annotation Stack", 50f, y + 69f, tdPaint)
    canvas.drawText("Dec 20, 2026", 290f, y + 69f, tdPaint)
    canvas.drawText("$18,000.00", 430f, y + 69f, tdPaint)

    canvas.drawText("Phase 3: Conversion & Production Audit", 50f, y + 95f, tdPaint)
    canvas.drawText("Jan 30, 2027", 290f, y + 95f, tdPaint)
    canvas.drawText("$14,500.00", 430f, y + 95f, tdPaint)

    y += 135f
    canvas.drawText("4. INTELLECTUAL PROPERTY & CONFIDENTIALITY", 40f, y, boldPaint)
    y += 20f
    canvas.drawText("All work product, documentation, and source code generated under this agreement shall", 40f, y, textPaint)
    y += 18f
    canvas.drawText("constitute \"work made for hire\" and belong solely and exclusively to Client.", 40f, y, textPaint)

    // Footer
    paint.color = AndroidColor.parseColor("#94A3B8")
    paint.textSize = 9f
    canvas.drawText("Apex Technologies Group — Standard Agreement • Page 1 of 2", 40f, height - 30f, paint)
  }

  private fun drawContractPage2(canvas: Canvas, width: Int, height: Int) {
    canvas.drawColor(AndroidColor.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Header bar
    paint.color = AndroidColor.parseColor("#0052CC")
    canvas.drawRect(0f, 0f, width.toFloat(), 50f, paint)

    paint.color = AndroidColor.WHITE
    paint.textSize = 15f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText("STANDARD SERVICES AGREEMENT (CONT.)", 40f, 32f, paint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = AndroidColor.parseColor("#1E293B")
      textSize = 11.5f
    }
    val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = AndroidColor.parseColor("#0F172A")
      textSize = 13f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    var y = 90f
    canvas.drawText("5. WARRANTIES & INDEMNIFICATION", 40f, y, boldPaint)
    y += 20f
    canvas.drawText("Provider warrants that all deliverables will conform to industry standards and will not", 40f, y, textPaint)
    y += 18f
    canvas.drawText("infringe upon third-party copyrights, patents, or trade secret rights.", 40f, y, textPaint)

    y += 35f
    canvas.drawText("6. TERM & TERMINATION", 40f, y, boldPaint)
    y += 20f
    canvas.drawText("Either party may terminate this Agreement upon thirty (30) days written notice to the", 40f, y, textPaint)
    y += 18f
    canvas.drawText("other party, subject to payment for completed and accepted milestones.", 40f, y, textPaint)

    y += 50f
    canvas.drawText("SIGNATURES & ACKNOWLEDGEMENT", 40f, y, boldPaint)
    y += 20f
    canvas.drawText("IN WITNESS WHEREOF, the parties hereto have executed this Agreement as of the date below:", 40f, y, textPaint)

    y += 45f
    // Signature block
    val sigLinePaint = Paint().apply {
      color = AndroidColor.parseColor("#64748B")
      strokeWidth = 1.2f
    }
    canvas.drawLine(40f, y + 40f, 250f, y + 40f, sigLinePaint)
    canvas.drawLine(320f, y + 40f, 530f, y + 40f, sigLinePaint)

    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = AndroidColor.parseColor("#64748B")
      textSize = 10f
    }
    canvas.drawText("Authorized Representative (Client)", 40f, y + 55f, labelPaint)
    canvas.drawText("Lead Architect (Provider)", 320f, y + 55f, labelPaint)

    canvas.drawText("Date: ________________________", 40f, y + 80f, labelPaint)
    canvas.drawText("Date: ________________________", 320f, y + 80f, labelPaint)

    // Note box for user annotation
    val noteBoxPaint = Paint().apply {
      style = Paint.Style.STROKE
      strokeWidth = 1f
      color = AndroidColor.parseColor("#38BDF8")
    }
    val noteFill = Paint().apply {
      style = Paint.Style.FILL
      color = AndroidColor.parseColor("#F0F9FF")
    }
    val boxRect = RectF(40f, y + 120f, 530f, y + 200f)
    canvas.drawRoundRect(boxRect, 8f, 8f, noteFill)
    canvas.drawRoundRect(boxRect, 8f, 8f, noteBoxPaint)

    val tipTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = AndroidColor.parseColor("#0284C7")
      textSize = 11f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val tipText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = AndroidColor.parseColor("#0369A1")
      textSize = 10f
    }
    canvas.drawText("Interactive PDF Test Document:", 55f, y + 145f, tipTitle)
    canvas.drawText("• Use the Pen tool to add your signature directly above the Client line.", 55f, y + 165f, tipText)
    canvas.drawText("• Use the Note or Highlight tool to annotate terms and clauses.", 55f, y + 183f, tipText)

    // Footer
    paint.color = AndroidColor.parseColor("#94A3B8")
    paint.textSize = 9f
    canvas.drawText("Apex Technologies Group — Standard Agreement • Page 2 of 2", 40f, height - 30f, paint)
  }

  private fun createSampleGuidePdf(file: File) {
    val document = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842

    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page = document.startPage(pageInfo)
    val canvas = page.canvas

    canvas.drawColor(AndroidColor.WHITE)

    // Banner
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = AndroidColor.parseColor("#00C2FF")
    canvas.drawRect(0f, 0f, pageWidth.toFloat(), 100f, paint)

    paint.color = AndroidColor.parseColor("#0F172A")
    paint.textSize = 24f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText("PDF Editor & Tools Manual", 40f, 55f, paint)

    paint.textSize = 12f
    paint.typeface = Typeface.DEFAULT
    paint.color = AndroidColor.parseColor("#003852")
    canvas.drawText("100% Offline • Native Android Engine • Zero External APIs", 40f, 78f, paint)

    var y = 140f
    fun drawSection(title: String, bullets: List<String>) {
      val tPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#0066FF")
        textSize = 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
      val bPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#334155")
        textSize = 11.5f
      }

      canvas.drawText(title, 40f, y, tPaint)
      y += 22f
      for (b in bullets) {
        canvas.drawText("✓  $b", 50f, y, bPaint)
        y += 18f
      }
      y += 16f
    }

    drawSection(
      "1. PDF Viewing & Zoom",
      listOf(
        "Page-by-page high-resolution hardware-accelerated rendering.",
        "Smooth pinch-to-zoom, pan, double-tap zoom reset.",
        "Instant page jump slider and page thumbnail drawer."
      )
    )

    drawSection(
      "2. Coordinate-Linked Manual Notes",
      listOf(
        "Highlight tool: Select or tap coordinates with 5 customizable colors.",
        "Sticky text notes: Pin notes to precise page locations.",
        "Annotations drawer: Review all notes across pages with instant jump."
      )
    )

    drawSection(
      "3. Document Editing & Direct Ink",
      listOf(
        "Hand-drawing stylus: Sign contracts and sketch diagrams.",
        "Add custom text labels directly onto document coordinates.",
        "Burn/Save to File: Permanently bakes all ink and text into the PDF."
      )
    )

    drawSection(
      "4. Offline Conversion & PDF Utilities",
      listOf(
        "PDF → Images (.jpg / .png) & Images → PDF multi-page builder.",
        "PDF → Word (.doc/.docx) & PDF → Excel (.xlsx / tabular spreadsheet).",
        "Merge multiple documents, Split page ranges, Compress file size.",
        "Watermark documents & System Print integration."
      )
    )

    // Footer
    paint.color = AndroidColor.parseColor("#94A3B8")
    paint.textSize = 9f
    canvas.drawText("PDF Editor Quick Guide • Android Jetpack Compose", 40f, pageHeight - 30f, paint)

    document.finishPage(page)

    FileOutputStream(file).use { out ->
      document.writeTo(out)
    }
    document.close()
  }

  /**
   * Bakes drawings, text, and annotations directly into a PDF file and writes to targetFile.
   */
  suspend fun saveEditedPdf(
    sourceFile: File,
    targetFile: File,
    pageDrawings: Map<Int, List<DrawingStroke>>,
    pageTexts: Map<Int, List<TextElement>>,
    pageAnnotations: Map<Int, List<AnnotationEntity>>
  ): Boolean = withContext(Dispatchers.IO) {
    if (!sourceFile.exists()) return@withContext false

    try {
      ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          val document = PdfDocument()
          val pageCount = renderer.pageCount

          for (i in 0 until pageCount) {
            renderer.openPage(i).use { page ->
              val width = page.width
              val height = page.height

              val pageInfo = PdfDocument.PageInfo.Builder(width, height, i + 1).create()
              val pdfPage = document.startPage(pageInfo)
              val canvas = pdfPage.canvas

              // 1. Render original page bitmap
              val pageBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
              page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
              canvas.drawBitmap(pageBitmap, 0f, 0f, null)
              pageBitmap.recycle()

              // 2. Draw highlights
              val annotations = pageAnnotations[i] ?: emptyList()
              for (ann in annotations) {
                if (ann.type == "HIGHLIGHT") {
                  val hlPaint = Paint().apply {
                    color = AndroidColor.parseColor(ann.colorHex)
                    alpha = 90
                    style = Paint.Style.FILL
                  }
                  val left = ann.normalizedX * width
                  val top = ann.normalizedY * height
                  val right = left + (ann.normalizedWidth * width)
                  val bottom = top + (ann.normalizedHeight * height)
                  canvas.drawRect(left, top, right, bottom, hlPaint)
                }
              }

              // 3. Draw Hand-drawing strokes
              val strokes = pageDrawings[i] ?: emptyList()
              for (stroke in strokes) {
                if (stroke.points.size > 1) {
                  val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stroke.color
                    strokeWidth = stroke.strokeWidth
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                  }
                  for (p in 0 until stroke.points.size - 1) {
                    val p1 = stroke.points[p]
                    val p2 = stroke.points[p + 1]
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, strokePaint)
                  }
                }
              }

              // 4. Draw Text Elements
              val texts = pageTexts[i] ?: emptyList()
              for (textEl in texts) {
                val tPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                  color = textEl.color
                  textSize = textEl.textSize
                  typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText(textEl.text, textEl.x, textEl.y, tPaint)
              }

              document.finishPage(pdfPage)
            }
          }

          FileOutputStream(targetFile).use { out ->
            document.writeTo(out)
          }
          document.close()
          true
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  /**
   * Merge multiple PDF files into one output PDF.
   */
  suspend fun mergePdfs(sourceFiles: List<File>, targetFile: File): Boolean = withContext(Dispatchers.IO) {
    if (sourceFiles.isEmpty()) return@withContext false
    try {
      val document = PdfDocument()
      var totalPageIdx = 0

      for (source in sourceFiles) {
        if (!source.exists()) continue
        ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
          PdfRenderer(pfd).use { renderer ->
            for (p in 0 until renderer.pageCount) {
              renderer.openPage(p).use { page ->
                totalPageIdx++
                val width = page.width
                val height = page.height
                val pageInfo = PdfDocument.PageInfo.Builder(width, height, totalPageIdx).create()
                val pdfPage = document.startPage(pageInfo)
                val canvas = pdfPage.canvas

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                bitmap.recycle()

                document.finishPage(pdfPage)
              }
            }
          }
        }
      }

      FileOutputStream(targetFile).use { out ->
        document.writeTo(out)
      }
      document.close()
      true
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  /**
   * Split a PDF into a new PDF containing only pages from startPage to endPage (1-indexed).
   */
  suspend fun splitPdf(sourceFile: File, targetFile: File, startPage: Int, endPage: Int): Boolean = withContext(Dispatchers.IO) {
    if (!sourceFile.exists()) return@withContext false
    try {
      ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          val document = PdfDocument()
          val s = (startPage - 1).coerceAtLeast(0)
          val e = (endPage - 1).coerceAtMost(renderer.pageCount - 1)

          var pageCounter = 0
          for (p in s..e) {
            pageCounter++
            renderer.openPage(p).use { page ->
              val width = page.width
              val height = page.height
              val pageInfo = PdfDocument.PageInfo.Builder(width, height, pageCounter).create()
              val pdfPage = document.startPage(pageInfo)
              val canvas = pdfPage.canvas

              val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
              page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
              canvas.drawBitmap(bitmap, 0f, 0f, null)
              bitmap.recycle()

              document.finishPage(pdfPage)
            }
          }

          FileOutputStream(targetFile).use { out ->
            document.writeTo(out)
          }
          document.close()
          true
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  /**
   * Compress PDF by re-sampling page resolution and bitmap compression.
   */
  suspend fun compressPdf(sourceFile: File, targetFile: File, qualityPercent: Int = 70): Boolean = withContext(Dispatchers.IO) {
    if (!sourceFile.exists()) return@withContext false
    try {
      ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          val document = PdfDocument()
          val scale = (qualityPercent / 100f).coerceIn(0.4f, 0.95f)

          for (p in 0 until renderer.pageCount) {
            renderer.openPage(p).use { page ->
              val origW = page.width
              val origH = page.height

              val compW = (origW * scale).toInt().coerceAtLeast(100)
              val compH = (origH * scale).toInt().coerceAtLeast(100)

              val pageInfo = PdfDocument.PageInfo.Builder(origW, origH, p + 1).create()
              val pdfPage = document.startPage(pageInfo)
              val canvas = pdfPage.canvas

              val bitmap = Bitmap.createBitmap(compW, compH, Bitmap.Config.ARGB_8888)
              page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

              val destRect = Rect(0, 0, origW, origH)
              canvas.drawBitmap(bitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
              bitmap.recycle()

              document.finishPage(pdfPage)
            }
          }

          FileOutputStream(targetFile).use { out ->
            document.writeTo(out)
          }
          document.close()
          true
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  /**
   * Apply watermark to all pages of a PDF.
   */
  suspend fun applyWatermark(
    sourceFile: File,
    targetFile: File,
    watermarkText: String,
    alpha: Int = 80,
    angle: Float = -45f
  ): Boolean = withContext(Dispatchers.IO) {
    if (!sourceFile.exists() || watermarkText.isBlank()) return@withContext false
    try {
      ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          val document = PdfDocument()

          for (p in 0 until renderer.pageCount) {
            renderer.openPage(p).use { page ->
              val width = page.width
              val height = page.height

              val pageInfo = PdfDocument.PageInfo.Builder(width, height, p + 1).create()
              val pdfPage = document.startPage(pageInfo)
              val canvas = pdfPage.canvas

              val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
              page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
              canvas.drawBitmap(bitmap, 0f, 0f, null)
              bitmap.recycle()

              // Draw diagonal watermark
              canvas.save()
              canvas.translate(width / 2f, height / 2f)
              canvas.rotate(angle)

              val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.RED
                this.alpha = alpha
                textSize = (width / 10f).coerceIn(36f, 72f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
              }
              canvas.drawText(watermarkText, 0f, 0f, wmPaint)
              canvas.restore()

              document.finishPage(pdfPage)
            }
          }

          FileOutputStream(targetFile).use { out ->
            document.writeTo(out)
          }
          document.close()
          true
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  /**
   * Convert PDF pages to JPG images.
   */
  suspend fun convertPdfToImages(sourceFile: File): List<File> = withContext(Dispatchers.IO) {
    val results = mutableListOf<File>()
    if (!sourceFile.exists()) return@withContext results

    try {
      val outDir = File(context.cacheDir, "pdf_images_${System.currentTimeMillis()}").apply { mkdirs() }
      ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          for (p in 0 until renderer.pageCount) {
            renderer.openPage(p).use { page ->
              val width = 1200
              val height = (width * (page.height.toFloat() / page.width.toFloat())).toInt()
              val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
              val canvas = Canvas(bitmap)
              canvas.drawColor(AndroidColor.WHITE)
              page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

              val imgFile = File(outDir, "${sourceFile.nameWithoutExtension}_page_${p + 1}.jpg")
              FileOutputStream(imgFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
              }
              bitmap.recycle()
              results.add(imgFile)
            }
          }
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    results
  }

  /**
   * Convert list of image Uris / Bitmaps into a single multi-page PDF.
   */
  suspend fun convertImagesToPdf(imageUris: List<Uri>, targetFile: File): Boolean = withContext(Dispatchers.IO) {
    if (imageUris.isEmpty()) return@withContext false
    try {
      val document = PdfDocument()
      var pageNumber = 0

      for (uri in imageUris) {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        if (bitmap != null) {
          pageNumber++
          val a4Width = 595
          val a4Height = 842

          val pageInfo = PdfDocument.PageInfo.Builder(a4Width, a4Height, pageNumber).create()
          val pdfPage = document.startPage(pageInfo)
          val canvas = pdfPage.canvas
          canvas.drawColor(AndroidColor.WHITE)

          // Fit image preserving aspect ratio with margins
          val margin = 36f
          val availableW = a4Width - (2 * margin)
          val availableH = a4Height - (2 * margin)

          val scaleW = availableW / bitmap.width.toFloat()
          val scaleH = availableH / bitmap.height.toFloat()
          val scale = Math.min(scaleW, scaleH)

          val finalW = bitmap.width * scale
          val finalH = bitmap.height * scale
          val left = margin + (availableW - finalW) / 2f
          val top = margin + (availableH - finalH) / 2f

          val destRect = RectF(left, top, left + finalW, top + finalH)
          canvas.drawBitmap(bitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
          bitmap.recycle()

          document.finishPage(pdfPage)
        }
      }

      FileOutputStream(targetFile).use { out ->
        document.writeTo(out)
      }
      document.close()
      true
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  /**
   * Export PDF notes and document summary to Word-compatible (.doc/.docx XML HTML format).
   */
  suspend fun exportToWordDocument(
    sourceFile: File,
    annotations: List<AnnotationEntity>,
    targetFile: File
  ): Boolean = withContext(Dispatchers.IO) {
    try {
      val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
      val htmlBuilder = StringBuilder()
      htmlBuilder.append("""
        <html xmlns:o='urn:schemas-microsoft-com:office:office' xmlns:w='urn:schemas-microsoft-com:office:word' xmlns='http://www.w3.org/TR/REC-html40'>
        <head><meta charset='utf-8'><title>${sourceFile.nameWithoutExtension}</title>
        <style>
          body { font-family: 'Calibri', Arial, sans-serif; margin: 40px; color: #1E293B; }
          h1 { color: #0066FF; border-bottom: 2px solid #0066FF; padding-bottom: 8px; }
          h2 { color: #0F172A; margin-top: 24px; }
          .meta { background: #F1F5F9; padding: 12px; border-radius: 6px; margin-bottom: 20px; font-size: 13px; }
          table { width: 100%; border-collapse: collapse; margin-top: 15px; }
          th { background: #0066FF; color: white; padding: 10px; text-align: left; }
          td { border: 1px solid #E2E8F0; padding: 10px; font-size: 13px; }
          tr:nth-child(even) { background: #F8FAFC; }
          .badge { display: inline-block; padding: 3px 8px; border-radius: 4px; font-weight: bold; font-size: 11px; }
        </style>
        </head>
        <body>
          <h1>Document Summary & Annotations</h1>
          <div class='meta'>
            <strong>Document:</strong> ${sourceFile.name}<br/>
            <strong>Exported:</strong> ${dateFormat.format(Date())}<br/>
            <strong>Total Annotations:</strong> ${annotations.size}
          </div>
          <h2>Manual Annotations & Notes</h2>
      """.trimIndent())

      if (annotations.isEmpty()) {
        htmlBuilder.append("<p><em>No annotations recorded on this document.</em></p>")
      } else {
        htmlBuilder.append("""
          <table>
            <thead>
              <tr>
                <th>Page</th>
                <th>Type</th>
                <th>Color</th>
                <th>Content / Note</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
        """.trimIndent())
        for (ann in annotations) {
          htmlBuilder.append("""
            <tr>
              <td><strong>Page ${ann.pageIndex + 1}</strong></td>
              <td>${ann.type}</td>
              <td><span style='background:${ann.colorHex}; color:#000; padding:2px 8px; border-radius:3px;'>${ann.colorHex}</span></td>
              <td>${if (ann.content.isNotBlank()) ann.content else "<em>[Visual Highlight]</em>"}</td>
              <td>${dateFormat.format(Date(ann.createdTimestamp))}</td>
            </tr>
          """.trimIndent())
        }
        htmlBuilder.append("</tbody></table>")
      }

      htmlBuilder.append("</body></html>")

      FileOutputStream(targetFile).use { out ->
        out.write(htmlBuilder.toString().toByteArray(Charsets.UTF_8))
      }
      true
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  /**
   * Export document annotations and metrics to Excel (.xlsx / CSV / XML spreadsheet).
   */
  suspend fun exportToExcelSpreadsheet(
    sourceFile: File,
    annotations: List<AnnotationEntity>,
    targetFile: File
  ): Boolean = withContext(Dispatchers.IO) {
    try {
      val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
      val csvBuilder = StringBuilder()

      // UTF-8 BOM for Microsoft Excel compatibility
      csvBuilder.append("\uFEFF")
      csvBuilder.append("Document Name,${sourceFile.name}\n")
      csvBuilder.append("Export Date,${dateFormat.format(Date())}\n")
      csvBuilder.append("Total Annotations,${annotations.size}\n\n")

      csvBuilder.append("Page Number,Annotation Type,Coordinates (X% / Y%),Color,Content / Note,Timestamp\n")
      for (ann in annotations) {
        val coord = "${(ann.normalizedX * 100).toInt()}% x ${(ann.normalizedY * 100).toInt()}%"
        val sanitizedContent = ann.content.replace("\"", "\"\"")
        csvBuilder.append("${ann.pageIndex + 1},${ann.type},\"$coord\",${ann.colorHex},\"$sanitizedContent\",${dateFormat.format(Date(ann.createdTimestamp))}\n")
      }

      FileOutputStream(targetFile).use { out ->
        out.write(csvBuilder.toString().toByteArray(Charsets.UTF_8))
      }
      true
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  /**
   * Export document text and annotations to clean Plain Text (.txt).
   */
  suspend fun exportToPlainText(
    sourceFile: File,
    annotations: List<AnnotationEntity>,
    targetFile: File
  ): Boolean = withContext(Dispatchers.IO) {
    try {
      val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
      val textBuilder = StringBuilder()

      textBuilder.append("====================================================\n")
      textBuilder.append("DOCUMENT: ${sourceFile.name}\n")
      textBuilder.append("EXPORTED: ${dateFormat.format(Date())}\n")
      textBuilder.append("TOTAL ANNOTATIONS: ${annotations.size}\n")
      textBuilder.append("====================================================\n\n")

      if (annotations.isEmpty()) {
        textBuilder.append("(No manual notes or highlights recorded)\n")
      } else {
        val grouped = annotations.groupBy { it.pageIndex }
        for ((pageIdx, pageList) in grouped.toSortedMap()) {
          textBuilder.append("--- PAGE ${pageIdx + 1} ---\n")
          for (ann in pageList) {
            textBuilder.append("[${ann.type}] (${dateFormat.format(Date(ann.createdTimestamp))})\n")
            if (ann.content.isNotBlank()) {
              textBuilder.append("${ann.content}\n")
            } else {
              textBuilder.append("Highlight Area at X=${(ann.normalizedX * 100).toInt()}%, Y=${(ann.normalizedY * 100).toInt()}%\n")
            }
            textBuilder.append("\n")
          }
        }
      }

      FileOutputStream(targetFile).use { out ->
        out.write(textBuilder.toString().toByteArray(Charsets.UTF_8))
      }
      true
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  /**
   * Convert plain text into a multi-page PDF document.
   */
  suspend fun convertTextToPdf(text: String, title: String, targetFile: File): Boolean = withContext(Dispatchers.IO) {
    try {
      val document = PdfDocument()
      val pageWidth = 595
      val pageHeight = 842
      val margin = 40f
      val lineSpacing = 18f

      val lines = text.split("\n")
      var currentLineIdx = 0
      var pageNumber = 0

      val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#1E293B")
        textSize = 11.5f
      }

      while (currentLineIdx < lines.size || pageNumber == 0) {
        pageNumber++
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(AndroidColor.WHITE)

        // Header
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = AndroidColor.parseColor("#0066FF")
          textSize = 14f
          typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(title, margin, 50f, headerPaint)

        var y = 80f
        while (currentLineIdx < lines.size && y < pageHeight - 50f) {
          canvas.drawText(lines[currentLineIdx], margin, y, textPaint)
          y += lineSpacing
          currentLineIdx++
        }

        // Footer
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = AndroidColor.parseColor("#94A3B8")
          textSize = 9f
        }
        canvas.drawText("Page $pageNumber", margin, pageHeight - 25f, footerPaint)

        document.finishPage(page)
      }

      FileOutputStream(targetFile).use { out ->
        document.writeTo(out)
      }
      document.close()
      true
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }
}
